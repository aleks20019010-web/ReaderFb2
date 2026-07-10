package com.nightread.app.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

object LocalAIManager {
    private const val TAG = "LocalAIManager"

    // Real URLs for reference as requested
    const val GEMMA_2B_URL = "https://huggingface.co/google/gemma-2b-it-GGUF/resolve/main/gemma-2b-it.Q4_K_M.gguf"
    const val QWEN_1_5B_URL = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
    const val LLAMA_3_2_3B_URL = "https://huggingface.co/meta-llama/Llama-3.2-3B-Instruct-GGUF/resolve/main/llama-3.2-3b-instruct-q4_k_m.gguf"
    
    private var localLLM: LocalLLM? = null
    var isModelLoaded = false
        private set
    private var currentModelPath: String? = null

    private fun getLLM(context: Context): LocalLLM {
        if (localLLM == null) {
            localLLM = LocalLLM.getInstance(context)
        }
        return localLLM!!
    }

    suspend fun loadModel(context: Context, modelPath: String): Boolean = withContext(Dispatchers.IO) {
        if (isModelLoaded && currentModelPath == modelPath) return@withContext true
        
        Log.i(TAG, "Loading local AI model from: $modelPath")
        val success = getLLM(context).loadModel(modelPath)
        
        if (success) {
            isModelLoaded = true
            currentModelPath = modelPath
        }
        return@withContext success
    }

    fun unloadModel() {
        Log.i(TAG, "Unloading local AI model")
        localLLM?.release()
        isModelLoaded = false
        currentModelPath = null
    }

    fun isLoaded(): Boolean = isModelLoaded

