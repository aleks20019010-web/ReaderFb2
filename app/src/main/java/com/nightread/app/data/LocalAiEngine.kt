package com.nightread.app.data

import android.content.Context
import android.util.Log
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

data class ClassicBookData(
    val annotation: String,
    val summary: String,
    val characters: String
)

object LocalAiEngine {

    private const val TAG = "LocalAiEngine"

    var isSimulatedMode = false
    var isOfflineModelReady = true

    fun isModelActive(context: Context? = null): Boolean {
        return true
    }

    fun hasLoadedLocalModel(): Boolean {
        return true
    }

    fun initRealModel(context: Context): Boolean {
        isOfflineModelReady = true
        isSimulatedMode = false
        return true
    }

    /**
     * Generates a book summary using DeepSeek API + RAG context
     */
    fun generateSummary(context: Context, book: BookEntity): String {
        Log.i(TAG, "Generating summary for book: ${book.title}")

        val classicMatch = findClassicBook(book.title)
        
        val ragQuery = "сюжет главный герой ключевой конфликт развязка тема"
        val ragChunks = RAGManager.searchRAG(context, book, ragQuery, topK = 10)
        
        val contextText = if (ragChunks.isNotEmpty()) {
            RAGManager.formatSearchResults(ragChunks)
        } else {
            getBookSampleText(book)
        }

        val userPrompt = PromptTemplates.bookSummary(contextText)
        val deepSeekResult = DeepSeekEngine.query(
            context = context,
            systemPrompt = PromptTemplates.SYSTEM_PROMPT,
            userPrompt = userPrompt
        )

        return if (!deepSeekResult.contains("Не удалось получить ответ")) {
            deepSeekResult
        } else if (classicMatch != null && classicMatch.summary.isNotBlank()) {
            classicMatch.summary
        } else {
            deepSeekResult
        }
    }

    /**
     * Generates a character breakdown using DeepSeek API + RAG context
     */
    fun generateCharacters(context: Context, book: BookEntity): String {
        Log.i(TAG, "Generating character analysis for book: ${book.title}")

        val classicMatch = findClassicBook(book.title)

        val ragQuery = "персонажи герои характеры мотивы конфликты"
        val ragChunks = RAGManager.searchRAG(context, book, ragQuery, topK = 10)

        val contextText = if (ragChunks.isNotEmpty()) {
            RAGManager.formatSearchResults(ragChunks)
        } else {
            getBookSampleText(book)
        }

        val userPrompt = PromptTemplates.analyzeAllCharacters(book.title, contextText)
        val deepSeekResult = DeepSeekEngine.query(
            context = context,
            systemPrompt = PromptTemplates.SYSTEM_PROMPT,
            userPrompt = userPrompt
        )

        return if (!deepSeekResult.contains("Не удалось получить ответ")) {
            deepSeekResult
        } else if (classicMatch != null && classicMatch.characters.isNotBlank()) {
            classicMatch.characters
        } else {
            deepSeekResult
        }
    }

    /**
     * Generates an annotation using DeepSeek API + RAG context
     */
    fun generateAnnotation(context: Context, book: BookEntity): String {
        Log.i(TAG, "Generating annotation for book: ${book.title}")

        val classicMatch = findClassicBook(book.title)

        val ragQuery = "аннотация завязка атмосфера основной сюжет"
        val ragChunks = RAGManager.searchRAG(context, book, ragQuery, topK = 10)

        val contextText = if (ragChunks.isNotEmpty()) {
            RAGManager.formatSearchResults(ragChunks)
        } else {
            getBookSampleText(book)
        }

        val userPrompt = """На основе этих отрывков из книги составь интересную краткую аннотацию в 3-5 предложениях.

Отрывки из книги (контекст):
$contextText

Начинай сразу с аннотации. Не пиши "Аннотация:" или "Ответ:".
<|reasoning|>"""

        val deepSeekResult = DeepSeekEngine.query(
            context = context,
            systemPrompt = PromptTemplates.SYSTEM_PROMPT,
            userPrompt = userPrompt
        )

        return if (!deepSeekResult.contains("Не удалось получить ответ")) {
            deepSeekResult
        } else if (classicMatch != null && classicMatch.annotation.isNotBlank()) {
            classicMatch.annotation
        } else {
            deepSeekResult
        }
    }

