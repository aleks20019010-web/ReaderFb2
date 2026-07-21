import re

file_path = "app/src/main/java/com/nightread/app/data/LocalAiEngine.kt"
with open(file_path, "r") as f:
    content = f.read()

replacement_summary = """
    fun generateSummary(context: Context, book: BookEntity): String {
        ensureModelInitialized(context)
        if (llmInference != null) {
            val sampleText = getBookSampleText(book).take(1500)
            if (sampleText.isNotBlank()) {
                try {
                    val prompt = "Сделай подробное краткое содержание книги '${book.title}' автора '${book.author}'. Если книга тебе незнакома, сделай краткое содержание на основе следующего начала текста: '$sampleText'"
                    val response = llmInference!!.generateResponse(prompt)
                    return "### Краткое содержание (Llama 3.2 1B)\\n\\n$response"
                } catch (e: Exception) {
                    // Fallback to offline heuristic
                }
            }
        }
"""

replacement_annotation = """
    fun generateAnnotation(context: Context, book: BookEntity): String {
        ensureModelInitialized(context)
        if (llmInference != null) {
            val sampleText = getBookSampleText(book).take(1000)
            if (sampleText.isNotBlank()) {
                try {
                    val prompt = "Напиши аннотацию для книги '${book.title}' автора '${book.author}'. Если книга тебе незнакома, сделай аннотацию на основе следующего отрывка: '$sampleText'"
                    val response = llmInference!!.generateResponse(prompt)
                    return "### Аннотация (Llama 3.2 1B)\\n\\n$response"
                } catch (e: Exception) {
                    // Fallback to offline heuristic
                }
            }
        }
"""

replacement_characters = """
    fun generateCharacters(context: Context, book: BookEntity): String {
        ensureModelInitialized(context)
        if (llmInference != null) {
            val sampleText = getBookSampleText(book).take(2000)
            if (sampleText.isNotBlank()) {
                try {
                    val prompt = "Перечисли главных героев книги '${book.title}' автора '${book.author}' и кратко опиши их. Если книга тебе незнакома, выдели персонажей из следующего текста и опиши их: '$sampleText'"
                    val response = llmInference!!.generateResponse(prompt)
                    return "### Персонажи (Llama 3.2 1B)\\n\\n$response"
                } catch (e: Exception) {
                    // Fallback to offline heuristic
                }
            }
        }
"""

content = content.replace("    fun generateSummary(context: Context, book: BookEntity): String {", replacement_summary.strip("\n"))
content = content.replace("    fun generateAnnotation(context: Context, book: BookEntity): String {", replacement_annotation.strip("\n"))
content = content.replace("    fun generateCharacters(context: Context, book: BookEntity): String {", replacement_characters.strip("\n"))

with open(file_path, "w") as f:
    f.write(content)
