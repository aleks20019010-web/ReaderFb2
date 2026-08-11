package com.nightread.app.data

import java.io.File
import java.util.Locale

/**
 * Вспомогательный класс для определения ключа синхронизации книг.
 * - Для файлов формата FB2 и FB2.ZIP (.fb2, .fb2.zip, .fbz) синхронизация выполняется по SHA-1 хэшу.
 * - Для всех остальных форматов (EPUB, PDF, TXT, DOCX, MOBI, FB3 и т.д.) синхронизация выполняется по имени файла.
 */
object SyncKeyHelper {

    /**
     * Проверяет, является ли файл форматом FB2 или FB2.ZIP/FBZ.
     */
    fun isFb2OrFb2Zip(fileNameOrPath: String): Boolean {
        val lower = fileNameOrPath.lowercase(Locale.ROOT)
        return lower.endsWith(".fb2") || lower.endsWith(".fb2.zip") || lower.endsWith(".fbz")
    }

    /**
     * Возвращает ключ синхронизации для файла.
     * Если передана заведомо корректная строка SHA-1, возвращается она.
     * В противном случае возвращается имя файла в нижнем регистре.
     */
    fun getSyncKey(fileNameOrPath: String, sha1: String?): String {
        if (!sha1.isNullOrBlank()) {
            return sha1
        }
        val fileName = File(fileNameOrPath).name
        return fileName.lowercase(Locale.ROOT)
    }
}
