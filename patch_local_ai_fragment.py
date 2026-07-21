import re

file_path = "app/src/main/java/com/nightread/app/ui/LocalAiFragment.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("private lateinit var btnUploadCustomRules: Button", "private lateinit var btnUploadCustomRules: Button\n    private lateinit var btnInitModel: Button")

content = content.replace("btnUploadCustomRules = view.findViewById(R.id.btnUploadCustomRules)", "btnUploadCustomRules = view.findViewById(R.id.btnUploadCustomRules)\n        btnInitModel = view.findViewById(R.id.btnInitModel)")

listener_patch = """
        btnInitModel.setOnClickListener {
            Toast.makeText(requireContext(), "Инициализация ИИ-модели...", Toast.LENGTH_SHORT).show()
            val initSuccess = com.nightread.app.data.LocalAiEngine.initRealModel(requireContext())
            if (initSuccess) {
                Toast.makeText(requireContext(), "Модель успешно инициализирована!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Не удалось инициализировать модель.", Toast.LENGTH_SHORT).show()
            }
        }
"""
content = content.replace("btnUploadCustomRules.setOnClickListener {", listener_patch + "\n        btnUploadCustomRules.setOnClickListener {")

ui_update = """
        if (isInstalled) {
            modelStatusValue.text = "Установлена (активна модель Llama 3.2 1B - GGUF)"
            btnDownloadModel.text = "Переустановить модель"
            btnInitModel.visibility = View.VISIBLE
        } else {
            modelStatusValue.text = "Не установлена (активен базовый офлайн-пакет)"
            btnDownloadModel.text = "Скачать Llama 3.2 1B (~800 МБ)"
            btnInitModel.visibility = View.GONE
        }
"""
content = re.sub(r'if \(isInstalled\) \{.*?(?=if \(customRulesJson != null\))', ui_update, content, flags=re.DOTALL)

with open(file_path, "w") as f:
    f.write(content)
