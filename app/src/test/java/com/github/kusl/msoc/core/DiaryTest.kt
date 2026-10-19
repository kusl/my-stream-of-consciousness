package com.github.kusl.msoc.core

import com.github.kusl.msoc.git.GitRepo
import com.github.kusl.msoc.git.Remote
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.transport.RefSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DiaryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var remote: File
    private val other = PersonIdent("Other", "other@example.com")

    private class FakeSettings(var target: Remote?, var viewOnly: Boolean = false) : SettingsSource {
        override fun remote() = target
        override fun readOnly() = viewOnly
        override fun author() = "Tester" to "tester@example.com"
    }

    @Before
    fun setUp() {
        remote = bare("remote.git")
    }

    private fun bare(name: String) = tmp.newFolder(name).also { Git.init().setBare(true).setInitialBranch("main").setDirectory(it).call().close() }

    private fun upstream(dir: File = remote) = Remote(dir.absolutePath, "main", "", "")

    private fun device(settings: SettingsSource = FakeSettings(upstream()), mirror: Boolean = false): Diary {
        val dir = tmp.newFolder()
        return Diary(dir, GitRepo(dir, EntryNaming.DIR, EntryNaming::renamed), settings, mirror)
    }

    private fun reader(settings: FakeSettings = FakeSettings(upstream(), viewOnly = true)) = device(settings, mirror = true)

    private suspend fun Diary.texts() = entries().map { read(it.path) }.toSet()

    private fun remoteCommits() = Git.open(remote).use { it.log().call().count() }

    private fun withClone(block: (Git) -> Unit) = Git.cloneRepository().setURI(remote.absolutePath).setDirectory(tmp.newFolder()).call().use { git ->
        block(git)
        git.commit().setAuthor(other).setCommitter(other).setMessage("external").call()
        git.push().setRemote(remote.absolutePath).setRefSpecs(RefSpec("HEAD:refs/heads/main")).call()
    }

    @Test
    fun devicesConverge() = runBlocking<Unit> {
        val a = device()
        val b = device()
        a.add("one")
        b.add("two")
        a.sync()
        b.sync()
        a.sync()
        assertEquals(setOf("one", "two"), a.texts())
        assertEquals(setOf("one", "two"), b.texts())
        assertFalse(a.hasUnpushed())
        assertFalse(b.hasUnpushed())
        assertEquals(2, remoteCommits())
    }

    @Test
    fun refreshReportsOnlyRemoteChanges() = runBlocking<Unit> {
        val a = device()
        val b = device()
        a.add("one")
        a.sync()
        assertTrue(b.refresh())
        assertFalse(b.refresh())
        a.add("two")
        a.sync()
        assertTrue(b.refresh())
        assertEquals(setOf("one", "two"), b.texts())
    }

    @Test
    fun offlineEntriesArePushedLater() = runBlocking<Unit> {
        val settings = FakeSettings(null)
        val a = device(settings)
        a.add("one")
        a.add("two")
        a.sync()
        assertFalse(a.hasUnpushed())
        settings.target = upstream()
        assertTrue(a.hasUnpushed())
        a.sync()
        assertFalse(a.hasUnpushed())
        assertEquals(2, remoteCommits())
    }

    @Test
    fun remoteDeletionIsRespected() = runBlocking<Unit> {
        val a = device()
        a.add("gone")
        a.sync()
        val path = a.entries().single().path
        withClone { it.rm().addFilepattern(path).call() }
        a.add("kept")
        a.sync()
        assertEquals(setOf("kept"), a.texts())
    }

    @Test
    fun conflictingPathKeepsBothVersions() = runBlocking<Unit> {
        val a = device()
        a.add("mine")
        val path = a.entries().single().path
        withClone { git ->
            File(git.repository.workTree, path).apply { parentFile?.mkdirs() }.writeText("theirs")
            git.add().addFilepattern(path).call()
        }
        a.sync()
        assertEquals(setOf("mine", "theirs"), a.texts())
        assertEquals("theirs", a.read(path))
        assertFalse(a.hasUnpushed())
    }

    @Test
    fun readOnlyNeverPushes() = runBlocking<Unit> {
        val settings = FakeSettings(upstream(), viewOnly = true)
        val a = device(settings)
        a.add("one")
        assertFalse(a.sync())
        assertFalse(a.hasUnpushed())
        settings.viewOnly = false
        assertTrue(a.hasUnpushed())
        a.sync()
        assertEquals(1, remoteCommits())
    }

    @Test
    fun mirrorFollowsRemote() = runBlocking<Unit> {
        val writer = device()
        val mirror = reader()
        assertFalse(mirror.refresh())
        writer.add("one")
        writer.sync()
        assertTrue(mirror.refresh())
        assertEquals(setOf("one"), mirror.texts())
        assertFalse(mirror.refresh())
        writer.add("two")
        writer.sync()
        assertTrue(mirror.refresh())
        assertEquals(setOf("one", "two"), mirror.texts())
        assertFalse(mirror.hasUnpushed())
    }

    @Test
    fun mirrorFollowsRemoteDeletion() = runBlocking<Unit> {
        val writer = device()
        writer.add("gone")
        writer.sync()
        val mirror = reader()
        mirror.refresh()
        val path = writer.entries().single().path
        withClone { it.rm().addFilepattern(path).call() }
        assertTrue(mirror.refresh())
        assertTrue(mirror.entries().isEmpty())
    }

    @Test
    fun mirrorClearsWhenRemoteChanges() = runBlocking<Unit> {
        val writer = device()
        writer.add("one")
        writer.sync()
        val settings = FakeSettings(upstream(), viewOnly = true)
        val mirror = reader(settings)
        mirror.refresh()
        settings.target = upstream(bare("empty.git"))
        mirror.remoteChanged()
        assertTrue(mirror.entries().isEmpty())
        assertFalse(mirror.refresh())
        settings.target = upstream()
        mirror.remoteChanged()
        assertTrue(mirror.refresh())
        assertEquals(setOf("one"), mirror.texts())
    }

    @Test
    fun mirrorNeverWrites() = runBlocking<Unit> {
        val mirror = reader()
        assertTrue(runCatching { mirror.add("nope") }.isFailure)
    }
}
