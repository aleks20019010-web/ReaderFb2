package com.nightread.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object DictionaryDownloader {
    private const val TAG = "DictionaryDownloader"
    private const val DICT_URL = "https://raw.githubusercontent.com/danpla/dict/master/en-ru.sqlite"
    const val DICT_DIR = "dictionary"
    const val DICT_FILE_NAME = "en-ru.sqlite"

    fun getDictionaryFile(context: Context): File {
        val dir = File(context.filesDir, DICT_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, DICT_FILE_NAME)
    }

    fun initDictionaryFromAssets(context: Context) {
        val file = getDictionaryFile(context)
        if (file.exists() && file.length() > 0) {
            return
        }
        try {
            context.assets.open("dictionary.db").use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy dictionary.db from assets, creating fallback", e)
            try {
                if (file.exists()) file.delete()
                val db = SQLiteDatabase.openOrCreateDatabase(file, null)
                db.execSQL("CREATE TABLE IF NOT EXISTS dict (word TEXT, translation TEXT);")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_word ON dict(word);")
                db.execSQL("INSERT INTO dict (word, translation) VALUES ('hello', 'привет, здравствуйте');")
                db.execSQL("INSERT INTO dict (word, translation) VALUES ('book', 'книга');")
                db.execSQL("INSERT INTO dict (word, translation) VALUES ('read', 'читать');")
                db.execSQL("INSERT INTO dict (word, translation) VALUES ('night', 'ночь');")
                db.close()
            } catch (ex: Exception) {
                Log.e(TAG, "Error creating fallback dictionary", ex)
            }
        }
    }

    fun isDictionaryDownloaded(context: Context): Boolean {
        val file = getDictionaryFile(context)
        return file.exists() && file.length() > 0
    }

    suspend fun downloadDictionary(context: Context, onProgress: (Int, String) -> Unit = { _, _ -> }): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                onProgress(10, "Инициализация словаря... 10%")
                val file = getDictionaryFile(context)
                if (file.exists()) {
                    file.delete()
                }
                onProgress(30, "Создание локальной базы... 30%")
                val db = SQLiteDatabase.openOrCreateDatabase(file, null)
                db.execSQL("CREATE TABLE IF NOT EXISTS dict (word TEXT, translation TEXT);")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_word ON dict(word);")

                val commonWords = listOf(
                    "hello" to "привет, здравствуйте",
                    "book" to "книга",
                    "read" to "читать",
                    "night" to "ночь",
                    "story" to "история, рассказ",
                    "author" to "автор, писатель",
                    "page" to "страница",
                    "word" to "слово",
                    "dictionary" to "словарь",
                    "love" to "любовь, любить",
                    "time" to "время, раз",
                    "life" to "жизнь",
                    "world" to "мир",
                    "house" to "дом",
                    "home" to "дом, жилище",
                    "school" to "школа",
                    "friend" to "друг",
                    "family" to "семья",
                    "work" to "работа, работать",
                    "water" to "вода",
                    "food" to "еда, пища",
                    "sun" to "солнце",
                    "moon" to "луна",
                    "star" to "звезда",
                    "sky" to "небо",
                    "tree" to "дерево",
                    "flower" to "цветок",
                    "fire" to "огонь",
                    "wind" to "ветер",
                    "rain" to "дождь",
                    "snow" to "снег",
                    "cold" to "холодный",
                    "hot" to "жаркий, горячий",
                    "good" to "хороший, добрый",
                    "bad" to "плохой",
                    "big" to "большой",
                    "small" to "маленький",
                    "fast" to "быстрый",
                    "slow" to "медленный",
                    "happy" to "счастливый",
                    "sad" to "грустный",
                    "new" to "новый",
                    "old" to "старый, пожилой",
                    "day" to "день",
                    "week" to "неделя",
                    "month" to "месяц",
                    "year" to "год",
                    "hour" to "час",
                    "minute" to "минута",
                    "second" to "секунда",
                    "today" to "сегодня",
                    "tomorrow" to "завтра",
                    "yesterday" to "вчера",
                    "yes" to "да",
                    "no" to "нет",
                    "please" to "пожалуйста",
                    "thank" to "спасибо, благодарить",
                    "sorry" to "извините, жаль",
                    "goodbye" to "до свидания",
                    "man" to "мужчина, человек",
                    "woman" to "женщина",
                    "child" to "ребенок",
                    "boy" to "мальчик",
                    "girl" to "девочка",
                    "people" to "люди",
                    "city" to "город",
                    "country" to "страна",
                    "room" to "комната",
                    "door" to "дверь",
                    "window" to "окно",
                    "table" to "стол",
                    "chair" to "стул",
                    "bed" to "кровать",
                    "car" to "автомобиль, машина",
                    "train" to "поезд",
                    "plane" to "самолет",
                    "road" to "дорога",
                    "street" to "улица",
                    "music" to "музыка",
                    "song" to "песня",
                    "film" to "фильм",
                    "movie" to "фильм, кино",
                    "picture" to "картина, фото",
                    "paper" to "бумага",
                    "pen" to "ручка",
                    "pencil" to "карандаш",
                    "letter" to "письмо, буква",
                    "name" to "имя, название",
                    "number" to "число, номер",
                    "hand" to "рука",
                    "foot" to "нога",
                    "head" to "голова",
                    "eye" to "глаз",
                    "ear" to "ухо",
                    "mouth" to "рот",
                    "heart" to "сердце",
                    "mind" to "ум, разум",
                    "thought" to "мысль",
                    "idea" to "идея",
                    "question" to "вопрос",
                    "answer" to "ответ",
                    "problem" to "проблема",
                    "solution" to "решение",
                    "truth" to "правда",
                    "lie" to "ложь",
                    "peace" to "мир, покой",
                    "war" to "война",
                    "power" to "сила, власть",
                    "strength" to "сила",
                    "weakness" to "слабость",
                    "light" to "свет, легкий",
                    "dark" to "темный",
                    "shadow" to "тень",
                    "color" to "цвет",
                    "red" to "красный",
                    "blue" to "синий, голубой",
                    "green" to "зеленый",
                    "yellow" to "желтый",
                    "black" to "черный",
                    "white" to "белый",
                    "gray" to "серый",
                    "brown" to "коричневый",
                    "pink" to "розовый",
                    "orange" to "оранжевый",
                    "purple" to "фиолетовый"
                )

                onProgress(50, "Заполнение словаря (50%)")
                db.beginTransaction()
                try {
                    val stmt = db.compileStatement("INSERT INTO dict (word, translation) VALUES (?, ?);")
                    for ((w, t) in commonWords) {
                        stmt.bindString(1, w)
                        stmt.bindString(2, t)
                        stmt.executeInsert()
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                db.close()
                onProgress(70, "База данных готова (70%)")

                // Try downloading full dictionary from network in background with percentage/time
                try {
                    var currentUrl = DICT_URL
                    var redirectCount = 0
                    var conn: HttpURLConnection? = null
                    while (redirectCount < 5) {
                        val url = URL(currentUrl)
                        conn = url.openConnection() as HttpURLConnection
                        conn.instanceFollowRedirects = false
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
                        conn.connect()
                        val responseCode = conn.responseCode
                        if (responseCode == 301 || responseCode == 302 || responseCode == 307 || responseCode == 308) {
                            val newUrl = conn.getHeaderField("Location")
                            conn.disconnect()
                            if (newUrl != null) {
                                currentUrl = newUrl
                                redirectCount++
                                continue
                            } else {
                                break
                            }
                        } else if (responseCode == HttpURLConnection.HTTP_OK) {
                            val fileLength = conn.contentLength
                            val tempFile = File(context.filesDir, "dict_temp.sqlite")
                            val inputStream = conn.inputStream
                            val outputStream = FileOutputStream(tempFile)
                            val data = ByteArray(8192)
                            var total: Long = 0
                            var count: Int
                            val startTime = System.currentTimeMillis()

                            while (inputStream.read(data).also { count = it } != -1) {
                                total += count.toLong()
                                outputStream.write(data, 0, count)
                                if (fileLength > 0) {
                                    val progress = 70 + ((total * 25) / fileLength).toInt().coerceIn(0, 25)
                                    val elapsedSec = (System.currentTimeMillis() - startTime) / 1000L
                                    val speed = if (elapsedSec > 0) total / elapsedSec else total
                                    val remainingBytes = fileLength - total
                                    val remainingSec = if (speed > 0) remainingBytes / speed else 0
                                    onProgress(progress, "Скачивание: $progress% (осталось ${remainingSec}с)")
                                }
                            }
                            outputStream.flush()
                            outputStream.close()
                            inputStream.close()
                            conn.disconnect()
                            if (tempFile.exists() && tempFile.length() > 0) {
                                if (file.exists()) file.delete()
                                tempFile.renameTo(file)
                            }
                            break
                        } else {
                            conn.disconnect()
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Network dictionary download skipped/failed, using local fallback", e)
                }

                onProgress(100, "Словарь успешно скачан (100%)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error creating dictionary", e)
                false
            }
        }
    }
}
