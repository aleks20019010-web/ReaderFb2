package com.nightread.app.data

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class DuplicateFile(
    val filePath: String,
    val size: Long,
    val isRecommended: Boolean,
    var isSelected: Boolean = false
)

data class DuplicateGroup(
    val sha1: String,
    val title: String,
    val author: String,
    val files: List<DuplicateFile>
)

class CleanupManager(
    private val context: Context,
    private val bookDao: BookDao
) {
    private val TAG = "CleanupManager"

    suspend fun findDuplicates(
        onProgress: (current: Int, total: Int, status: String) -> Unit
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        onProgress(0, 0, "Сбор списка файлов...")
        
        val paths = listOf(
            Environment.getExternalStorageDirectory(),
            File(Environment.getExternalStorageDirectory(), "Download"),
            File(Environment.getExternalStorageDirectory(), "Documents"),
            File(Environment.getExternalStorageDirectory(), "Books")
        )

        val filesToProcess = mutableListOf<File>()
        val gatheredPaths = HashSet<String>()
        for (path in paths) {
            try {
                if (path.exists() && path.isDirectory && path.canRead()) {
                    val tempFileList = mutableListOf<File>()
                    gatherFilesRecursive(path, tempFileList, 0)
                    for (file in tempFileList) {
                        val canonical = file.canonicalPath
                        if (!gatheredPaths.contains(canonical)) {
                            gatheredPaths.add(canonical)
                            filesToProcess.add(file)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error gathering files from path: ${path.absolutePath}", e)
            }
        }

        val totalFiles = filesToProcess.size
        if (totalFiles == 0) {
            return@withContext emptyList<DuplicateGroup>()
        }

        // Get all books from DB to match metadata
        val allBooks = try {
            bookDao.getAllBooks().first()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching books from database", e)
            emptyList()
        }
        val booksMap = allBooks.associateBy { it.sha1 }

        // Compute SHA-1 for each file and group them
        val fileGroups = mutableMapOf<String, MutableList<File>>()
        for ((index, file) in filesToProcess.withIndex()) {
            val progressText = "Анализ файла [${index + 1}/$totalFiles]: ${file.name}"
            onProgress(index + 1, totalFiles, progressText)
            
            val sha1 = if (EpubIdentifierHelper.isEpub(file)) {
                EpubIdentifierHelper.getEpubIdentifier(file)
            } else {
                Sha1Helper.computeSha1FromContent(file)
            }
            if (sha1 != null) {
                if (!fileGroups.containsKey(sha1)) {
                    fileGroups[sha1] = mutableListOf()
                }
                fileGroups[sha1]?.add(file)
            }
        }

        // Filter groups with size > 1
        val duplicateGroups = mutableListOf<DuplicateGroup>()
        for ((sha1, files) in fileGroups) {
            if (files.size > 1) {
                val dbBook = booksMap[sha1]
                val title = dbBook?.title ?: files.first().nameWithoutExtension
                val author = dbBook?.author ?: "Неизвестен"

                // Decide recommendation: prefer path containing "books" (case insensitive)
                // If multiple or none, recommend the one with the shortest path
                var recommendedFileIndex = 0
                var maxScore = -1
                for ((i, file) in files.withIndex()) {
                    var score = 0
                    if (file.absolutePath.lowercase().contains("/books/")) {
                        score += 10
                    } else if (file.absolutePath.lowercase().contains("books")) {
                        score += 5
                    }
                    // Secondary criteria: prefer shorter path
                    score -= file.absolutePath.length / 10
                    if (score > maxScore) {
                        maxScore = score
                        recommendedFileIndex = i
                    }
                }

                val duplicateFiles = files.mapIndexed { i, file ->
                    val isRec = i == recommendedFileIndex
                    DuplicateFile(
                        filePath = file.absolutePath,
                        size = file.length(),
                        isRecommended = isRec,
                        isSelected = !isRec // by default, all except recommended are selected for deletion
                    )
                }

                duplicateGroups.add(
                    DuplicateGroup(
                        sha1 = sha1,
                        title = title,
                        author = author,
                        files = duplicateFiles
                    )
                )
            }
        }

        duplicateGroups
    }

    private fun gatherFilesRecursive(dir: File, list: MutableList<File>, depth: Int) {
        if (depth > 6) return
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return
        val files = dir.listFiles() ?: return
        for (file in files) {
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
                if (ext == "fb2" || ext == "zip" || ext == "epub") {
                    if (file.length() > 0 && file.length() < 30 * 1024 * 1024) {
                        list.add(file)
                    }
                }
            }
        }
    }

    /**
     * Deletes the specified files, updates database records if necessary.
     * Returns Pair(deletedCount, freedSpaceBytes)
     */
    suspend fun executeCleanup(
        filesToDelete: List<DuplicateFile>,
        groups: List<DuplicateGroup>
    ): Pair<Int, Long> = withContext(Dispatchers.IO) {
        var deletedCount = 0
        var freedSpace = 0L

        // Group files to delete by sha1
        val deletePathsBySha1 = filesToDelete.groupBy { df ->
            groups.find { g -> g.files.any { f -> f.filePath == df.filePath } }?.sha1 ?: ""
        }

        for ((sha1, dfs) in deletePathsBySha1) {
            if (sha1.isEmpty()) continue
            
            val group = groups.find { it.sha1 == sha1 } ?: continue
            val dbBook = bookDao.getBookBySha1(sha1)

            // Collect surviving file paths
            val deletedPaths = dfs.map { it.filePath }.toSet()
            val survivingFiles = group.files.filter { !deletedPaths.contains(it.filePath) }

            // Physically delete selected files
            for (df in dfs) {
                val file = File(df.filePath)
                if (file.exists()) {
                    val size = file.length()
                    try {
                        if (file.delete()) {
                            freedSpace += size
                            deletedCount++
                        } else {
                            Log.e(TAG, "Failed physical delete: ${df.filePath}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error physically deleting file ${df.filePath}", e)
                    }
                } else {
                    // File already gone on disk, but we consider it cleaned
                    deletedCount++
                }
            }

            // Database updates
            if (dbBook != null) {
                if (survivingFiles.isEmpty()) {
                    // All files were deleted for this book (shouldn't happen with proper safety, but just in case)
                    bookDao.deleteBookBySha1(sha1)
                } else {
                    // If the database currently points to one of the deleted files, update it to point to a surviving one
                    val currentPath = dbBook.filePath
                    if (deletedPaths.contains(currentPath)) {
                        val newPath = survivingFiles.first().filePath
                        try {
                            bookDao.updateFilePath(sha1, newPath)
                            Log.d(TAG, "Updated database path for book $sha1 from $currentPath to $newPath")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed database path update for book $sha1", e)
                        }
                    }
                }
            }
        }

        Pair(deletedCount, freedSpace)
    }
}