    suspend fun explainWord(context: Context, word: String, contextText: String): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded) return@withContext "Для работы AI необходимо скачать и загрузить модель."
        
        val prompt = AIPrompts.getWordExplanationPrompt(word, contextText)
        return@withContext getLLM(context).generate(prompt, maxTokens = 150)
    }

    suspend fun generateAnnotation(context: Context, title: String, textSnippet: String): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded) return@withContext "Для использования AI-функций скачайте модель в настройках."
        
        val prompt = AIPrompts.getBookAnnotationPrompt(title, textSnippet)
        return@withContext getLLM(context).generate(prompt, maxTokens = 250)
    }

    suspend fun summarizeChapter(context: Context, chapterText: String): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded) return@withContext "Для использования AI-функций скачайте модель в настройках."
        
        val prompt = AIPrompts.getChapterSummaryPrompt(chapterText)
        return@withContext getLLM(context).generate(prompt, maxTokens = 300)
    }

    data class SmartSearchResult(val text: String, val score: Float)

    suspend fun smartSearch(context: Context, query: String, fragments: List<String>): List<SmartSearchResult> = withContext(Dispatchers.IO) {
        if (!isModelLoaded) return@withContext emptyList()
        
        Log.i(TAG, "Smart searching for: $query")
        val searchResults = mutableListOf<SmartSearchResult>()
        val queryLower = query.lowercase(java.util.Locale.ROOT)
        
        // Filter candidates
        val candidates = fragments.filter { it.lowercase(java.util.Locale.ROOT).contains(queryLower) || it.length > 100 }.take(10)
        
        candidates.forEach { fragment ->
            val score = if (fragment.lowercase(java.util.Locale.ROOT).contains(queryLower)) 0.9f else 0.5f
            searchResults.add(SmartSearchResult(fragment, score))
        }
        
        return@withContext searchResults.sortedByDescending { it.score }.take(5)
    }


    suspend fun getQuestionAnswer(context: Context, question: String, bookContext: String): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded) return@withContext "Для использования AI-функций скачайте модель в настройках."
        
        val prompt = AIPrompts.getQuestionAnswerPrompt(question, bookContext)
        return@withContext getLLM(context).generate(prompt, maxTokens = 500)
    }

    suspend fun summarizeFullBook(context: Context, text: String): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded) return@withContext "Для использования AI-функций скачайте модель в настройках."
        
        Log.i(TAG, "Summarizing full book...")
        val prompt = AIPrompts.getBookAnnotationPrompt("Книга", text.take(10000))
        return@withContext getLLM(context).generate(prompt, maxTokens = 500)
    }

    data class Character(val name: String, val description: String, val role: String)

    suspend fun getCharacters(context: Context, text: String): List<Character> = withContext(Dispatchers.IO) {
        if (!isModelLoaded) return@withContext emptyList()
        
        Log.i(TAG, "Getting characters...")
        val prompt = AIPrompts.getCharacterAnalysisPrompt(text.take(5000))
        val response = getLLM(context).generate(prompt, maxTokens = 800)
        
        // Simple heuristic parsing of character list
        // Expected format: Name: Description (Role)
        val characters = mutableListOf<Character>()
        val lines = response.split("\n")
        for (line in lines) {
            if (line.contains(":") && (line.contains("(") || line.length > 20)) {
                val parts = line.split(":", limit = 2)
                val name = parts[0].trim().replace(Regex("^[-*• ]+"), "")
                val rest = parts[1].trim()
                
                var role = "Второстепенный"
                var description = rest
                
                if (rest.contains("(") && rest.contains(")")) {
                    val roleStart = rest.lastIndexOf("(")
                    val roleEnd = rest.lastIndexOf(")")
                    if (roleEnd > roleStart) {
                        role = rest.substring(roleStart + 1, roleEnd)
                        description = rest.substring(0, roleStart).trim()
                    }
                }
                
                if (name.isNotEmpty() && description.isNotEmpty()) {
                    characters.add(Character(name, description, role))
                }
            }
        }
        
        if (characters.isEmpty() && response.isNotBlank()) {
            characters.add(Character("Результат анализа", response, "Список"))
        }
        
        return@withContext characters
    }

    suspend fun explainTerm(context: Context, word: String, contextText: String): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded) return@withContext "Для использования AI-функций скачайте модель в настройках."
        
        Log.i(TAG, "Explaining term: $word")
        val prompt = AIPrompts.getWordExplanationPrompt(word, contextText)
        return@withContext getLLM(context).generate(prompt, maxTokens = 300)
    }

    suspend fun generateChapterDescriptions(context: Context, chapters: List<String>): List<String> = withContext(Dispatchers.IO) {
        if (!isModelLoaded) return@withContext emptyList()
        
        Log.i(TAG, "Generating chapter descriptions for ${chapters.size} chapters")
        return@withContext chapters.mapIndexed { index, chapterText -> 
            val prompt = AIPrompts.getChapterDescriptionPrompt(index + 1, chapterText.take(2000))
            getLLM(context).generate(prompt, maxTokens = 100)
        }
    }

    suspend fun getRecommendations(context: Context, readBooks: List<com.nightread.app.data.BookEntity>, allBooks: List<com.nightread.app.data.BookEntity>): List<com.nightread.app.data.BookEntity> = withContext(Dispatchers.IO) {
        if (!isModelLoaded) return@withContext emptyList()
        
        Log.i(TAG, "Getting recommendations based on ${readBooks.size} read books")
        val history = readBooks.joinToString(", ") { it.title }
        val available = allBooks.joinToString(", ") { it.title }
        
        val prompt = AIPrompts.getRecommendationsPrompt(history, available)
        val response = getLLM(context).generate(prompt, maxTokens = 500)
        
        // Simple logic: return books that are in the response or just filter as fallback
        val readSha1s = readBooks.map { it.sha1 }.toSet()
        val candidates = allBooks.filter { it.sha1 !in readSha1s }
        
        val recommended = candidates.filter { book ->
            response.contains(book.title, ignoreCase = true)
        }
        
        return@withContext if (recommended.isNotEmpty()) recommended else candidates.take(5)
    }

    suspend fun compareTexts(context: Context, textA: String, textB: String): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded) return@withContext "Модель не загружена"
        val prompt = AIPrompts.getComparisonPrompt(textA.take(5000), textB.take(5000))
        return@withContext getLLM(context).generate(prompt, maxTokens = 600)
    }

    suspend fun generateTags(context: Context, textSnippet: String): List<String> = withContext(Dispatchers.IO) {
        if (!isModelLoaded) return@withContext emptyList()
        val prompt = AIPrompts.getTagsPrompt(textSnippet.take(5000))
        val response = getLLM(context).generate(prompt, maxTokens = 100)
        return@withContext response.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    suspend fun simplifyText(context: Context, text: String): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded) return@withContext "Модель не загружена"
        val prompt = AIPrompts.getSimplificationPrompt(text.take(3000))
        return@withContext getLLM(context).generate(prompt, maxTokens = 500)
    }

    suspend fun findQuote(context: Context, topic: String, textSnippet: String): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded) return@withContext "Модель не загружена"
        val prompt = AIPrompts.getQuoteSearchPrompt(topic, textSnippet.take(8000))
        return@withContext getLLM(context).generate(prompt, maxTokens = 300)
    }
}
