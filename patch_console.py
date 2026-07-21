import re

file_path = "app/src/main/java/com/nightread/app/ui/LocalAiFragment.kt"
with open(file_path, "r") as f:
    content = f.read()

replacement = """
    private fun runLocalAiTest() {
        val prompt = etTestPrompt.text.toString().trim()
        if (prompt.isEmpty()) return

        btnRunTest.isEnabled = false
        layoutTestResponse.visibility = View.VISIBLE
        tvTestResponse.text = "Локальный ИИ анализирует (без интернета)..."
        
        val context = requireContext().applicationContext
        
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Delay a bit for UI
            kotlinx.coroutines.delay(400)
            
            // Generate response using real LocalAiEngine
            // For general prompts, we will use explainWord as a proxy if it's text generation
            val response = com.nightread.app.data.LocalAiEngine.explainWord(context, prompt, null)
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                tvTestResponse.text = response
                btnRunTest.isEnabled = true
            }
        }
    }
"""

start_idx = content.find("private fun runLocalAiTest()")
# The next method is usually companion object or something. We need to replace just this method.
end_idx = content.find("}", content.find("tvTestResponse.text =", start_idx)) + 1
end_idx = content.find("}", end_idx) + 1
end_idx = content.find("}", end_idx) + 1

# Let's use a simpler regex
import re
new_content = re.sub(r'private fun runLocalAiTest\(\) \{.*?\n    \}', replacement.strip(), content, flags=re.DOTALL)

with open(file_path, "w") as f:
    f.write(new_content)
