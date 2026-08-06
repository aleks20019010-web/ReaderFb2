package com.nightread.app.features.reader.domain

import com.nightread.app.data.EpubIdentifierHelper
import com.nightread.app.service.Fb2Parser
import com.nightread.app.service.MobiParser
import java.io.File
import java.io.InputStream

class ParseBookUseCase {

    fun parseFb2(inputStream: InputStream, defaultTitle: String) = Fb2Parser.parse(inputStream, defaultTitle)

    fun parseEpub(file: File) = EpubIdentifierHelper.getEpubMetadata(file)

    fun parseMobi(file: File, defaultTitle: String = "Book") = MobiParser.parse(file, defaultTitle)
}
