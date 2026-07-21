import re

file_path = "app/src/main/java/com/nightread/app/ui/LocalAiFragment.kt"
with open(file_path, "r") as f:
    content = f.read()

replacement = """
    private fun startModelDownloadSimulation() {
        val builder = android.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Скачать модель (.bin / .task)")
        
        val input = android.widget.EditText(requireContext())
        input.hint = "https://.../model.task"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        builder.setView(input)
        
        builder.setPositiveButton("Скачать") { dialog, _ ->
            val url = input.text.toString().trim()
            if (url.isNotEmpty() && (url.endsWith(".bin", true) || url.endsWith(".task", true) || url.contains(".bin") || url.contains(".task"))) {
                startRealModelDownload(url)
            } else {
                android.widget.Toast.makeText(requireContext(), "Укажите корректную ссылку на файл .bin или .task", android.widget.Toast.LENGTH_LONG).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Отмена") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }
"""

content = re.sub(
    r'private fun startModelDownloadSimulation\(\) \{[\s\S]*?startRealModelDownload\(llamaUrl\)\s*\}',
    replacement.strip(),
    content
)

with open(file_path, "w") as f:
    f.write(content)
