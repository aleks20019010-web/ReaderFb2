package com.example.service

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.BookDao
import com.example.data.BookEntity
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.security.MessageDigest

class NewBookScanner(
    private val context: Context,
    private val bookDao: BookDao
) {
    val state = MutableStateFlow(ScannerState())
    private val TAG = "NewBookScanner"

    suspend fun scan() {
        Log.d(TAG, "Scanning started")
        state.value = ScannerState(isScanning = true, status = "Scanning started")
        
        val paths = listOf(
            Environment.getExternalStorageDirectory(),
            File(Environment.getExternalStorageDirectory(), "Download"),
            File(Environment.getExternalStorageDirectory(), "Documents"),
            File(Environment.getExternalStorageDirectory(), "Books")
        )
        
        val filesToProcess = mutableListOf<File>()
        for (path in paths) {
            gatherFilesRecursive(path, filesToProcess, 0)
        }
        
        state.value = state.value.copy(totalFiles = filesToProcess.size, status = "Files gathered: ${filesToProcess.size}")
        Log.d(TAG, "Files gathered: ${filesToProcess.size}")
        
        val sha1Cache = bookDao.getAllSha1s().toMutableSet()
        val batchList = mutableListOf<BookEntity>()
        
        for ((index, file) in filesToProcess.withIndex()) {
            state.value = state.value.copy(processedFiles = index + 1, status = "Processing: ${file.name}")
            
            val contentBytes = file.readBytes()
            val sha1 = computeSha1(contentBytes)
            if (sha1 in sha1Cache) {
                state.value = state.value.copy(skippedBooks = state.value.skippedBooks + 1)
                continue
            }
            
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
        
        Log.d(TAG, "Finished. Added: ${state.value.addedBooks}, Skipped: ${state.value.skippedBooks}")
        state.value = state.value.copy(isScanning = false, status = "Finished")
    }
    
    private fun gatherFilesRecursive(dir: File, list: MutableList<File>, depth: Int) {
        if (depth > 6 || !dir.exists() || !dir.isDirectory) return
        
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val name = file.name.lowercase()
                if (name.startsWith(".") || name == "android" || name == "data" || name == "obb" || name == "system" || name == "vendor") {
                    return@forEach
                }
                gatherFilesRecursive(file, list, depth + 1)
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
