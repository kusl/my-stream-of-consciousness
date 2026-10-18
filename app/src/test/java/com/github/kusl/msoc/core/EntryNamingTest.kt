package com.github.kusl.msoc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EntryNamingTest {
    private val at = Instant.parse("2026-10-18T14:27:05.123Z")

    @Test
    fun pathEncodesUtcTimestamp() {
        val path = EntryNaming.newPath(at)
        assertTrue(path, path.matches(Regex("""entries/2026/10/18/2026-10-18T14-27-05\.123Z-[0-9a-f]{8}\.txt""")))
        assertEquals(at, EntryNaming.instantOf(path))
    }

    @Test
    fun renameKeepsTimestamp() {
        val path = EntryNaming.newPath(at)
        val renamed = EntryNaming.renamed(path)
        assertNotEquals(path, renamed)
        assertEquals(at, EntryNaming.instantOf(renamed))
    }

    @Test
    fun foreignFilesAreIgnored() {
        assertNull(EntryNaming.instantOf("entries/notes.txt"))
    }
}
