import re

file_path = "app/src/main/java/com/nightread/app/ui/LocalAiFragment.kt"
with open(file_path, "r") as f:
    content = f.read()

# Replace startModelDownloadSimulation
new_function = """    private fun startModelDownloadSimulation() {
        val modelUrl = "https://huggingface.co/lmstudio-community/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        startRealModelDownload(modelUrl)
    }"""

content = re.sub(
    r'private fun startModelDownloadSimulation\(\) \{[\s\S]*?builder\.setNegativeButton\("Отмена"\) \{ dialog, _ ->[\s\S]*?dialog\.cancel\(\)[\s\S]*?\}[\s\S]*?builder\.show\(\)[\s\S]*?\}',
    new_function,
    content
)

with open(file_path, "w") as f:
    f.write(content)

