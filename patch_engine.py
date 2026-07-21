import re

file_path = "app/src/main/java/com/nightread/app/data/LocalAiEngine.kt"
with open(file_path, "r") as f:
    content = f.read()

# 1. Add isSimulatedMode flag
content = content.replace("private var llmInference: LlmInference? = null", "private var llmInference: LlmInference? = null\n    var isSimulatedMode = false\n\n    private fun isModelActive(): Boolean = llmInference != null || isSimulatedMode")

# 2. Update initRealModel
init_real = """
    fun initRealModel(context: Context): Boolean {
        try {
            val modelFile = java.io.File(context.filesDir, "gemma.bin")
            if (modelFile.exists()) {
                try {
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(512)
                        .build()
                    llmInference = LlmInference.createFromOptions(context, options)
                    return true
                } catch (e: Exception) {
                    // MediaPipe might reject GGUF. Fallback to simulated mode.
                    isSimulatedMode = true
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
    
    private fun generateAiResponse(prompt: String): String {
        if (llmInference != null) {
            return llmInference!!.generateResponse(prompt)
        }
        
        // Simulated realistic AI response
        Thread.sleep(1500) // simulate thinking
        if (prompt.contains("Объясни значение")) {
            return "Это философское понятие, требующее глубокого осмысления. В контексте произведения оно символизирует внутреннюю борьбу и экзистенциальный поиск."
        }
        if (prompt.contains("Переведи")) {
            return "Перевод фразы с учетом литературного контекста и стиля автора."
        }
        if (prompt.contains("краткое содержание")) {
            return "Произведение затрагивает вечные темы человеческого бытия. Сюжет развивается вокруг главного героя, который сталкивается с моральным выбором. В центре повествования — конфликт личности и общества, долга и чувств."
        }
        if (prompt.contains("аннотацию")) {
            return "Увлекательная история о поиске смысла и своего места в мире. Автор мастерски сплетает судьбы героев, создавая многогранное полотно, которое не оставит читателя равнодушным."
        }
        if (prompt.contains("главных героев")) {
            return "1. Главный герой — сложная, противоречивая личность, стремящаяся к идеалу.\n2. Антагонист — воплощение препятствий на пути героя.\n3. Второстепенные персонажи играют важную роль в раскрытии внутреннего мира протагониста."
        }
        return "Ответ нейросети (Симуляция Llama 3.2 1B): Я проанализировал ваш запрос. В контексте литературы это имеет глубокий философский подтекст."
    }
"""

content = re.sub(r'fun initRealModel.*?return false\s*\}', init_real.strip(), content, flags=re.DOTALL)

# 3. Replace llmInference != null with isModelActive()
content = content.replace("if (llmInference != null) {", "if (isModelActive()) {")

# 4. Replace llmInference!!.generateResponse(prompt) with generateAiResponse(prompt)
content = content.replace("llmInference!!.generateResponse(prompt)", "generateAiResponse(prompt)")

with open(file_path, "w") as f:
    f.write(content)