    /**
     * Custom Prompt Processor with optional BookEntity
     */
    fun customAiPrompt(
        context: Context,
        prompt: String,
        book: BookEntity?,
        actionType: String = ""
    ): String {
        val ragChunks = if (book != null) {
            RAGManager.searchRAG(context, book, prompt, topK = 10)
        } else {
            emptyList()
        }

        val ragContext = if (ragChunks.isNotEmpty()) {
            RAGManager.formatSearchResults(ragChunks)
        } else if (book != null) {
            getBookSampleText(book)
        } else {
            ""
        }

        return executeCustomPromptLogic(context, prompt, book?.title ?: "", ragContext, actionType)
    }

    /**
     * Custom Prompt Processor with direct text context snippet
     */
    fun customAiPromptWithSnippet(
        context: Context,
        prompt: String,
        contextSnippet: String?,
        actionType: String = ""
    ): String {
        val ragContext = if (!contextSnippet.isNullOrBlank()) {
            RAGManager.indexBook(contextSnippet)
            val searchResults = RAGManager.search(prompt, topK = 10)
            if (searchResults.isNotEmpty()) RAGManager.formatSearchResults(searchResults) else contextSnippet
        } else {
            ""
        }

        return executeCustomPromptLogic(context, prompt, "", ragContext, actionType)
    }

    /**
     * Overload for simple prompt with no book context
     */
    fun customAiPrompt(
        context: Context,
        prompt: String
    ): String {
        return customAiPrompt(context, prompt, null as BookEntity?, "")
    }

    private fun executeCustomPromptLogic(
        context: Context,
        prompt: String,
        bookTitle: String,
        ragContext: String,
        actionType: String
    ): String {
        val upperAction = actionType.uppercase(Locale.ROOT)
        
        val userPrompt = when (upperAction) {
            "EXPLAIN" -> PromptTemplates.explainTerm(prompt, ragContext)
            "TRANSLATE" -> PromptTemplates.translateWord(prompt, ragContext)
            "WHO_IS" -> {
                val lowerKey = prompt.lowercase(Locale.ROOT).trim()
                if (CLASSIC_CHARACTERS_DB.containsKey(lowerKey)) {
                    return CLASSIC_CHARACTERS_DB[lowerKey]!!
                }
                PromptTemplates.whoIsCharacter(prompt, ragContext)
            }
            "CHARACTER", "CHARACTER_ANALYSIS" -> PromptTemplates.characterAnalysis(prompt, bookTitle, ragContext)
            "CHARACTER_ARC" -> PromptTemplates.characterArcAnalysis(prompt, bookTitle, ragContext)
            "SUMMARIZE", "SUMMARY" -> PromptTemplates.bookSummary(ragContext)
            else -> {
                if (prompt.contains("Кто такой", ignoreCase = true) || prompt.contains("Кто эта", ignoreCase = true)) {
                    val charName = prompt.replace("Кто такой", "", ignoreCase = true)
                        .replace("Кто эта", "", ignoreCase = true)
                        .replace("?", "").trim()
                    PromptTemplates.whoIsCharacter(charName, ragContext)
                } else if (prompt.contains("проанализируй", ignoreCase = true) && prompt.contains("персонаж", ignoreCase = true)) {
                    PromptTemplates.characterAnalysis(prompt, bookTitle, ragContext)
                } else if (prompt.contains("краткое содержание", ignoreCase = true) || prompt.contains("содержание", ignoreCase = true)) {
                    PromptTemplates.bookSummary(ragContext)
                } else if (prompt.contains("значение слова", ignoreCase = true) || prompt.contains("объясни", ignoreCase = true)) {
                    PromptTemplates.explainTerm(prompt, ragContext)
                } else {
                    """$prompt

${if (ragContext.isNotBlank()) "Отрывки из книги (контекст для анализа):\n$ragContext" else ""}

Начинай сразу с ответа, без вступлений.
<|reasoning|>"""
                }
            }
        }

        return DeepSeekEngine.query(
            context = context,
            systemPrompt = PromptTemplates.SYSTEM_PROMPT,
            userPrompt = userPrompt
        )
    }

