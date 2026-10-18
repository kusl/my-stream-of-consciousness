package com.github.kusl.msoc.core

import java.util.Locale

object Sizes {
    const val SHOW = 100L * 1024
    const val WARN = 1024L * 1024
    const val MAX = 10L * 1024 * 1024

    fun format(bytes: Long): String =
        if (bytes < WARN) String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0)
        else String.format(Locale.ROOT, "%.1f MiB", bytes / 1048576.0)

    fun utf8(text: CharSequence): Long {
        var bytes = 0L
        var i = 0
        while (i < text.length) {
            val c = text[i]
            bytes += when {
                c.code < 0x80 -> 1
                c.code < 0x800 -> 2
                c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate() -> { i++; 4 }
                else -> 3
            }
            i++
        }
        return bytes
    }
}
