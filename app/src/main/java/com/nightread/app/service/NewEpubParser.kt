package com.nightread.app.service

import java.io.File

object NewEpubParser {
    fun parse(file: File, defaultTitle: String): EpubParser.EpubMetadata {
        return EpubParser.parseEpub(file, defaultTitle)
    }

    fun extractText(file: File): String {
        return EpubParser.extractText(file)
    }

    fun extractCover(file: File): ByteArray? {
        return EpubParser.parseEpub(file, "").coverBytes
    }
}
