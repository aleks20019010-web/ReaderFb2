import re

file_path = "app/src/main/java/com/nightread/app/data/LocalAiEngine.kt"
with open(file_path, "r") as f:
    content = f.read()

method = """
    fun customAiPrompt(context: Context, text: String, contextSnippet: String?, actionType: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return "Пожалуйста, выделите текст."
        
        if (llmInference != null) {
            try {
                val prompt = when (actionType) {
                    "explain" -> "Объясни значение следующего текста или слова в контексте книги: '$trimmed'. Контекст: '${contextSnippet ?: ""}'"
                    "translate" -> "Переведи следующий текст на русский язык: '$trimmed'. Контекст: '${contextSnippet ?: ""}'"
                    "summarize" -> "Сделай краткий пересказ следующего фрагмента текста: '$trimmed'"
                    "character" -> "Расскажи, кто такой персонаж '$trimmed', основываясь на контексте: '${contextSnippet ?: ""}'"
                    "simplify" -> "Перепиши этот текст более простыми и понятными словами: '$trimmed'"
                    else -> "Ответь на вопрос/проанализируй текст: '$trimmed'. Контекст: '${contextSnippet ?: ""}'"
                }
                
                val response = llmInference!!.generateResponse(prompt)
                
                val header = when (actionType) {
                    "explain" -> "### Толкование (Llama 3.2 1B)"
                    "translate" -> "### Перевод (Llama 3.2 1B)"
                    "summarize" -> "### Краткий пересказ (Llama 3.2 1B)"
                    "character" -> "### О персонаже (Llama 3.2 1B)"
                    "simplify" -> "### Упрощенный текст (Llama 3.2 1B)"
                    else -> "### Ответ ИИ (Llama 3.2 1B)"
                }
                
                return "$header\\n\\n$response"
            } catch (e: Exception) {
                return "Ошибка при генерации ответа: ${e.message}"
            }
        }
        
        // Fallbacks for offline (no real LLM)
        return when (actionType) {
            "explain" -> explainWord(context, text, contextSnippet)
            "translate" -> translateWord(context, text, contextSnippet)
            "summarize" -> "Для пересказа текста необходимо скачать полную ИИ-модель (Llama 3.2 1B) в разделе 'Локальный ИИ'."
            "character" -> "Для анализа персонажа необходимо скачать полную ИИ-модель (Llama 3.2 1B) в разделе 'Локальный ИИ'."
            "simplify" -> "Для упрощения текста необходимо скачать полную ИИ-модель (Llama 3.2 1B) в разделе 'Локальный ИИ'."
            else -> "Функция требует загрузки полной ИИ-модели."
        }
    }
"""

# Insert before the last bracket
last_brace = content.rfind("}")
content = content[:last_brace] + method + content[last_brace:]

with open(file_path, "w") as f:
    f.write(content)
