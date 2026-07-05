package com.nightread.app.data

import com.nightread.app.data.BookEntity

data class SyncStats(
    val booksOnDisk: Int,
    val booksLocal: Int,
    val toDownload: List<ResourceItem>,
    val toUpload: List<BookEntity>,
    val duplicates: Int,
    val manifest: SyncManifest,
    val cloudProgressItems: List<ResourceItem>
)

data class SyncManifest(
    val books: MutableMap<String, String> = mutableMapOf() // filename -> sha1
)
