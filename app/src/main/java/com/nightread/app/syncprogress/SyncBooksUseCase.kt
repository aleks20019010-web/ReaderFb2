package com.nightread.app.syncprogress

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SyncBooksUseCase"

class SyncBooksUseCase(
    private val sha1CacheRepository: Sha1CacheRepository,
    private val progressRepository: ProgressRepository,
    private val syncBookDao: SyncBookDao
) {

    /**
     * Синхронизация книг с Яндекс Диском с дедупликацией.
     * 
     * @param token Токен авторизации Яндекс Диска
     * @param accountId Идентификатор аккаунта
     * @param cloudFiles Список файлов, полученных из облака
     * @return Список обнаруженных дубликатов в облаке (для UI уведомления)
     */
    suspend fun syncBooks(
        token: String,
        accountId: String,
        cloudFiles: List<CloudMetadata>
    ): List<CloudDuplicateInfo> = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting syncBooks with ${cloudFiles.size} cloud files")

        val duplicateInfos = mutableListOf<CloudDuplicateInfo>()

        // 1. Группируем файлы в облаке по SHA-1, чтобы обнаружить дубликаты
        // Но сначала нужно вычислить/получить SHA-1 для каждого файла.
        val fileWithSha1List = mutableListOf<Pair<CloudMetadata, String>>()

        for (cloudFile in cloudFiles) {
            val sha1 = try {
                sha1CacheRepository.getOrComputeSha1(
                    authToken = token,
                    accountId = accountId,
                    filePath = cloudFile.path,
                    fileSize = cloudFile.size,
                    fileModified = cloudFile.modified
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving SHA1 for ${cloudFile.path}, skipping: ${e.localizedMessage}")
                null
            }

            if (sha1 != null) {
                fileWithSha1List.add(Pair(cloudFile, sha1))
            }
        }

        // Группируем по SHA-1
        val groupedBySha1 = fileWithSha1List.groupBy { it.second }

        // Фильтруем и дедуплицируем файлы в облаке:
        // "Повторяющиеся SHA1 в облаке → игнорируем дубли, берём первый по дате."
        // При конфликте одинаковых SHA1 в облаке — логируем предупреждение, но не создаем дубли.
        val uniqueCloudBooks = mutableMapOf<String, CloudMetadata>()

        for ((sha1, files) in groupedBySha1) {
            if (files.size > 1) {
                Log.w(TAG, "⚠️ [CONFLICT] Found ${files.size} duplicates in cloud for SHA1: $sha1")
                // Сортируем по дате изменения (modified) и берем первый
                val sortedFiles = files.map { it.first }.sortedBy { it.modified }
                val chosenFile = sortedFiles.first()
                uniqueCloudBooks[sha1] = chosenFile

                // Собираем информацию о дубликатах для передачи в UI
                duplicateInfos.add(
                    CloudDuplicateInfo(
                        sha1 = sha1,
                        chosenPath = chosenFile.path,
                        allPaths = sortedFiles.map { it.path }
                    )
                )
            } else {
                uniqueCloudBooks[sha1] = files.first().first
            }
        }

        // 2. Сверяем с локальным индексом
        val localBooks = syncBookDao.findAllActiveSync()

        // Новые SHA1 -> добавляем/обновляем
        for ((sha1, cloudFile) in uniqueCloudBooks) {
            progressRepository.addBookIfNotExists(cloudFile, sha1)
        }

        // Пропавшие SHA1 -> помечаем как "удалены" (soft delete)
        for (localBook in localBooks) {
            if (!uniqueCloudBooks.containsKey(localBook.sha1)) {
                Log.i(TAG, "Book ${localBook.title} (sha1=${localBook.sha1}) missing in cloud. Marking as deleted (soft delete).")
                val softDeletedBook = localBook.copy(isDeleted = true)
                syncBookDao.insertOrUpdate(softDeletedBook)
            }
        }

        Log.i(TAG, "SyncBooks completed. Active books saved/updated, duplicates reported: ${duplicateInfos.size}")
        duplicateInfos
    }
}

/**
 * Информация о дубликатах в облаке для UI.
 */
data class CloudDuplicateInfo(
    val sha1: String,
    val chosenPath: String,
    val allPaths: List<String>
)
