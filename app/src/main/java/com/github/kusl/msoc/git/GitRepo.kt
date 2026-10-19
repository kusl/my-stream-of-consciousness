package com.github.kusl.msoc.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectInserter
import org.eclipse.jgit.lib.ObjectReader
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.AndTreeFilter
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.treewalk.filter.TreeFilter
import java.io.File
import java.io.IOException

data class Remote(val url: String, val branch: String, val username: String, val token: String)

class GitRepo(private val dir: File, private val prefix: String, private val rename: (String) -> String) {
    private val git by lazy {
        if (File(dir, Constants.DOT_GIT).isDirectory) Git.open(dir)
        else Git.init().setDirectory(dir).setInitialBranch(MAIN).call()
    }
    private val repo get() = git.repository

    fun commit(path: String, ident: PersonIdent, message: String) {
        git.add().addFilepattern(path).call()
        git.commit().setAuthor(ident).setCommitter(ident).setMessage(message).setSign(false).call()
    }

    fun uncommitted(): List<String> = git.status().addPath(prefix).call().run { untracked + added }.sorted()

    fun hasUnpushed(): Boolean = repo.resolve(Constants.HEAD)?.let { it != repo.resolve(PUSHED) } ?: false

    fun forgetPushed() = deleteRef(PUSHED)

    fun sync(remote: Remote): Boolean {
        val auth = credentials(remote)
        val (branch, upstream) = fetch(remote, auth)
        val head = repo.resolve(Constants.HEAD)
        if (upstream != null && head != upstream) rebuild(upstream, head).takeIf { it != head }?.let(::moveHead)
        val tip = repo.resolve(Constants.HEAD) ?: return false
        if (tip != upstream) {
            push(remote.url, branch, auth)
            setRef(TRACKING, tip)
        }
        if (repo.resolve(PUSHED) != tip) setRef(PUSHED, tip)
        return tip != head
    }

    fun mirror(remote: Remote): Boolean {
        val upstream = fetch(remote, credentials(remote)).second
        if (upstream == repo.resolve(SHOWN)) return false
        if (upstream == null) {
            clear()
        } else {
            moveHead(upstream)
            setRef(SHOWN, upstream)
        }
        return true
    }

    fun clear() {
        deleteRef(SHOWN)
        dir.listFiles()?.filter { it.name != Constants.DOT_GIT }?.forEach { it.deleteRecursively() }
        repo.indexFile.delete()
    }

    private fun fetch(remote: Remote, auth: CredentialsProvider?): Pair<String, ObjectId?> {
        val refs = Git.lsRemoteRepository().setRemote(remote.url).setCredentialsProvider(auth).setTimeout(TIMEOUT).callAsMap()
        val branch = remote.branch.ifBlank {
            refs[Constants.HEAD]?.takeIf { it.isSymbolic }?.target?.name?.removePrefix(Constants.R_HEADS) ?: MAIN
        }
        val advertised = refs[Constants.R_HEADS + branch]?.objectId ?: return branch to null
        if (advertised == repo.resolve(TRACKING)) return branch to advertised
        git.fetch().setRemote(remote.url).setCredentialsProvider(auth).setTimeout(TIMEOUT)
            .setRefSpecs(RefSpec("+${Constants.R_HEADS}$branch:$TRACKING")).call()
        return branch to repo.resolve(TRACKING)
    }

    private fun rebuild(base: ObjectId, head: ObjectId?): ObjectId {
        val pending = head?.let { pending(it, base) }.orEmpty()
        repo.newObjectInserter().use { inserter ->
            repo.newObjectReader().use { reader ->
                var tip = base
                var tree: ObjectId = RevWalk(reader).use { it.parseCommit(base).tree }
                for (entry in pending) {
                    val blob = inserter.insert(Constants.OBJ_BLOB, entry.bytes)
                    val existing = TreeWalk.forPath(reader, entry.path, tree)?.use { it.getObjectId(0) }
                    if (existing == blob) continue
                    val path = if (existing == null) entry.path else rename(entry.path)
                    tree = withFile(reader, inserter, tree, path, blob)
                    tip = inserter.insert(CommitBuilder().apply {
                        setTreeId(tree)
                        setParentId(tip)
                        author = entry.author
                        committer = entry.committer
                        message = entry.message
                    })
                }
                inserter.flush()
                return tip
            }
        }
    }

