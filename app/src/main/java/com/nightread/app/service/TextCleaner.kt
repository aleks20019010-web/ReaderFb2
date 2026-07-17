package com.nightread.app.service

import java.text.Normalizer

/**
 * Утилита для очистки текста от "мусорных" символов, нормализации пробелов 
 * и правильной замены типографики. Полезна перед разбивкой текста на страницы.
 */
object TextCleaner {

    data class CleanResult(
        val text: String,
        val originalLength: Int,
        val cleanedLength: Int,
        val removedCount: Int,
        val replacedCount: Int,
        val details: Map<String, Int>
    )

    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val ENTITY_REGEX = Regex("&[a-zA-Z]+;|&#[0-9]+;")
    
    private val HTML_ENTITIES = mapOf(
        "&nbsp;" to " ",
        "&mdash;" to "—",
        "&ndash;" to "–",
        "&quot;" to "\"",
        "&apos;" to "'",
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&laquo;" to "\"",
        "&raquo;" to "\"",
        "&#160;" to " ",
        "&#171;" to "\"",
        "&#187;" to "\"",
        "&#8211;" to "–",
        "&#8212;" to "—",
        "&#8216;" to "'",
        "&#8217;" to "'",
        "&#8220;" to "\"",
        "&#8221;" to "\"",
        "&#8230;" to "...",
        "&#8482;" to "(TM)",
        "&#169;" to "(c)"
    )

    private val CONTROL_CHARS = Regex("[\\u0000-\\u0008\\u000B\\u000E-\\u001F\\u007F]")
    private val ZERO_WIDTH_CHARS = Regex("[\\u200B\\u200C\\u200D\\uFEFF]")
    private val PRIVATE_USE_CHARS = Regex("[\\uE000-\\uF8FF\\uFFFE\\uFFFF]")
    
    private val SPACES = Regex("[\\u00A0\\u202F\\u2007\\u2000-\\u200A\\u205F]")
    private val HYPHENS = Regex("[\\u2010\\u2011]")
    private val SINGLE_QUOTES = Regex("[\\u2018\\u2019\\u201A\\u201B]")
    private val DOUBLE_QUOTES = Regex("[\\u201C\\u201D\\u201E\\u201F\\u00AB\\u00BB]")
    
    private val MULTIPLE_SPACES = Regex(" {2,}")
    private val SPACE_BEFORE_PUNCTUATION = Regex(" +([.,!?:;])")
    private val SPACE_AFTER_QUOTE = Regex("([\"'«„]) +")

    /**
     * Очищает текст от мусора и нормализует типографику.
     * Возвращает String (если collectStats = false) или CleanResult (если collectStats = true).
     */
    fun cleanText(
        text: String,
        removeSoftHyphens: Boolean = true,
        normalizeUnicode: Boolean = true,
        collectStats: Boolean = false
    ): Any {
        return if (collectStats) {
            cleanTextWithStats(text, removeSoftHyphens, normalizeUnicode)
        } else {
            cleanTextFast(text, removeSoftHyphens, normalizeUnicode)
        }
    }

    /**
     * Проверяет наличие невидимых и мусорных символов в тексте.
     */
    fun hasInvisibleCharacters(text: String): Boolean {
        val invisibleRegex = Regex("[\\u0000-\\u0008\\u000B\\u000E-\\u001F\\u007F\\u200B\\u200C\\u200D\\uFEFF\\uE000-\\uF8FF\\uFFFE\\uFFFF]")
        return invisibleRegex.containsMatchIn(text)
    }

