import re

file_path = "app/src/main/java/com/nightread/app/data/LocalAiEngine.kt"
with open(file_path, "r") as f:
    content = f.read()

# Let's cleanly replace the explainWord method body
explain_match = re.search(r'fun explainWord\(context: Context, word: String, contextSnippet: String\?\): String \{(.*?)\n    \}', content, flags=re.DOTALL)
if explain_match:
    explain_body = """
        val trimmed = word.trim().replace(Regex("[^a-zA-Zа-яА-ЯёЁ\\\\-]"), "")
        if (trimmed.isEmpty()) return "Пожалуйста, выделите одно корректное слово."
        
        val customRule = getCustomRule(context, trimmed)
        if (customRule != null) {
            val ssb = StringBuilder()
            ssb.append("### Толкование слова (Пользовательский словарь)\\n\\n")
            ssb.append("- **Слово**: $trimmed\\n")
            ssb.append("- **Значение**: $customRule\\n\\n")
            if (!contextSnippet.isNullOrBlank()) {
                ssb.append("**Контекст употребления**:\\n")
                ssb.append("> «...${contextSnippet.trim()}...»\\n")
            }
            return ssb.toString()
        }
        
        if (llmInference != null) {
            try {
                val prompt = "Объясни значение слова '$trimmed' в контексте: '${contextSnippet ?: ""}'"
                val response = llmInference!!.generateResponse(prompt)
                return "### Ответ от нейросети (Gemma)\\n\\n$response"
            } catch (e: Exception) {
                return "Ошибка при генерации ответа: ${e.message}"
            }
        }
        
        val isEnglish = trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }
        
        if (isEnglish) {
            val translation = translateEnglishWord(trimmed)
            return "### Толкование слова (Локальный ИИ)\\n\\n" +
                    "- **Оригинал**: $trimmed\\n" +
                    "- **Перевод**: $translation\\n" +
                    "- **Особенность**: Английское слово\\n\\n" +
                    "**Описание**: Широко употребляемое понятие. " +
                    (if (!contextSnippet.isNullOrBlank()) "\\n\\n**Контекст в книге**:\\n> «...$contextSnippet...»" else "")
        } else {
            val partOfSpeech = guessRussianPartOfSpeech(trimmed)
            val definition = guessRussianDefinition(trimmed)
            
            val ssb = StringBuilder()
            ssb.append("### Толкование слова (Локальный ИИ)\\n\\n")
            ssb.append("- **Слово**: $trimmed\\n")
            ssb.append("- **Часть речи**: $partOfSpeech\\n")
            ssb.append("- **Значение**: $definition\\n\\n")
            
            if (!contextSnippet.isNullOrBlank()) {
                ssb.append("**Контекст употребления**:\\n")
                ssb.append("> «...${contextSnippet.trim()}...»\\n\\n")
                ssb.append("В данном контексте слово выражает смысловой оттенок, заложенный автором в художественную канву сюжета.")
            }
            return ssb.toString()
        }"""
    content = content[:explain_match.start(1)] + explain_body + content[explain_match.end(1):]

# Let's cleanly replace the translateWord method body
translate_match = re.search(r'fun translateWord\(context: Context, word: String, contextSnippet: String\?\): String \{(.*?)\n    \}', content, flags=re.DOTALL)
if translate_match:
    translate_body = """
        val trimmed = word.trim().replace(Regex("[^a-zA-Zа-яА-ЯёЁ\\\\-]"), "")
        if (trimmed.isEmpty()) return "Пожалуйста, выделите корректное слово."
        
        val customRule = getCustomRule(context, trimmed)
        if (customRule != null) {
            val ssb = StringBuilder()
            ssb.append("### Локальный перевод (Пользовательский словарь)\\n\\n")
            ssb.append("- **Оригинал**: $trimmed\\n")
            ssb.append("- **Перевод**: **$customRule**\\n\\n")
            if (!contextSnippet.isNullOrBlank()) {
                ssb.append("**Контекст фразы**:\\n> «...$contextSnippet...»")
            }
            return ssb.toString()
        }
        
        if (llmInference != null) {
            try {
                val prompt = "Переведи слово '$trimmed' на русский язык. Контекст: '${contextSnippet ?: ""}'"
                val response = llmInference!!.generateResponse(prompt)
                return "### Локальный перевод (Gemma)\\n\\n$response"
            } catch (e: Exception) {
                return "Ошибка при генерации перевода: ${e.message}"
            }
        }
        
        val isEnglish = trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }
        
        if (isEnglish) {
            val translation = translateEnglishWord(trimmed)
            val ssb = StringBuilder()
            ssb.append("### Локальный перевод (Английский ➔ Русский)\\n\\n")
            ssb.append("- **Английский**: $trimmed\\n")
            ssb.append("- **Русский перевод**: **$translation**\\n\\n")
            if (!contextSnippet.isNullOrBlank()) {
                ssb.append("**Контекст фразы**:\\n> «...$contextSnippet...»")
            }
            return ssb.toString()
        } else {
            val ssb = StringBuilder()
            ssb.append("### Перевод не требуется\\n\\n")
            ssb.append("Слово **«$trimmed»** уже является русским.\\n")
            if (!contextSnippet.isNullOrBlank()) {
                ssb.append("\\n**Контекст фразы**:\\n> «...$contextSnippet...»")
            }
            return ssb.toString()
        }"""
    content = content[:translate_match.start(1)] + translate_body + content[translate_match.end(1):]

with open(file_path, "w") as f:
    f.write(content)
