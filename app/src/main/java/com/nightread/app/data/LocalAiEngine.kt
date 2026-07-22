package com.nightread.app.data

import android.content.Context
import android.util.Log
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

data class ClassicBookData(
    val annotation: String,
    val summary: String,
    val characters: String
)

object LocalAiEngine {

    private const val TAG = "LocalAiEngine"
    private const val CACHE_PREFS = "cotype_ai_cache_prefs"

    var isOfflineModelReady = false
    @Volatile
    var isProcessing = false

    fun normalizeText(text: String): String {
        return text
            .replace(Regex("\\s+"), " ")                  // Убираем лишние пробелы и переносы
            .replace(Regex("[\\u0000-\\u001F]"), "")        // Убираем управляющие спецсимволы
            .replace("«", "\"")                            // Унификация кавычек
            .replace("»", "\"")
            .replace("“", "\"")
            .replace("”", "\"")
            .trim()
    }

    fun isModelActive(context: Context? = null): Boolean {
        if (LlamaEngine.isLoaded()) return true
        if (context != null) {
            return CotypeModelManager.isModelDownloaded(context)
        }
        return false
    }

    fun hasLoadedLocalModel(): Boolean {
        return LlamaEngine.isLoaded()
    }

    fun initRealModel(context: Context): Boolean {
        if (LlamaEngine.isLoaded()) {
            isOfflineModelReady = true
            return true
        }

        val modelFile = CotypeModelManager.getModelFile(context)
        if (modelFile.exists() && modelFile.length() > 500_000_000L) {
            Log.i(TAG, "Loading Cotype Nano 1.5B model from ${modelFile.absolutePath} into native memory...")
            val modelParams = LlamaModelParams(
                nCtx = 8192,
                nThreads = 4,
                nGpuLayers = 0,
                useMMap = true,
                useMLock = false
            )
            val success = LlamaEngine.loadModel(modelFile.absolutePath, modelParams)
            isOfflineModelReady = success
            return success
        }

        isOfflineModelReady = false
        return false
    }

    /**
     * Executes local inference using Cotype Nano 1.5B (llama.cpp JNI) + RAG Context with 120s timeout
     */
    private fun executeLocalInference(
        context: Context,
        systemPrompt: String,
        userPrompt: String,
        temperature: Float = 0.7f
    ): String {
        if (isProcessing) {
            return "Модель уже выполняет запрос. Пожалуйста, подождите завершения."
        }
        isProcessing = true
        try {
            val normalizedUserPrompt = normalizeText(userPrompt)
            val cacheKey = hashKey("$systemPrompt::$normalizedUserPrompt")
            val cached = getFromCache(context, cacheKey)
            if (!cached.isNullOrBlank()) {
                Log.d(TAG, "Returning cached Cotype Nano response for key $cacheKey")
                return cached
            }

            // Try initializing model if not yet loaded
            if (!LlamaEngine.isLoaded()) {
                initRealModel(context)
            }

            val fullPrompt = """<|im_start|>system
$systemPrompt<|im_end|>
<|im_start|>user
$normalizedUserPrompt<|im_end|>
<|im_start|>assistant
"""

            if (LlamaEngine.isLoaded()) {
                Log.i(TAG, "Executing Cotype Nano 1.5B local native generation with 120s timeout...")
                val genParams = GenerationParams(
                    temperature = temperature,
                    topP = 0.95f,
                    topK = 40,
                    repeatPenalty = 1.1f
                )

                val rawResponse = try {
                    kotlinx.coroutines.runBlocking {
                        kotlinx.coroutines.withTimeoutOrNull(120_000L) {
                            LlamaEngine.generate(fullPrompt, params = genParams, maxTokens = 1024)
                        }
                    } ?: "Превышено время ожидания (120 сек). Попробуйте упростить запрос."
                } catch (e: Exception) {
                    Log.e(TAG, "Error or timeout during execution", e)
                    "Ошибка при локальном запуске Cotype Nano 1.5B."
                }

                if (rawResponse.isNotBlank() && !rawResponse.contains("Ошибка") && !rawResponse.contains("Превышено время")) {
                    val cleaned = cleanResponse(rawResponse)
                    saveToCache(context, cacheKey, cleaned)
                    return cleaned
                } else if (rawResponse.contains("Превышено время")) {
                    return rawResponse
                }
            }

            return ""
        } finally {
            isProcessing = false
        }
    }

    fun cleanResponse(raw: String): String {
        return raw
            .replace(Regex("User:.*"), "")
            .replace(Regex("\\[INST\\].*"), "")
            .replace(Regex("<\\|im_end\\|>"), "")
            .replace(Regex("<\\|im_start\\|>"), "")
            .replace(Regex("<\\|reasoning\\|>"), "")
            .replace(Regex("(.)\\1{10,}"), "$1$1")
            .trim()
            .let { if (it.length < 10) "Не удалось сгенерировать ответ. Попробуйте переформулировать." else it }
    }