    private fun cleanTextFast(
        text: String,
        removeSoftHyphens: Boolean,
        normalizeUnicode: Boolean
    ): String {
        var result = text

        // Convert title tags to CHAPTER tags for FB2 and other formats
        result = result
            .replace(Regex("<title[^>]*>", RegexOption.IGNORE_CASE), "\n\u000C[CHAPTER]")
            .replace(Regex("</title>", RegexOption.IGNORE_CASE), "[/CHAPTER]\n")

        // Replace common block/paragraph tags (and Cyrillic equivalent <р> / </р>) with newlines to keep paragraph breaks
        result = result
            .replace(Regex("<p[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<р[^>]*>", RegexOption.IGNORE_CASE), "\n") // Cyrillic р
            .replace(Regex("</р>", RegexOption.IGNORE_CASE), "\n")   // Cyrillic р
            .replace(Regex("<v[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</v>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")

        // 10. Удаляем HTML-теги
        result = result.replace(HTML_TAG_REGEX, "")

        // 6. Заменяем HTML-сущности
        result = result.replace(ENTITY_REGEX) { match ->
            HTML_ENTITIES[match.value.lowercase()] ?: match.value
        }

        // 7. Переносы строк
        // result = result.replace("\u000C", "\n\n") // Removed to preserve chapter page breaks
        result = result.replace("\r\n", "\n")
        result = result.replace("\r", "\n")

        // 1. Удаляем управляющие и невидимые символы
        result = result.replace(CONTROL_CHARS, "")
        result = result.replace(ZERO_WIDTH_CHARS, "")
        result = result.replace(PRIVATE_USE_CHARS, "")

        // 11. Обрабатываем мягкие переносы
        if (removeSoftHyphens) {
            result = result.replace("\u00AD", "")
        } else {
            result = result.replace("\u00AD", "-")
        }

        // 2. Заменяем спец. пробелы на обычные
        result = result.replace(SPACES, " ")

        // 3. Заменяем дефисы
        result = result.replace(HYPHENS, "-")

        // 4. Заменяем кавычки
        result = result.replace(SINGLE_QUOTES, "'")
        result = result.replace(DOUBLE_QUOTES, "\"")

        // 5. Заменяем тире
        result = result.replace("\u2013", "–")
        result = result.replace("\u2014", "—")
        result = result.replace("\u2212", "-")

        // 8. Схлопываем пробелы
        result = result.replace(MULTIPLE_SPACES, " ")
        result = result.replace(SPACE_BEFORE_PUNCTUATION, "$1")
        result = result.replace(SPACE_AFTER_QUOTE, "$1")

        // 9. Нормализуем Unicode
        if (normalizeUnicode) {
            result = Normalizer.normalize(result, Normalizer.Form.NFC)
        }

        return result
    }

    private fun cleanTextWithStats(
        text: String,
        removeSoftHyphens: Boolean,
        normalizeUnicode: Boolean
    ): CleanResult {
        var result = text
        val details = mutableMapOf<String, Int>()
        var removedCount = 0
        var replacedCount = 0

        fun doReplace(name: String, regex: Regex, replacement: String, isRemoval: Boolean = false) {
            val count = regex.findAll(result).count()
            if (count > 0) {
                result = regex.replace(result, replacement)
                details[name] = count
                if (isRemoval) removedCount += count else replacedCount += count
            }
        }

        fun doReplaceStr(name: String, target: String, replacement: String, isRemoval: Boolean = false) {
            var count = 0
            var idx = 0
            while (true) {
                idx = result.indexOf(target, idx)
                if (idx == -1) break
                count++
                idx += target.length
            }
            if (count > 0) {
                result = result.replace(target, replacement)
                details[name] = count
                if (isRemoval) removedCount += count else replacedCount += count
            }
        }

        // Convert title tags to CHAPTER tags for FB2 and other formats
        result = result
            .replace(Regex("<title[^>]*>", RegexOption.IGNORE_CASE), "\n\u000C[CHAPTER]")
            .replace(Regex("</title>", RegexOption.IGNORE_CASE), "[/CHAPTER]\n")

        // Replace common block/paragraph tags (and Cyrillic equivalent <р> / </р>) with newlines to keep paragraph breaks
        result = result
            .replace(Regex("<p[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<р[^>]*>", RegexOption.IGNORE_CASE), "\n") // Cyrillic р
            .replace(Regex("</р>", RegexOption.IGNORE_CASE), "\n")   // Cyrillic р
            .replace(Regex("<v[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</v>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")

        // 10. Удаляем HTML
        doReplace("HTML tags", HTML_TAG_REGEX, "", isRemoval = true)

        // 6. HTML сущности
        var entityCount = 0
        result = result.replace(ENTITY_REGEX) { match ->
            val replacement = HTML_ENTITIES[match.value.lowercase()]
            if (replacement != null) {
                entityCount++
                replacement
            } else {
                match.value
            }
        }
        if (entityCount > 0) {
            details["HTML entities"] = entityCount
            replacedCount += entityCount
        }

        // 7. Переносы строк
        // doReplaceStr("Form Feed", "\u000C", "\n\n", isRemoval = false) // Removed to preserve chapter page breaks
        doReplaceStr("CRLF", "\r\n", "\n", isRemoval = false)
        doReplaceStr("CR", "\r", "\n", isRemoval = false)

        // 1. Управляющие и мусорные символы
        doReplace("Control chars", CONTROL_CHARS, "", isRemoval = true)
        doReplace("Zero-width chars", ZERO_WIDTH_CHARS, "", isRemoval = true)
        doReplace("Private use chars", PRIVATE_USE_CHARS, "", isRemoval = true)

        // 11. Мягкие переносы
        if (removeSoftHyphens) {
            doReplaceStr("Soft hyphens", "\u00AD", "", isRemoval = true)
        } else {
            doReplaceStr("Soft hyphens", "\u00AD", "-", isRemoval = false)
        }

        // 2, 3, 4, 5
        doReplace("Special spaces", SPACES, " ", isRemoval = false)
        doReplace("Hyphens", HYPHENS, "-", isRemoval = false)
        doReplace("Single quotes", SINGLE_QUOTES, "'", isRemoval = false)
        doReplace("Double quotes", DOUBLE_QUOTES, "\"", isRemoval = false)
        
        doReplaceStr("En dash", "\u2013", "–", isRemoval = false)
        doReplaceStr("Em dash", "\u2014", "—", isRemoval = false)
        doReplaceStr("Minus", "\u2212", "-", isRemoval = false)

        // 8. Схлопывание пробелов
        doReplace("Multiple spaces", MULTIPLE_SPACES, " ", isRemoval = true)
        doReplace("Space before punctuation", SPACE_BEFORE_PUNCTUATION, "$1", isRemoval = true)
        doReplace("Space after quote", SPACE_AFTER_QUOTE, "$1", isRemoval = true)

        // 9. Нормализация
        if (normalizeUnicode) {
            result = Normalizer.normalize(result, Normalizer.Form.NFC)
        }

        return CleanResult(
            text = result,
            originalLength = text.length,
            cleanedLength = result.length,
            removedCount = removedCount,
            replacedCount = replacedCount,
            details = details
        )
    }
}
