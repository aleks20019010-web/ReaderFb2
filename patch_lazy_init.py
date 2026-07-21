import re
file_path = "app/src/main/java/com/nightread/app/data/LocalAiEngine.kt"
with open(file_path, "r") as f:
    content = f.read()

lazy_init_code = """
    private fun ensureModelInitialized(context: Context) {
        if (llmInference == null) {
            val modelFile = java.io.File(context.filesDir, "gemma.bin")
            if (modelFile.exists()) {
                initRealModel(context)
            }
        }
    }
"""

if "ensureModelInitialized" not in content:
    content = content.replace("private var llmInference: LlmInference? = null", "private var llmInference: LlmInference? = null\n" + lazy_init_code)

    content = content.replace("if (llmInference != null) {", "ensureModelInitialized(context)\n        if (llmInference != null) {")
    
    with open(file_path, "w") as f:
        f.write(content)
