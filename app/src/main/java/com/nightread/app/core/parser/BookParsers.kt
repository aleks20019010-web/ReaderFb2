package com.nightread.app.core.parser

import com.nightread.app.data.EpubIdentifierHelper
import com.nightread.app.service.Fb2Parser
import com.nightread.app.service.MobiParser
import java.io.File
import java.io.InputStream

object BookParsers {
    fun parseFb2(input: InputStream, defaultTitle: String) = Fb2Parser.parse(input, defaultTitle)
    fun parseEpub(file: File) = EpubIdentifierHelper.getEpubMetadata(file)
    fun parseMobi(file: File, defaultTitle: String = "Book") = MobiParser.parse(file, defaultTitle)
}
