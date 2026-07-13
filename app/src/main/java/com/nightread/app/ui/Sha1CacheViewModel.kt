package com.nightread.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.ResourceItem
import com.nightread.app.data.Sha1CacheRepository
import com.nightread.app.data.YandexDiskApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException

/**
 * Состояние синхронизации и кэширования для UI.
 */
data class SyncUiState(
    val isSyncing: Boolean = false,
    val cacheHits: Long = 0,
    val cacheMisses: Long = 0,
    val statusMessage: String = "Готов к синхронизации",
    val currentFilePath: String = "",
    val processedCount: Int = 0,
    val totalCount: Int = 0
)

/**
 * ViewModel для демонстрации и использования системы кэширования SHA-1 хэшей.
 */
class Sha1CacheViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val sha1CacheDao = database.sha1CacheDao()
    
    // Создаем экземпляр API (в реальном приложении обычно внедряется через DI)
    private val yandexDiskApi: YandexDiskApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://cloud-api.yandex.net/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(YandexDiskApi::class.java)
    }

    // Инициализируем репозиторий
    private val repository = Sha1CacheRepository(application, sha1CacheDao, yandexDiskApi)

    // Экспортируем состояние через StateFlow
    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        updateStats()
    }

    /**
     * Обновление статистики попаданий/промахов кэша.
     */
    fun updateStats() {
        _uiState.update { currentState ->
            currentState.copy(
                cacheHits = repository.getCacheHits(),
                cacheMisses = repository.getCacheMisses()
            )
        }
    }

    /**
     * Сброс локальной статистики кэша.
     */
    fun resetCacheStatistics() {
        repository.resetStats()
        updateStats()
    }

    /**
     * Очистка всего кэша (инвалидация).
     */
    fun clearAllCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, statusMessage = "Очистка кэша...") }
            repository.invalidateAll()
            repository.resetStats()
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    cacheHits = 0,
                    cacheMisses = 0,
                    statusMessage = "Кэш успешно очищен"
                )
            }
        }
    }

    /**
     * Инвалидация кэша конкретного аккаунта.
     */
    fun clearAccountCache(accountId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, statusMessage = "Очистка кэша аккаунта...") }
            repository.invalidateAccount(accountId)
            updateStats()
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    statusMessage = "Кэш аккаунта $accountId очищен"
                )
            }
        }
    }

    /**
     * Запускает синхронизацию списка файлов с использованием кэширования SHA-1.
     *
     * @param token Токен авторизации Яндекс Диска
     * @param accountId Идентификатор текущего аккаунта (например, логин)
     * @param cloudPath Путь к папке в облаке для сканирования (например, "/Books")
     */
    fun syncCloudFolder(token: String, accountId: String, cloudPath: String) {
        viewModelScope.launch {
            if (_uiState.value.isSyncing) return@launch

            _uiState.update {
                it.copy(
                    isSyncing = true,
                    statusMessage = "Получение списка файлов из облака...",
                    processedCount = 0,
                    totalCount = 0,
                    currentFilePath = ""
                )
            }

            try {
                val authHeader = if (token.startsWith("OAuth ")) token else "OAuth $token"
                
                // 1. Получаем метаданные папки и файлы через REST API
                val resourceResponse = yandexDiskApi.getResource(authHeader, cloudPath)
                val files = resourceResponse.embedded?.items?.filter { it.type == "file" } ?: emptyList()

                if (files.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            statusMessage = "В папке '$cloudPath' файлы не найдены."
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(totalCount = files.size, statusMessage = "Начало анализа файлов...") }

                // 2. Обрабатываем каждый файл с использованием умного кэширования
                for ((index, fileItem) in files.withIndex()) {
                    val filePath = fileItem.path
                    val fileSize = fileItem.size ?: 0L
                    val fileModified = fileItem.modified ?: ""

                    _uiState.update {
                        it.copy(
                            currentFilePath = fileItem.name,
                            statusMessage = "Анализ файла ${index + 1} из ${files.size}..."
                        )
                    }

                    // Шаг 1: Проверяем, есть ли готовый SHA-1 в метаданных ответа API Яндекс Диска.
                    // (Требование 1: При первом анализе файла получаем его SHA1 через REST API Яндекс Диска, если оно там есть)
                    var sha1 = fileItem.sha1

                    if (sha1.isNullOrBlank()) {
                        // Шаг 2: Если API не вернул SHA-1 в общем списке, используем наш репозиторий кэша.
                        // Он проверит по композитному ключу ${accountId}_${filePath}_${fileSize}_${fileModified}.
                        // Если ключ совпадет - вернет из БД Room.
                        // Если ключ не совпал (файл изменился или первая синхронизация) - скачает временный файл,
                        // вычислит SHA-1 потоковым буфером 8KB и запишет в БД кэша.
                        sha1 = repository.getOrComputeSha1(
                            authToken = token,
                            accountId = accountId,
                            path = filePath,
                            size = fileSize,
                            modified = fileModified
                        )
                    } else {
                        // Если SHA-1 был в метаданных, мы просто обновляем локальный кэш, чтобы при повторной
                        // синхронизации (или когда поле sha1 будет отсутствовать в выдаче) мы могли его вытащить оттуда.
                        repository.updateCache(accountId, filePath, fileSize, fileModified, sha1)
                        // Логируем прямое попадание из метаданных API
                        android.util.Log.d("Sha1CacheVM", "Found SHA1 directly in Yandex metadata for $filePath: $sha1")
                    }

                    _uiState.update {
                        it.copy(
                            processedCount = index + 1,
                            cacheHits = repository.getCacheHits(),
                            cacheMisses = repository.getCacheMisses()
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        statusMessage = "Синхронизация завершена успешно! Обработано ${files.size} файлов."
                    )
                }

            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        statusMessage = "Ошибка: Превышено время ожидания скачивания (30 сек)."
                    )
                }
            } catch (e: IOException) {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        statusMessage = "Сетевая ошибка или диск недоступен: ${e.localizedMessage}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        statusMessage = "Произошла ошибка во время синхронизации: ${e.localizedMessage}"
                    )
                }
            }
        }
    }
}
