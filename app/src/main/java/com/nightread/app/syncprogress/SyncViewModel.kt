package com.nightread.app.syncprogress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nightread.app.data.YandexDiskApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(
        val message: String,
        val cacheHits: Long,
        val cacheMisses: Long,
        val duplicates: List<CloudDuplicateInfo> = emptyList()
    ) : SyncState()
    data class Error(val message: String) : SyncState()
    data class ConflictDetected(
        val local: ReadingProgress,
        val cloud: ReadingProgress,
        val resolveLocal: () -> Unit,
        val resolveCloud: () -> Unit
    ) : SyncState()
}

class SyncViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SyncDatabase.getDatabase(application)
    
    // Ленивое создание Retrofit клиента для API Яндекс Диска
    private val yandexDiskApi: YandexDiskApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://cloud-api.yandex.net/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(YandexDiskApi::class.java)
    }

    private val sha1CacheRepository = Sha1CacheRepository(
        context = application,
        dao = db.syncSha1CacheDao(),
        yandexDiskApi = yandexDiskApi
    )

    private val progressRepository = ProgressRepository(
        progressDao = db.syncReadingProgressDao(),
        bookDao = db.syncBookDao()
    )

    private val cloudSyncRepository = CloudSyncRepository(
        context = application,
        yandexDiskApi = yandexDiskApi
    )

    private val syncReadingProgressUseCase = SyncReadingProgressUseCase(
        sha1CacheRepository = sha1CacheRepository,
        progressRepository = progressRepository,
        cloudSyncRepository = cloudSyncRepository
    )

    private val syncBooksUseCase = SyncBooksUseCase(
        sha1CacheRepository = sha1CacheRepository,
        progressRepository = progressRepository,
        syncBookDao = db.syncBookDao()
    )

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init {
        // Очищаем просроченный кеш старше 7 дней при старте
        viewModelScope.launch {
            sha1CacheRepository.cleanExpiredCache()
        }
    }

    /**
     * Запуск процесса синхронизации списка файлов прогресса
     */
    fun startSync(token: String, accountId: String, cloudFilesList: List<CloudMetadata>) {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            sha1CacheRepository.resetStats()

            try {
                // 1. Синхронизируем список книг с дедупликацией в облаке
                val duplicates = syncBooksUseCase.syncBooks(token, accountId, cloudFilesList)

                // 2. Для каждой книги синхронизируем прогресс чтения
                for (cloudFile in cloudFilesList) {
                    val cloudPath = cloudFile.path
                    val fileSize = cloudFile.size
                    val fileModified = cloudFile.modified

                    // Синхронизируем прогресс для каждой книги
                    syncReadingProgressUseCase.syncBookProgress(
                        token = token,
                        accountId = accountId,
                        cloudPath = cloudPath,
                        fileSize = fileSize,
                        fileModified = fileModified,
                        onConflict = { local, cloud ->
                            // Возвращаем Deferred или приостанавливаем корутину до ручного разрешения конфликта в UI
                            // Для демонстрации мы создаем приостановку корутины и переводим стейт в ConflictDetected
                            suspendConflictResolution(local, cloud)
                        }
                    )
                }

                _syncState.value = SyncState.Success(
                    message = "Синхронизация прогресса завершена успешно!",
                    cacheHits = sha1CacheRepository.getCacheHits(),
                    cacheMisses = sha1CacheRepository.getCacheMisses(),
                    duplicates = duplicates
                )

            } catch (e: IOException) {
                _syncState.value = SyncState.Error("Сетевая ошибка синхронизации: ${e.localizedMessage}")
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("Ошибка во время синхронизации: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Перенос прогресса чтения со старой версии книги (старый SHA1) на новую (новый SHA1).
     * Требование 7: Если загружена новая версия книги — создаём новую запись, прогресс сбрасываем (или переносим по желанию пользователя).
     */
    fun transferReadingProgress(fromSha1: String, toSha1: String) {
        viewModelScope.launch {
            val oldProgress = progressRepository.getProgressForBook(fromSha1)
            if (oldProgress != null) {
                val newProgress = oldProgress.copy(
                    bookId = toSha1,
                    lastReadDate = java.util.Date() // обновляем дату переноса
                )
                progressRepository.saveProgressDirectly(newProgress)
                android.util.Log.i("SyncViewModel", "Successfully transferred reading progress from $fromSha1 to $toSha1")
            }
        }
    }

    private var conflictResolver: kotlinx.coroutines.CompletableDeferred<ReadingProgress>? = null

    /**
     * Вспомогательный метод для ручного интерактивного выбора прогресса в UI при конфликте.
     */
    private suspend fun suspendConflictResolution(
        local: ReadingProgress,
        cloud: ReadingProgress
    ): ReadingProgress {
        val deferred = kotlinx.coroutines.CompletableDeferred<ReadingProgress>()
        conflictResolver = deferred

        _syncState.value = SyncState.ConflictDetected(
            local = local,
            cloud = cloud,
            resolveLocal = {
                deferred.complete(local)
                _syncState.value = SyncState.Syncing
            },
            resolveCloud = {
                deferred.complete(cloud)
                _syncState.value = SyncState.Syncing
            }
        )

        return deferred.await()
    }

    /**
     * Очистка кэша при смене аккаунта
     */
    fun onAccountChanged(accountId: String) {
        viewModelScope.launch {
            sha1CacheRepository.resetCacheForAccount(accountId)
            _syncState.value = SyncState.Idle
        }
    }

    /**
     * Принудительное автосохранение прогресса при смене страницы пользователем
     */
    fun onPageTurned(bookId: String, page: Int, totalPages: Int, deviceId: String) {
        val percent = (page.toDouble() / totalPages.toDouble()) * 100.0
        val progress = ReadingProgress(
            bookId = bookId,
            currentPage = page,
            percent = percent,
            lastReadDate = java.util.Date(),
            deviceId = deviceId
        )
        // Вызов дебаунс-сохранения (2 секунды)
        progressRepository.saveProgressWithDebounce(progress)
    }
}