    /**
     * Generates a book summary using Cotype Nano 1.5B + RAG context
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
        val localResult = executeLocalInference(
            context = context,
            systemPrompt = PromptTemplates.SYSTEM_PROMPT,
            userPrompt = userPrompt
        )

        return if (localResult.isNotBlank()) {
            localResult
        } else if (classicMatch != null && classicMatch.summary.isNotBlank()) {
            classicMatch.summary
        } else {
            generateRuleBasedSummary(contextText)
        }
    }

    /**
     * Generates a character breakdown using Cotype Nano 1.5B + RAG context
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
        val localResult = executeLocalInference(
            context = context,
            systemPrompt = PromptTemplates.SYSTEM_PROMPT,
            userPrompt = userPrompt
        )

        return if (localResult.isNotBlank()) {
            localResult
        } else if (classicMatch != null && classicMatch.characters.isNotBlank()) {
            classicMatch.characters
        } else {
            generateRuleBasedCharacters(contextText)
        }
    }

    /**
     * Generates an annotation using Cotype Nano 1.5B + RAG context
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

Начинай сразу с аннотации."""

        val localResult = executeLocalInference(
            context = context,
            systemPrompt = PromptTemplates.SYSTEM_PROMPT,
            userPrompt = userPrompt
        )

        return if (localResult.isNotBlank()) {
            localResult
        } else if (classicMatch != null && classicMatch.annotation.isNotBlank()) {
            classicMatch.annotation
        } else {
            generateRuleBasedAnnotation(contextText)
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

Начинай сразу с ответа, без вступлений."""
                }
            }
        }

        val localResult = executeLocalInference(
            context = context,
            systemPrompt = PromptTemplates.SYSTEM_PROMPT,
            userPrompt = userPrompt
        )

        return if (localResult.isNotBlank()) {
            localResult
        } else {
            generateRuleBasedFallback(prompt, ragContext)
        }
    }

    // --- FALLBACK LOGIC & HELPER METHODS ---

    private fun generateRuleBasedSummary(contextText: String): String {
        val snippet = if (contextText.length > 400) contextText.substring(0, 400) + "..." else contextText
        return "Произведение описывает повествование, сосредоточенное вокруг ключевых событий произведения.\n\n" +
                "**Отрывок произведения (RAG)**:\n\"$snippet\""
    }

    private fun generateRuleBasedCharacters(contextText: String): String {
        return "1. **Главный герой**:\n" +
                "- Мотивация: Поиск истины и преодоление сюжетных испытаний.\n" +
                "- Динамика: Меняется под воздействием ключевых событий произведения.\n" +
                "- Конфликт: Противостояние обстоятельствам и внутренний выбор.\n" +
                "- Авторская идея: Раскрытие человеческого характера в сложных ситуациях."
    }

    private fun generateRuleBasedAnnotation(contextText: String): String {
        val snippet = if (contextText.length > 250) contextText.substring(0, 250) + "..." else contextText
        return "Увлекательное литературное произведение, погружающее читателя в глубокую эмоциональную атмосферу. " +
                "Сюжет раскрывает ключевые темы человеческой судьбы и морального выбора.\n\n\"$snippet\""
    }

    private fun generateRuleBasedFallback(prompt: String, ragContext: String): String {
        val snippet = if (ragContext.length > 300) ragContext.substring(0, 300) + "..." else ragContext
        return "Ответ на запрос: \"$prompt\"\n\n" +
                "**Контекст из книги (RAG)**:\n${if (snippet.isNotBlank()) "\"$snippet\"" else "Отрывки из произведения найдены в базе RAG."}"
    }

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

    fun unloadFromMemory() {
        try {
            if (LlamaEngine.isLoaded()) {
                LlamaEngine.nativeUnload()
                Log.i(TAG, "Unloaded Cotype Nano model from RAM")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading model", e)
        } finally {
            isOfflineModelReady = false
        }
    }

    fun clearCache(context: Context) {
        val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.i(TAG, "Cleared AI cache")
    }

    private fun getFromCache(context: Context, key: String): String? {
        val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        return prefs.getString(key, null)
    }

    private fun saveToCache(context: Context, key: String, value: String) {
        val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        val allEntries = prefs.all
        if (allEntries.size >= 100) {
            // Remove oldest 20 entries to cap cache size
            val keysToRemove = allEntries.keys.take(20)
            val editor = prefs.edit()
            for (k in keysToRemove) {
                editor.remove(k)
            }
            editor.apply()
        }
        prefs.edit().putString(key, value).apply()
    }

    private fun hashKey(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }

    private val CLASSICS_DATABASE = mapOf(
        "преступление и наказание" to ClassicBookData(
            annotation = "Глубокий философский роман Федора Достоевского об убийстве ради идеи и последующем духовном возрождении.",
            summary = "В романе описывается судьба бывшего студента Родиона Раскольникова, который совершает убийство старухи-процентщицы ради проверки своей психологической теории. Ключевой конфликт разворачивается в его душе под давлением мук совести.",
            characters = "1. **Родион Раскольников**: Бывший студент, автор теории о делении людей на «право имеющих» и «обыкновенных»."
        ),
        "мастер и маргарита" to ClassicBookData(
            annotation = "Шедевр Михаила Булгакова, объединяющий сатиру на советскую Москву, трагическую историю любви Мастера и Маргариты, а также философское прочтение библейских событий.",
            summary = "В произведении переплетаются сатирический визит Сатаны (Воланда) в Москву 1930-х годов и трагический роман о Понтии Пилате. Главный герой Мастер сталкивается с уничтожением своего произведения тоталитарными критиками.",
            characters = "1. **Мастер**: Создатель романа о Понтии Пилате.\n2. **Воланд**: Сатана, прибывший в Москву с свитой."
        )
    )

    private val CLASSIC_CHARACTERS_DB = mapOf(
        "воланд" to "1. **МОТИВАЦИЯ И ВНУТРЕННИЕ ПРОТИВОРЕЧИЯ**\nВоланд выступает высшим судией, проверяющим людей на алчность, трусость и фальшь.",
        "раскольников" to "1. **МОТИВАЦИЯ И ВНУТРЕННИЕ ПРОТИВОРЕЧИЯ**\nЖелание доказать себе статус «необыкновенного человека» сталкивается с глубоким состраданием."
    )
}
