package com.nightread.app.service

object TtsDataProvider {
    @Volatile
    var paragraphs: List<TtsParagraph> = emptyList()

    @Volatile
    var currentBookText: String = ""
}
