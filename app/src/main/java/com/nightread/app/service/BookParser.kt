package com.nightread.app.service

import java.io.File

interface BookParser {
    data class ParsedBook(
        val title: String,
        val author: String,
        val content: String,
        val notes: Map<String, String> = emptyMap(),
        val coverBytes: ByteArray? = null,
        val annotation: String? = null
    )

    fun parse(file: File, defaultTitle: String): ParsedBook
}
