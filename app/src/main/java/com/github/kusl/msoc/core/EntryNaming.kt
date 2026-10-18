package com.github.kusl.msoc.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.random.Random

object EntryNaming {
    const val DIR = "entries"
    private val stamp = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH-mm-ss.SSS'Z'")
    private val day = DateTimeFormatter.ofPattern("uuuu/MM/dd")
    private val name = Regex("""(\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}\.\d{3}Z)-[0-9a-f]{8}\.txt""")

    fun newPath(at: Instant): String {
        val utc = at.atOffset(ZoneOffset.UTC)
        return "$DIR/${day.format(utc)}/${stamp.format(utc)}-${suffix()}.txt"
    }

    fun renamed(path: String) = "${path.substringBeforeLast('-')}-${suffix()}.txt"

    fun instantOf(path: String): Instant? = name.matchEntire(path.substringAfterLast('/'))
        ?.let { LocalDateTime.parse(it.groupValues[1], stamp).toInstant(ZoneOffset.UTC) }

    fun message(path: String) = "Add ${path.substringAfterLast('/')}"

    private fun suffix() = "%08x".format(Random.nextInt())
}
