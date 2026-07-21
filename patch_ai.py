import re

file_path = "app/src/main/java/com/nightread/app/data/LocalAiEngine.kt"
with open(file_path, "r") as f:
    content = f.read()

# Insert LlmInference instance
content = content.replace("object LocalAiEngine {", """object LocalAiEngine {

    private var llmInference: LlmInference? = null
    
    fun initRealModel(context: Context): Boolean {
        try {
            val modelFile = java.io.File(context.filesDir, "gemma.bin")
            if (modelFile.exists()) {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(512)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
""")

# Insert real LLM call in explainWord
content = content.replace("val isEnglish = trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }", """
        if (llmInference != null) {
            try {
                val prompt = "Объясни значение слова '$trimmed' в контексте: '${contextSnippet ?: ""}'"
                val response = llmInference!!.generateResponse(prompt)
                return "### Ответ от нейросети (Gemma)\n\n$response"
            } catch (e: Exception) {
                return "Ошибка при генерации ответа: ${e.message}"
            }
        }
        
        val isEnglish = trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }""")

with open(file_path, "w") as f:
    f.write(content)
