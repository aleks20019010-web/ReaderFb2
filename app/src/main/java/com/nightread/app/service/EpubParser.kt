package com.nightread.app.service

import com.nightread.app.data.EpubIdentifierHelper
import java.io.File

object EpubParser : BookParser {
    override fun parse(file: File, defaultTitle: String): BookParser.ParsedBook {
        val metadata = EpubIdentifierHelper.getEpubMetadata(file)
        
        return BookParser.ParsedBook(
            title = metadata?.title ?: defaultTitle,
            author = metadata?.author ?: "Unknown",
            content = metadata?.content ?: ""
        )
    }
}
