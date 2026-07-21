import re

file_path = "app/src/main/java/com/nightread/app/data/LocalAiEngine.kt"
with open(file_path, "r") as f:
    content = f.read()

# Replace the second occurrence of "val isEnglish = trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }"
parts = content.split("val isEnglish = trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }")
if len(parts) >= 3:
    parts[2] = """
        if (llmInference != null) {
            try {
                val prompt = "Переведи слово '$trimmed' на русский язык. Контекст: '${contextSnippet ?: ""}'"
                val response = llmInference!!.generateResponse(prompt)
                return "### Локальный перевод (Gemma)\n\n$response"
            } catch (e: Exception) {
                return "Ошибка при генерации перевода: ${e.message}"
            }
        }
        
        val isEnglish = trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }""" + parts[2]
    
    with open(file_path, "w") as f:
        f.write("val isEnglish = trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }".join(parts))
