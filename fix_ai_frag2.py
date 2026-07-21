import re

file_path = "app/src/main/java/com/nightread/app/ui/LocalAiFragment.kt"
with open(file_path, "r") as f:
    content = f.read()

replacement = """
                val isTask = urlString.contains(".task")
                val filename = if (isTask) "model.task" else "model.bin"
                val outputFile = java.io.File(requireContext().filesDir, filename)
"""

content = re.sub(
    r'val outputFile = java\.io\.File\(requireContext\(\)\.filesDir, "gemma\.bin"\)',
    replacement.strip(),
    content
)

with open(file_path, "w") as f:
    f.write(content)
