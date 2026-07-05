package com.nightread.app.data

data class SyncStats(
    val booksOnDisk: Int,
    val booksLocal: Int,
    val toDownload: List<CloudFileEntry>,
    val toUpload: List<BookEntity>,
    val duplicates: Int,
    val cloudProgressItems: List<ResourceItem>
)
