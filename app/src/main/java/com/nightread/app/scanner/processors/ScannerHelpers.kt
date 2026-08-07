package com.nightread.app.scanner.processors

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.nightread.app.data.Sha1Helper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

const val SHA1_MAX_FILE_SIZE = 50L * 1024 * 1024

suspend fun computeBookSha1(book: BookSource, context: Context): String {
    return withContext(Dispatchers.IO) {
        try {
            if (!book.realPath.isNullOrBlank()) {
                val file = File(book.realPath)
                if (file.exists() && file.length() > 0) {
                    if (file.length() < SHA1_MAX_FILE_SIZE) {
                        Sha1Helper.computeSha1FromContent(file) 
                            ?: Sha1Helper.computeSha1FileNio(file)
                    } else {
                        generateFastHash(book)
                    }
                } else {
                    context.contentResolver.openInputStream(book.uri)?.use { 
                        Sha1Helper.computeSha1Stream(it) 
                    }
                }
            } else {
                context.contentResolver.openInputStream(book.uri)?.use { 
                    Sha1Helper.computeSha1Stream(it) 
                }
            }
        } catch (e: Throwable) {
            Log.w("ScannerHelpers", "Error computing SHA1 for ${book.name}", e)
            generateFastHash(book)
        } ?: generateFastHash(book)
    }
}

fun generateFastHash(book: BookSource): String {
    val input = "${book.uri}_${book.size}_${book.modified}"
    return MessageDigest.getInstance("SHA-1")
        .digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

fun resolveBookPath(book: BookSource, context: Context): String {
    if (!book.realPath.isNullOrBlank() && File(book.realPath).exists()) {
        return File(book.realPath).absolutePath
    }
    if (book.uri.scheme == "file") {
        val path = book.uri.path ?: book.uri.toString().removePrefix("file://")
        if (File(path).exists()) return File(path).absolutePath
    }
    val uriPath = book.uri.path
    if (!uriPath.isNullOrBlank() && File(uriPath).exists()) {
        return File(uriPath).absolutePath
    }
    if (book.uri.scheme == "content") {
        try {
            context.contentResolver.query(book.uri, arrayOf(MediaStore.Files.FileColumns.DATA), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                    if (dataIdx != -1) {
                        val path = cursor.getString(dataIdx)
                        if (!path.isNullOrBlank() && File(path).exists()) {
                            return File(path).absolutePath
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w("ScannerHelpers", "Error resolving DATA column for ${book.uri}", e)
        }
    }
    return book.realPath ?: book.uri.path ?: book.uri.toString()
}
