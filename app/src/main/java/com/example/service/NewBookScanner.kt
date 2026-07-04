package com.example.service

import android.content.Context
import android.os.Environment
import com.example.data.BookDao
import com.example.data.BookEntity
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.security.MessageDigest

data class ScannerState(
    val isScanning: Boolean = false,
    val status: String = "",
    val totalFiles: Int = 0,
    val processedFiles: Int = 0,
    val addedBooks: Int = 0,
    val skippedBooks: Int = 0
)

class NewBookScanner(
    private val context: Context,
    private val bookDao: BookDao
) {
    val state = MutableStateFlow(ScannerState())

    suspend fun scan() {
        state.value = ScannerState(isScanning = true, status = "Scanning started")
        
        val paths = listOf(
            Environment.getExternalStorageDirectory(),
            File(Environment.getExternalStorageDirectory(), "Download"),
            File(Environment.getExternalStorageDirectory(), "Documents"),
            File(Environment.getExternalStorageDirectory(), "Books")
        )
        
        val filesToProcess = mutableListOf<File>()
        for (path in paths) {
            gatherFilesRecursive(path, filesToProcess)
        }
        
        state.value = state.value.copy(totalFiles = filesToProcess.size, status = "Files gathered")
        
        val sha1Cache = bookDao.getAllSha1s().toMutableSet()
        val batchList = mutableListOf<BookEntity>()
        
        for ((index, file) in filesToProcess.withIndex()) {
            state.value = state.value.copy(processedFiles = index + 1, status = "Processing: ${file.name}")
            
            // Simplified logic for example purposes
            val sha1 = computeSha1(file.readBytes())
            if (sha1 in sha1Cache) {
                state.value = state.value.copy(skippedBooks = state.value.skippedBooks + 1)
                continue
            }
            
            // Parse metadata (simplified)
            val content = file.readText()
            val metadata = NewFb2Parser.parse(content, file.nameWithoutExtension)
            
            val book = BookEntity(
                sha1 = sha1,
                title = metadata.title,
                author = metadata.author,
                series = metadata.series,
                seriesIndex = metadata.seriesIndex,
                content = content,
                filePath = file.absolutePath
            )
            
            batchList.add(book)
            sha1Cache.add(sha1)
            
            if (batchList.size >= 50) {
                bookDao.insertBooks(batchList)
                state.value = state.value.copy(addedBooks = state.value.addedBooks + batchList.size)
                batchList.clear()
            }
        }
        
        if (batchList.isNotEmpty()) {
            bookDao.insertBooks(batchList)
            state.value = state.value.copy(addedBooks = state.value.addedBooks + batchList.size)
        }
        
        state.value = state.value.copy(isScanning = false, status = "Finished")
    }
    
    private fun gatherFilesRecursive(dir: File, list: MutableList<File>) {
        if (!dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (file.name.startsWith(".")) return@forEach
                gatherFilesRecursive(file, list)
            } else {
                val ext = file.extension.lowercase()
                if (ext == "fb2" || ext == "zip") {
                    list.add(file)
                }
            }
        }
    }
    
    private fun computeSha1(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
