file_path = "app/src/main/java/com/nightread/app/data/LocalAiEngine.kt"
with open(file_path, "r") as f:
    content = f.read()

# Fix the import before package
import re
content = re.sub(r'import com.google.mediapipe.tasks.genai.llminference.LlmInference\s*package com.nightread.app.data', 
                 'package com.nightread.app.data\n\nimport com.google.mediapipe.tasks.genai.llminference.LlmInference', content)

# Fix duplicated `val isEnglish`
content = content.replace("val isEnglish = trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }\n        val isEnglish = trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }", "val isEnglish = trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }")

# The syntax error about '"' might be because the replacement in translateWord was messed up.
# Let's remove the weird duplicates.
content = content.replace('val isEnglish = trimmed.any { it in \'a\'..\'z\' || it in \'A\'..\'Z\' }val isEnglish = trimmed.any { it in \'a\'..\'z\' || it in \'A\'..\'Z\' }', 'val isEnglish = trimmed.any { it in \'a\'..\'z\' || it in \'A\'..\'Z\' }')

with open(file_path, "w") as f:
    f.write(content)
