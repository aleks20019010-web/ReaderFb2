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

    private fun updateLocalAndGlobalState(newState: ScannerState) {
        state.value = newState
        NewBookScanState.updateState(newState)
    }

    suspend fun scan() {
        scanBooks()
    }

    suspend fun scanBooks() {
        Log.d(TAG, "scanBooks: Starting auto-scanning sequence.")
        updateLocalAndGlobalState(ScannerState(isScanning = true, status = "Scanning started..."))

        val paths = listOf(
            Environment.getExternalStorageDirectory(),
            File(Environment.getExternalStorageDirectory(), "Download"),
            File(Environment.getExternalStorageDirectory(), "Documents"),
            File(Environment.getExternalStorageDirectory(), "Books")
        )

        val filesToProcess = mutableListOf<File>()
        for (path in paths) {
            try {
                if (path.exists() && path.isDirectory && path.canRead()) {
                    Log.d(TAG, "Checking path for gathering: ${path.absolutePath}")
                    gatherFilesRecursive(path, filesToProcess, 0)
                } else {
                    Log.w(TAG, "Path is not accessible: ${path.absolutePath} (exists=${path.exists()}, isDir=${path.isDirectory()}, canRead=${path.canRead()})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking root path: ${path.absolutePath}", e)
            }
        }

        val total = filesToProcess.size
        Log.d(TAG, "Total FB2/ZIP files gathered: $total")
        
        if (total == 0) {
            Log.d(TAG, "Finished scanning: no supported books found.")
            updateLocalAndGlobalState(ScannerState(isScanning = false, status = "No books found. Please check Download, Documents, or Books folders."))
            return
        }

        updateLocalAndGlobalState(ScannerState(
            isScanning = true,
            status = "Files gathered: $total",
            totalFiles = total
        ))

        val sha1Cache = try {
            bookDao.getAllSha1s().toMutableSet()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching SHA1 cache from DB", e)
            mutableSetOf<String>()
        }

        val batchList = mutableListOf<BookEntity>()
        var addedCount = 0
        var skippedCount = 0

        for ((index, file) in filesToProcess.withIndex()) {
            val fileIndex = index + 1
            Log.d(TAG, "Processing file [$fileIndex/$total]: ${file.name}")
            
            updateLocalAndGlobalState(ScannerState(
                isScanning = true,
                status = "Processing: ${file.name} ($fileIndex/$total)",
                totalFiles = total,
                processedFiles = fileIndex,
                addedBooks = addedCount,
                skippedBooks = skippedCount
            ))

            val success = kotlinx.coroutines.withTimeoutOrNull(12000) {
                try {
                    processFile(file, sha1Cache, batchList, { added, skipped ->
                        addedCount += added
                        skippedCount += skipped
                    })
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing file ${file.absolutePath}", e)
                    false
                }
            }
            if (success == null) {
                Log.e(TAG, "Timeout of 12s exceeded processing file: ${file.absolutePath}")
            }

            // Insert to DB in batches of 50 to maximize performance
            if (batchList.size >= 50) {
                try {
                    bookDao.insertBooks(batchList)
                    Log.d(TAG, "Inserted batch of ${batchList.size} books to database successfully.")
                    batchList.clear()
                } catch (e: Exception) {
                    Log.e(TAG, "Error committing books batch to DB", e)
                }
            }
        }

        if (batchList.isNotEmpty()) {
            try {
                bookDao.insertBooks(batchList)
                Log.d(TAG, "Inserted final batch of ${batchList.size} books to database successfully.")
                batchList.clear()
            } catch (e: Exception) {
                Log.e(TAG, "Error committing final books batch to DB", e)
            }
        }

        val finalStatus = "Scan finished. Added $addedCount books, skipped $skippedCount duplicates."
        Log.d(TAG, "Scan sequence completed successfully: totalFiles=$total, added=$addedCount, skipped=$skippedCount")
        
        updateLocalAndGlobalState(ScannerState(
            isScanning = false,
            status = finalStatus,
            totalFiles = total,
            processedFiles = total,
            addedBooks = addedCount,
            skippedBooks = skippedCount
        ))
    }

    private fun processFile(
        file: File,
        sha1Cache: MutableSet<String>,
        batchList: MutableList<BookEntity>,
        onStatsUpdated: (added: Int, skipped: Int) -> Unit
    ) {
        val ext = file.extension.lowercase()
        if (ext == "fb2") {
            val bytes = try {
                file.readBytes()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read bytes for file: ${file.absolutePath}", e)
                return
            }
            if (bytes.isEmpty()) return
            
            val sha1 = computeSha1(bytes)
            if (sha1 in sha1Cache) {
                Log.d(TAG, "Skipped existing book by SHA1 ($sha1): ${file.name}")
                onStatsUpdated(0, 1)
                return
            }
            
            val rawText = decodeBytesToString(bytes)
            val metadata = NewFb2Parser.parse(rawText, file.nameWithoutExtension)
            val book = BookEntity(
                sha1 = sha1,
                title = metadata.title,
                author = metadata.author,
                content = rawText,
                coverGradientStart = getRandomGradientStartColor(),
                coverGradientEnd = getRandomGradientEndColor(),
                category = "Local",
                totalCharacters = rawText.length,
                filePath = file.absolutePath
            )
            batchList.add(book)
            sha1Cache.add(sha1)
            onStatsUpdated(1, 0)
        } else if (ext == "zip") {
            try {
                java.util.zip.ZipInputStream(file.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val entryName = entry.name.lowercase()
                        if (!entry.isDirectory && entryName.endsWith(".fb2")) {
                            val tempBytes = zis.readBytes()
                            if (tempBytes.isNotEmpty()) {
                                val sha1 = computeSha1(tempBytes)
                                if (sha1 in sha1Cache) {
                                    Log.d(TAG, "Skipped existing ZIP-entry book by SHA1 ($sha1): $entryName")
                                    onStatsUpdated(0, 1)
                                } else {
                                    val rawText = decodeBytesToString(tempBytes)
                                    val metadata = NewFb2Parser.parse(rawText, entryName.removeSuffix(".fb2"))
                                    val book = BookEntity(
                                        sha1 = sha1,
                                        title = metadata.title,
                                        author = metadata.author,
                                        content = rawText,
                                        coverGradientStart = getRandomGradientStartColor(),
                                        coverGradientEnd = getRandomGradientEndColor(),
                                        category = "Local",
                                        totalCharacters = rawText.length,
                                        filePath = file.absolutePath
                                    )
                                    batchList.add(book)
                                    sha1Cache.add(sha1)
                                    onStatsUpdated(1, 0)
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read zip archive: ${file.absolutePath}", e)
            }
        }
    }

    private fun gatherFilesRecursive(dir: File, list: MutableList<File>, depth: Int) {
        if (depth > 6) {
            Log.d(TAG, "Max recursion depth (6) reached at: ${dir.absolutePath}")
            return
        }
        if (!dir.exists() || !dir.isDirectory() || !dir.canRead()) {
            return
        }

        val files = try {
            dir.listFiles()
        } catch (e: Exception) {
            Log.e(TAG, "Failed listFiles for directory: ${dir.absolutePath}", e)
            null
        } ?: return

        for (file in files) {
            try {
                if (file.isDirectory) {
                    val name = file.name.lowercase()
                    if (name.startsWith(".") || 
                        name == "android" || 
                        name == "data" || 
                        name == "obb" || 
                        name == "system" || 
                        name == "vendor" || 
                        name == "cache" || 
                        name == "temp" || 
                        name == "dcim" || 
                        name == "pictures" || 
                        name == "alarms" || 
                        name == "notifications" || 
                        name == "ringtones" || 
                        name == "podcasts"
                    ) {
                        continue
                    }
                    gatherFilesRecursive(file, list, depth + 1)
                } else {
                    val ext = file.extension.lowercase()
                    if (ext == "fb2" || ext == "zip") {
                        if (file.length() > 0 && file.length() < 30 * 1024 * 1024) {
                            list.add(file)
                        } else {
                            Log.d(TAG, "Ignoring size-restricted/empty file: ${file.name} (${file.length()} bytes)")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking file object: ${file.absolutePath}", e)
            }
        }
    }

    private fun decodeBytesToString(bytes: ByteArray): String {
        return try {
            val utf8Str = String(bytes, Charsets.UTF_8)
            if (utf8Str.contains("<?xml") || utf8Str.contains("<fictionbook")) {
                utf8Str
            } else {
                String(bytes, java.nio.charset.Charset.forName("windows-1251"))
            }
        } catch (e: Exception) {
            String(bytes, Charsets.UTF_8)
        }
    }

    private fun computeSha1(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun getRandomGradientStartColor(): String {
        val colors = listOf("#1A1A2E", "#16213E", "#0F3460", "#2E2528", "#3B0066")
        return colors.random()
    }

    private fun getRandomGradientEndColor(): String {
        val colors = listOf("#E94560", "#00ADB5", "#FF2E63", "#FF9F43", "#F35588")
        return colors.random()
    }
}
