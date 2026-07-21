import re

file_path = "app/src/main/java/com/nightread/app/data/LocalAiEngine.kt"
with open(file_path, "r") as f:
    content = f.read()

replacement = """
    private fun generateAiResponse(prompt: String): String {
        if (llmInference != null) {
            try {
                return llmInference?.generateResponse(prompt) ?: ""
            } catch (e: Exception) {
                e.printStackTrace()
                return "Ошибка инференса: ${e.message}"
            }
        }
        
        // Simulated realistic AI response
"""

content = re.sub(
    r'private fun generateAiResponse\(prompt: String\): String \{[\s\S]*?// Simulated realistic AI response',
    replacement.strip(),
    content
)

with open(file_path, "w") as f:
    f.write(content)