    // --- HELPER METHODS & PRESET DATABASES FOR OFFLINE FALLBACK ---

    fun cleanBookText(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw
        val bodyIdx = text.indexOf("<body", ignoreCase = true)
        if (bodyIdx != -1) {
            text = text.substring(bodyIdx)
        }
        text = text.replace(Regex("<[^>]*>"), " ")
        text = text.replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
        return text.replace(Regex("\\s+"), " ").trim()
    }

    fun getBookSampleText(book: BookEntity): String {
        val path = book.filePath ?: return ""
        val file = File(path)
        if (!file.exists()) return ""
        return try {
            val extension = file.extension.lowercase(Locale.ROOT)
            val fullRawText = if (extension == "zip") {
                readFirstZipEntryText(file)
            } else if (extension == "epub") {
                com.nightread.app.service.EpubParser.parse(file, file.nameWithoutExtension).content
            } else {
                file.readText(StandardCharsets.UTF_8)
            }
            val cleaned = cleanBookText(fullRawText)
            if (cleaned.isNotBlank()) {
                RAGManager.indexBook(cleaned)
            }
            if (cleaned.length <= 15000) {
                cleaned
            } else {
                val len = cleaned.length
                val part1 = cleaned.substring(0, minOf(5000, len))
                val midStart = len / 3
                val part2 = cleaned.substring(midStart, minOf(midStart + 5000, len))
                val lateStart = (len * 2) / 3
                val part3 = cleaned.substring(lateStart, minOf(lateStart + 5000, len))
                "$part1\n\n...[продолжение книги]...\n\n$part2\n\n...[кульминация книги]...\n\n$part3"
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun readFirstZipEntryText(file: File): String {
        return try {
            java.util.zip.ZipInputStream(file.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && (entry.name.endsWith(".fb2", true) || entry.name.endsWith(".txt", true))) {
                        return zis.bufferedReader(StandardCharsets.UTF_8).readText()
                    }
                    entry = zis.nextEntry
                }
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun findClassicBook(title: String): ClassicBookData? {
        if (title.isBlank()) return null
        val cleanTitle = title.lowercase(Locale.ROOT)
            .replace(Regex("[^a-zA-Zа-яА-ЯёЁ0-9\\s]"), "")
            .trim()
        
        for ((key, data) in CLASSICS_DATABASE) {
            val cleanKey = key.lowercase(Locale.ROOT)
                .replace(Regex("[^a-zA-Zа-яА-ЯёЁ0-9\\s]"), "")
                .trim()
            if (cleanTitle.contains(cleanKey) || cleanKey.contains(cleanTitle)) {
                return data
            }
        }
        return null
    }

    private val CLASSICS_DATABASE = mapOf(
        "преступление и наказание" to ClassicBookData(
            annotation = "Глубокий философский роман Федора Достоевского об убийстве ради идеи и последующем духовном возрождении.",
            summary = "В романе описывается судьба бывшего студента Родиона Раскольникова, который совершает убийство старухи-процентщицы ради проверки своей психологической теории. Ключевой конфликт разворачивается в его душе под давлением мук совести.",
            characters = "1. **Родион Раскольников**: Бывший студент, автор теории о делении людей на «право имеющих» и «обыкновенных»."
        )
    )

    private val CLASSIC_CHARACTERS_DB = mapOf(
        "воланд" to "1. **МОТИВАЦИЯ И ВНУТРЕННИЕ ПРОТИВОРЕЧИЯ**\nВоланд выступает высшим судией, проверяющим людей на алчность, трусость и фальшь.",
        "раскольников" to "1. **МОТИВАЦИЯ И ВНУТРЕННИЕ ПРОТИВОРЕЧИЯ**\nЖелание доказать себе статус «необыкновенного человека» сталкивается с глубоким состраданием."
    )
}
