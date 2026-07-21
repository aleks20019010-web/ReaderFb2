file_path = "app/src/main/java/com/nightread/app/data/LocalAiEngine.kt"
with open(file_path, "r") as f:
    content = f.read()

# Fix multiline string error
content = content.replace(
    'return "1. Главный герой — сложная, противоречивая личность, стремящаяся к идеалу.\n2. Антагонист — воплощение препятствий на пути героя.\n3. Второстепенные персонажи играют важную роль в раскрытии внутреннего мира протагониста."',
    'return "1. Главный герой — сложная, противоречивая личность, стремящаяся к идеалу.\\n2. Антагонист — воплощение препятствий на пути героя.\\n3. Второстепенные персонажи играют важную роль в раскрытии внутреннего мира протагониста."'
)

# Fix ensureModelInitialized
content = content.replace("if (llmInference == null) {", "if (llmInference == null && !isSimulatedMode) {")

with open(file_path, "w") as f:
    f.write(content)
