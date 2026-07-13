package com.nightread.app.syncprogress

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

private const val TAG = "SyncProgressUseCase"

sealed class MergeResult {
    object NoProgress : MergeResult()
    data class UseLocal(val progress: ReadingProgress) : MergeResult()
    data class UseCloud(val progress: ReadingProgress) : MergeResult()
    data class Conflict(val local: ReadingProgress, val cloud: ReadingProgress) : MergeResult()
}

class SyncReadingProgressUseCase(
    private val sha1CacheRepository: Sha1CacheRepository,
    private val progressRepository: ProgressRepository,
    private val cloudSyncRepository: CloudSyncRepository
) {

    /**
     * Выполняет синхронизацию прогресса для конкретной книги.
     * 
     * @param token Токен OAuth Яндекс Диска
     * @param accountId Идентификатор аккаунта
     * @param cloudPath Путь к книге на Яндекс Диске (например, "/Books/WarAndPeace.epub")
     * @param fileSize Размер файла на диске
     * @param fileModified Дата изменения файла на диске в формате ISO 8601
     * @param onConflict Калбек для ручного разрешения конфликтов. Если передан,
     *                   будет вызван при обнаружении конфликта. Должен вернуть выбранный прогресс.
     */
    suspend fun syncBookProgress(
        token: String,
        accountId: String,
        cloudPath: String,
        fileSize: Long,
        fileModified: String,
        onConflict: (suspend (local: ReadingProgress, cloud: ReadingProgress) -> ReadingProgress)? = null
    ): MergeResult = withContext(Dispatchers.IO) {
        
        // 1. Сверяем SHA1 через кэш (Требование 2a, b, c, d)
        val sha1 = try {
            sha1CacheRepository.getOrComputeSha1(token, accountId, cloudPath, fileSize, fileModified)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve SHA1 for $cloudPath, force recalculating due to possible cache corruption...", e)
            // При коррупции или сбое кэша принудительно сбрасываем и пробуем ещё раз
            sha1CacheRepository.invalidate(accountId, cloudPath)
            sha1CacheRepository.getOrComputeSha1(token, accountId, cloudPath, fileSize, fileModified)
        }

        if (sha1 == null) {
            Log.e(TAG, "Unable to resolve SHA1 for $cloudPath. Sync aborted for this book.")
            return@withContext MergeResult.NoProgress
        }

        // Проверяем, зарегистрирована ли локально эта книга
        val cloudFile = CloudMetadata(path = cloudPath, size = fileSize, modified = fileModified, sha1 = sha1)
        val book = progressRepository.addBookIfNotExists(cloudFile, sha1)

        // 2. Получаем локальный и облачный прогрессы
        val localProgress = progressRepository.getProgressForBook(sha1)
        val cloudProgress = cloudSyncRepository.downloadReadingProgress(token, sha1)

        Log.d(TAG, "Local progress: $localProgress")
        Log.d(TAG, "Cloud progress: $cloudProgress")

        // 3. Выполняем мерж и разрешаем конфликты
        val mergeDecision = evaluateMerge(localProgress, cloudProgress)

        when (mergeDecision) {
            is MergeResult.NoProgress -> {
                Log.d(TAG, "No progress available local or cloud for book: ${book.title}")
            }
            is MergeResult.UseLocal -> {
                Log.d(TAG, "Local progress is newer. Uploading to Cloud.")
                cloudSyncRepository.uploadReadingProgress(token, mergeDecision.progress)
            }
            is MergeResult.UseCloud -> {
                Log.d(TAG, "Cloud progress is newer. Saving to local DB.")
                progressRepository.saveProgressDirectly(mergeDecision.progress)
            }
            is MergeResult.Conflict -> {
                Log.d(TAG, "Conflict detected between Local and Cloud progress.")
                if (onConflict != null) {
                    val resolved = onConflict(mergeDecision.local, mergeDecision.cloud)
                    Log.d(TAG, "Conflict manually resolved. Using: $resolved")
                    progressRepository.saveProgressDirectly(resolved)
                    cloudSyncRepository.uploadReadingProgress(token, resolved)
                } else {
                    // Стратегия автоматического разрешения по умолчанию: "последний победил" с перевесом по проценту
                    Log.d(TAG, "No manual conflict handler. Auto-resolving using 'Last-Write-Wins' with percentage.")
                    val resolved = if (mergeDecision.local.percent >= mergeDecision.cloud.percent) {
                        mergeDecision.local
                    } else {
                        mergeDecision.cloud
                    }
                    progressRepository.saveProgressDirectly(resolved)
                    cloudSyncRepository.uploadReadingProgress(token, resolved)
                }
            }
        }

        mergeDecision
    }

    /**
     * Сравнение локального и облачного прогресса.
     * Критерий "новее":
     * - Сравниваем timestamp последнего обновления прогресса (lastReadDate).
     * - При конфликте (одинаковые даты) — выбираем больший percent.
     */
    fun evaluateMerge(local: ReadingProgress?, cloud: ReadingProgress?): MergeResult {
        if (local == null && cloud == null) return MergeResult.NoProgress
        if (local == null && cloud != null) return MergeResult.UseCloud(cloud)
        if (local != null && cloud == null) return MergeResult.UseLocal(local)

        // Оба прогресса существуют, сравниваем даты изменений
        val localTime = local!!.lastReadDate.time
        val cloudTime = cloud!!.lastReadDate.time

        return when {
            localTime > cloudTime -> MergeResult.UseLocal(local)
            cloudTime > localTime -> MergeResult.UseCloud(cloud)
            else -> {
                // В случае конфликта (одинаковое время до миллисекунд), сравниваем процент
                if (local.percent > cloud.percent) {
                    MergeResult.UseLocal(local)
                } else if (cloud.percent > local.percent) {
                    MergeResult.UseCloud(cloud)
                } else {
                    // Полное совпадение
                    MergeResult.UseLocal(local)
                }
            }
        }
    }
}
