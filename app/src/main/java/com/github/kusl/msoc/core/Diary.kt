package com.github.kusl.msoc.core

import com.github.kusl.msoc.git.GitRepo
import com.github.kusl.msoc.git.Remote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.jgit.lib.PersonIdent
import java.io.File
import java.time.Instant
import java.time.ZoneId

data class Entry(val path: String, val at: Instant, val size: Long, val preview: String, val truncated: Boolean)

interface SettingsSource {
    fun remote(): Remote?
    fun author(): Pair<String, String>
}

class Diary(
    private val root: File,
    private val repo: GitRepo,
    private val settings: SettingsSource,
    private val clock: () -> Instant = Instant::now,
) {
    private val mutex = Mutex()

    suspend fun add(text: String) = locked {
        val now = clock()
        val path = EntryNaming.newPath(now)
        File(root, path).apply { parentFile?.mkdirs() }.writeText(text)
        repo.commit(path, ident(now), EntryNaming.message(path))
    }

    suspend fun entries(): List<Entry> = locked {
        File(root, EntryNaming.DIR).walk().filter { it.isFile }.mapNotNull { file ->
            val path = file.relativeTo(root).invariantSeparatorsPath
            EntryNaming.instantOf(path)?.let { at ->
                val (text, cut) = preview(file)
                Entry(path, at, file.length(), text, cut)
            }
        }.sortedByDescending { it.path.substringAfterLast('/') }.toList()
    }

    suspend fun read(path: String): String = locked { File(root, path).readText() }

    suspend fun sync() = locked<Unit> {
        repo.untracked().forEach { repo.commit(it, ident(EntryNaming.instantOf(it) ?: clock()), EntryNaming.message(it)) }
        settings.remote()?.let(repo::sync)
    }

    suspend fun hasUnpushed() = locked { settings.remote() != null && repo.hasUnpushed() }

    suspend fun forgetPushed() = locked { repo.forgetPushed() }

    private fun ident(at: Instant) = settings.author().let { (name, email) -> PersonIdent(name, email, at, ZoneId.systemDefault()) }

    private fun preview(file: File): Pair<String, Boolean> {
        val buffer = ByteArray(PREVIEW_BYTES)
        var read = 0
        file.inputStream().use { input ->
            while (read < buffer.size) {
                val n = input.read(buffer, read, buffer.size - read)
                if (n < 0) break
                read += n
            }
        }
        val cut = file.length() > read
        val text = String(buffer, 0, read, Charsets.UTF_8)
        return (if (cut) text.trimEnd('\uFFFD') else text) to cut
    }

    private suspend fun <T> locked(block: () -> T): T = mutex.withLock { withContext(Dispatchers.IO) { block() } }

    private companion object {
        const val PREVIEW_BYTES = 4096
    }
}
