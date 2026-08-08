package com.nightread.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import android.util.Log
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * ==============================================================================
 * SQL-СХЕМА ТАБЛИЦЫ ПРОГРЕССА (Room / SQLite)
 * ==============================================================================
 *
 * CREATE TABLE IF NOT EXISTS `reading_progress` (
 *     `bookId` TEXT NOT NULL,
 *     `pageIndex` INTEGER NOT NULL,
 *     `totalPages` INTEGER NOT NULL DEFAULT 0,
 *     `timestamp` INTEGER NOT NULL,
 *     PRIMARY KEY(`bookId`)
 * );
 * ==============================================================================
 */

/**
 * Entity-класс для Room БД
 */
@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val bookId: String,
    val pageIndex: Int,
    val totalPages: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val textOffset: Int = 0
)

/**
 * DAO для работы с таблицей прогресса
 */
@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId LIMIT 1")
    suspend fun getProgress(bookId: String): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId LIMIT 1")
    fun getProgressSync(bookId: String): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ReadingProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveProgressSync(progress: ReadingProgressEntity)

    @Query("DELETE FROM reading_progress WHERE bookId = :bookId")
    suspend fun deleteProgress(bookId: String)
}

/**
 * Модель записи прогресса чтения в памяти и хранилище.
 */
data class ReadingProgressRecord(
    val bookId: String,
    val pageIndex: Int,
    val totalPages: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceName: String = "Unknown",
    val textOffset: Int = 0
)

/**
 * SafeProgressManager - класс надежного сохранения и восстановления
 * прогресса чтения с многоуровневым резервированием (Multi-level persistence):
 *
 * Уровень 1: Память (Memory Cache in ConcurrentHashMap)
 * Уровень 2: SQLite / Room DB (Основной источник данных)
 * Уровень 3: SharedPreferences (Быстрый сбойный fallback)
 * Уровень 4: Файл-контрольная точка (Checkpoint JSON File каждые 5-10 страниц)
 *
 * Алгоритм восстановления с приоритетами:
 * Память -> Room БД -> SharedPreferences -> Checkpoint File -> 0
 * С сопоставлением временных меток (timestamp) для выбора наиболе свежей записи.
 */
class SafeProgressManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // SharedPreferences для быстрого fallback
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Уровень 1: Кэш в памяти (Memory Cache)
    private val memoryCache = ConcurrentHashMap<String, ReadingProgressRecord>()

    // Папка для контрольных точек (Checkpoint Files)
    private val checkpointsDir: File by lazy {
        File(appContext.filesDir, "checkpoints").apply {
            if (!exists()) mkdirs()
        }
    }

    // Получение DAO из общей БД приложения
    private val progressDao: ReadingProgressDao? by lazy {
        try {
            AppDatabase.getDatabase(appContext).readingProgressDao()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации Room Dao: ${e.message}", e)
            null
        }
    }

    companion object {
        private const val TAG = "SafeProgressManager"
        private const val PREFS_NAME = "safe_reading_progress_prefs"
        private const val KEY_LAST_OPENED_BOOK_ID = "last_opened_book_id"
        private const val CHECKPOINT_PAGE_INTERVAL = 5 // Интервал создания контрольной точки файла (каждые 5 страниц)

        @Volatile
        private var instance: SafeProgressManager? = null

        fun getInstance(context: Context): SafeProgressManager {
            return instance ?: synchronized(this) {
                instance ?: SafeProgressManager(context).also { instance = it }
            }
        }
    }

    // =========================================================================
    // 1. СОХРАНЕНИЕ ПРОГРЕССА
    // =========================================================================

    /**
     * Сохранение прогресса при КАЖДОМ перелистывании страницы.
     * Записывает во все уровни (Память -> SP -> Room -> File Checkpoint).
     */
    fun saveProgress(bookId: String, pageIndex: Int, totalPages: Int = 0, textOffset: Int = 0) {
        if (bookId.isBlank()) return

        val now = System.currentTimeMillis()
        val record = ReadingProgressRecord(
            bookId = bookId,
            pageIndex = pageIndex,
            totalPages = totalPages,
            timestamp = now,
            sourceName = "Memory",
            textOffset = textOffset
        )

        // 1. Уровень 1: Память
        memoryCache[bookId] = record

        // 2. Уровень 3: SharedPreferences (быстрый синхронный commit/apply)
        saveToPreferences(record)

        // 3. Сохранение ID последней активной книги
        saveLastOpenedBookId(bookId)

        // 4. Асинхронное/Синхронное сохранение в Room БД и файл контрольной точки
        scope.launch {
            // Уровень 2: Room SQLite БД
            saveToRoomDb(record)

            // Уровень 4: Файл-контрольная точка (Checkpoint File каждые N страниц)
            val lastCheckpointPage = getLastCheckpointPage(bookId)
            if (pageIndex == 0 || Math.abs(pageIndex - lastCheckpointPage) >= CHECKPOINT_PAGE_INTERVAL) {
                saveCheckpointFile(record)
                setLastCheckpointPage(bookId, pageIndex)
            }
        }
    }

    /**
     * Синхронное сохранение (при закрытии приложения/Activity onPause/onDestroy)
     */
    fun saveProgressSync(bookId: String, pageIndex: Int, totalPages: Int = 0, textOffset: Int = 0) {
        if (bookId.isBlank()) return

        val now = System.currentTimeMillis()
        val record = ReadingProgressRecord(
            bookId = bookId,
            pageIndex = pageIndex,
            totalPages = totalPages,
            timestamp = now,
            sourceName = "Memory",
            textOffset = textOffset
        )

        // 1. Память
        memoryCache[bookId] = record

        // 2. SharedPreferences (Синхронный commit)
        saveToPreferencesSync(record)

        // 3. Последняя книга
        saveLastOpenedBookIdSync(bookId)

        // 4. Checkpoint файл
        saveCheckpointFile(record)

        // 5. Room БД
        try {
            progressDao?.saveProgressSync(
                ReadingProgressEntity(
                    bookId = bookId,
                    pageIndex = pageIndex,
                    totalPages = totalPages,
                    timestamp = now,
                    textOffset = textOffset
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка критического сохранения в Room: ${e.message}")
        }
    }

    // =========================================================================
    // 2. ВОССТАНОВЛЕНИЕ ПРОГРЕССА (С ПРИОРИТЕТАМИ И СРАВНЕНИЕМ СВЕЖЕСТИ)
    // =========================================================================

    /**
     * Загрузка последней сохраненной страницы книги с приоритетами:
     * Память -> Room БД -> SharedPreferences -> Файл контрольной точки -> 0
     *
     * Если у менее приоритетного источника временная метка свежее — берется свежайшая запись.
     */
    suspend fun loadProgress(bookId: String): Int {
        val record = loadProgressRecord(bookId)
        return record.pageIndex
    }

    /**
     * Получить полную запись прогресса чтения.
     */
    suspend fun loadProgressRecord(bookId: String): ReadingProgressRecord {
        if (bookId.isBlank()) return ReadingProgressRecord(bookId, 0, 0, 0L, "Default", 0)

        val candidates = mutableListOf<ReadingProgressRecord>()

        // Source 1: Память
        memoryCache[bookId]?.let {
            candidates.add(it.copy(sourceName = "Memory"))
        }

        // Source 2: Room SQLite БД
        try {
            withContext(Dispatchers.IO) {
                progressDao?.getProgress(bookId)?.let { entity ->
                    candidates.add(
                        ReadingProgressRecord(
                            bookId = entity.bookId,
                            pageIndex = entity.pageIndex,
                            totalPages = entity.totalPages,
                            timestamp = entity.timestamp,
                            sourceName = "RoomDB",
                            textOffset = entity.textOffset
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка чтения из Room БД: ${e.message}")
        }

        // Source 3: SharedPreferences
        loadFromPreferences(bookId)?.let {
            candidates.add(it)
        }

        // Source 4: Checkpoint файл
        loadCheckpointFile(bookId)?.let {
            candidates.add(it)
        }

        if (candidates.isEmpty()) {
            return ReadingProgressRecord(bookId = bookId, pageIndex = 0, totalPages = 0, timestamp = 0L, sourceName = "Fallback0", textOffset = 0)
        }

        // 1. Проверяем в порядке приоритета: Memory -> RoomDB -> SharedPreferences -> CheckpointFile
        // 2. Берем наиболее свежий timestamp, если данные отличаются
        val bestRecord = candidates.maxByOrNull { it.timestamp } ?: candidates.first()

        Log.d(TAG, "Восстановлен прогресс для '$bookId': страница ${bestRecord.pageIndex}, offset ${bestRecord.textOffset} из источника [${bestRecord.sourceName}] (timestamp=${bestRecord.timestamp})")

        // Обновляем память свежайшей записью
        memoryCache[bookId] = bestRecord

        return bestRecord
    }

    // =========================================================================
    // 3. РАБОТА С ШАГАМИ ХРАНЕНИЯ (СПЕЦИФИЧЕСКИЕ МЕТОДЫ)
    // =========================================================================

    private fun saveToPreferences(record: ReadingProgressRecord) {
        prefs.edit()
            .putInt("page_${record.bookId}", record.pageIndex)
            .putInt("total_${record.bookId}", record.totalPages)
            .putLong("time_${record.bookId}", record.timestamp)
            .putInt("offset_${record.bookId}", record.textOffset)
            .apply()
    }

    private fun saveToPreferencesSync(record: ReadingProgressRecord) {
        prefs.edit()
            .putInt("page_${record.bookId}", record.pageIndex)
            .putInt("total_${record.bookId}", record.totalPages)
            .putLong("time_${record.bookId}", record.timestamp)
            .putInt("offset_${record.bookId}", record.textOffset)
            .commit()
    }

    private fun loadFromPreferences(bookId: String): ReadingProgressRecord? {
        val page = prefs.getInt("page_$bookId", -1)
        if (page < 0) return null
        val total = prefs.getInt("total_$bookId", 0)
        val time = prefs.getLong("time_$bookId", 0L)
        val offset = prefs.getInt("offset_$bookId", 0)
        return ReadingProgressRecord(
            bookId = bookId,
            pageIndex = page,
            totalPages = total,
            timestamp = time,
            sourceName = "SharedPreferences",
            textOffset = offset
        )
    }

    private suspend fun saveToRoomDb(record: ReadingProgressRecord) {
        try {
            progressDao?.saveProgress(
                ReadingProgressEntity(
                    bookId = record.bookId,
                    pageIndex = record.pageIndex,
                    totalPages = record.totalPages,
                    timestamp = record.timestamp,
                    textOffset = record.textOffset
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сохранения в Room БД: ${e.message}")
        }
    }

    /**
     * Запись чекпоинт-файла с использованием AtomicFile для атомарной гарантии сохранения при краше/отключении питания.
     */
    private fun saveCheckpointFile(record: ReadingProgressRecord) {
        try {
            val file = File(checkpointsDir, "${record.bookId}_checkpoint.json")
            val atomicFile = AtomicFile(file)
            var fos: FileOutputStream? = null
            try {
                fos = atomicFile.startWrite()
                val json = JSONObject().apply {
                    put("bookId", record.bookId)
                    put("pageIndex", record.pageIndex)
                    put("totalPages", record.totalPages)
                    put("timestamp", record.timestamp)
                    put("textOffset", record.textOffset)
                }
                fos.write(json.toString().toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(fos)
            } catch (e: Exception) {
                if (fos != null) {
                    atomicFile.failWrite(fos)
                }
                Log.e(TAG, "Ошибка записи контрольного файла: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при формировании контрольной точки: ${e.message}")
        }
    }

    private fun loadCheckpointFile(bookId: String): ReadingProgressRecord? {
        return try {
            val file = File(checkpointsDir, "${bookId}_checkpoint.json")
            if (!file.exists()) return null
            val text = file.readText(Charsets.UTF_8)
            val json = JSONObject(text)
            ReadingProgressRecord(
                bookId = json.optString("bookId", bookId),
                pageIndex = json.optInt("pageIndex", 0),
                totalPages = json.optInt("totalPages", 0),
                timestamp = json.optLong("timestamp", 0L),
                sourceName = "CheckpointFile",
                textOffset = json.optInt("textOffset", 0)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка чтения контрольного файла для $bookId: ${e.message}")
            null
        }
    }

    private fun getLastCheckpointPage(bookId: String): Int {
        return prefs.getInt("last_checkpoint_page_$bookId", -100)
    }

    private fun setLastCheckpointPage(bookId: String, page: Int) {
        prefs.edit().putInt("last_checkpoint_page_$bookId", page).apply()
    }

    // =========================================================================
    // 4. ХРАНЕНИЕ ID ПОСЛЕДНЕЙ ОТКРЫТОЙ КНИГИ
    // =========================================================================

    fun saveLastOpenedBookId(bookId: String) {
        if (bookId.isNotBlank()) {
            prefs.edit().putString(KEY_LAST_OPENED_BOOK_ID, bookId).apply()
        }
    }

    fun saveLastOpenedBookIdSync(bookId: String) {
        if (bookId.isNotBlank()) {
            prefs.edit().putString(KEY_LAST_OPENED_BOOK_ID, bookId).commit()
        }
    }

    fun getLastOpenedBookId(): String? {
        val id = prefs.getString(KEY_LAST_OPENED_BOOK_ID, null)
        return if (id.isNullOrBlank()) null else id
    }

    fun clearLastOpenedBookId() {
        prefs.edit().remove(KEY_LAST_OPENED_BOOK_ID).apply()
    }
}