    private fun pending(head: ObjectId, base: ObjectId): List<Pending> = RevWalk(repo).use { walk ->
        walk.markStart(walk.parseCommit(head))
        walk.markUninteresting(walk.parseCommit(base))
        repo.resolve(PUSHED)?.let { walk.markUninteresting(walk.parseCommit(it)) }
        walk.sort(RevSort.TOPO, true)
        walk.sort(RevSort.REVERSE, true)
        walk.toList().flatMap { commit -> added(commit, commit.parents.firstOrNull()?.let { walk.parseCommit(it).tree }) }
    }

    private fun added(commit: RevCommit, parentTree: ObjectId?): List<Pending> = TreeWalk(repo).use { walk ->
        walk.isRecursive = true
        walk.filter = AndTreeFilter.create(PathFilter.create(prefix), TreeFilter.ANY_DIFF)
        if (parentTree == null) walk.addTree(EmptyTreeIterator()) else walk.addTree(parentTree)
        walk.addTree(commit.tree)
        buildList {
            while (walk.next()) {
                if (walk.getRawMode(0) == 0 && FileMode.fromBits(walk.getRawMode(1)) == FileMode.REGULAR_FILE) {
                    val bytes = repo.open(walk.getObjectId(1)).bytes
                    add(Pending(walk.pathString, bytes, commit.authorIdent, commit.committerIdent, commit.fullMessage))
                }
            }
        }
    }

    private fun withFile(reader: ObjectReader, inserter: ObjectInserter, tree: ObjectId, path: String, blob: ObjectId): ObjectId {
        val cache = DirCache.newInCore()
        cache.builder().apply {
            addTree(ByteArray(0), DirCacheEntry.STAGE_0, reader, tree)
            add(DirCacheEntry(path).apply {
                fileMode = FileMode.REGULAR_FILE
                setObjectId(blob)
            })
        }.finish()
        return cache.writeTree(inserter)
    }

    private fun moveHead(tip: ObjectId) {
        setRef(Constants.HEAD, tip)
        git.reset().setMode(ResetCommand.ResetType.HARD).call()
    }

    private fun push(url: String, branch: String, auth: CredentialsProvider?) {
        val failed = git.push().setRemote(url).setCredentialsProvider(auth).setTimeout(TIMEOUT)
            .setRefSpecs(RefSpec("${Constants.HEAD}:${Constants.R_HEADS}$branch")).call()
            .flatMap { it.remoteUpdates }
            .filter { it.status != RemoteRefUpdate.Status.OK && it.status != RemoteRefUpdate.Status.UP_TO_DATE }
        if (failed.isNotEmpty()) throw IOException("Push ${failed.first().status} ${failed.first().message.orEmpty()}".trim())
    }

    private fun setRef(name: String, id: ObjectId) {
        val result = repo.updateRef(name).apply { setNewObjectId(id) }.forceUpdate()
        if (result !in UPDATED) throw IOException("$name $result")
    }

    private fun deleteRef(name: String) {
        if (repo.exactRef(name) != null) repo.updateRef(name).apply { isForceUpdate = true }.delete()
    }

    private fun credentials(remote: Remote): CredentialsProvider? =
        if (remote.username.isEmpty() && remote.token.isEmpty()) null
        else UsernamePasswordCredentialsProvider(remote.username.ifEmpty { "git" }, remote.token)

    private class Pending(val path: String, val bytes: ByteArray, val author: PersonIdent, val committer: PersonIdent, val message: String)

    private companion object {
        const val MAIN = "main"
        const val TRACKING = "refs/msoc/upstream"
        const val PUSHED = "refs/msoc/pushed"
        const val SHOWN = "refs/msoc/shown"
        const val TIMEOUT = 30
        val UPDATED = setOf(RefUpdate.Result.NEW, RefUpdate.Result.FORCED, RefUpdate.Result.FAST_FORWARD, RefUpdate.Result.NO_CHANGE)
    }
}
