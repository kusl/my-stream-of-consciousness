package com.github.kusl.msoc.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SizesTest {
    @Test
    fun utf8MatchesEncoder() {
        val text = "aé€😀\n"
        assertEquals(text.encodeToByteArray().size.toLong(), Sizes.utf8(text))
    }

    @Test
    fun formatsBinaryUnits() {
        assertEquals("100.0 KiB", Sizes.format(102_400))
        assertEquals("1.5 MiB", Sizes.format(1_572_864))
    }
}
