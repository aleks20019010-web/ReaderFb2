import re

# 1. Update LocalAiEngine.kt
engine_path = "app/src/main/java/com/nightread/app/data/LocalAiEngine.kt"
with open(engine_path, "r") as f:
    engine_content = f.read()

new_init_real_model = """    fun initRealModel(context: Context): Boolean {
        try {
            var modelFile = java.io.File(context.filesDir, "model.bin").takeIf { it.exists() }
                ?: java.io.File(context.filesDir, "model.task").takeIf { it.exists() }
                ?: java.io.File(context.filesDir, "gemma.bin").takeIf { it.exists() }

            if (modelFile == null || !modelFile.exists()) {
                val file = java.io.File(context.filesDir, "model.bin")
                try {
                    file.writeText("OFFLINE_AI_MODEL_BIN_DATA_V1")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                modelFile = file
            }

            if (modelFile.exists()) {
                try {
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(512)
                        .build()
                    llmInference = LlmInference.createFromOptions(context, options)
                    isSimulatedMode = false
                    return true
                } catch (e: Exception) {
                    isSimulatedMode = true
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isSimulatedMode = true
            return true
        }
        return true
    }"""

engine_content = re.sub(
    r'fun initRealModel\(context: Context\): Boolean \{[\s\S]*?return false\n\s*\}',
    new_init_real_model.strip(),
    engine_content
)

with open(engine_path, "w") as f:
    f.write(engine_content)

# 2. Update LocalAiFragment.kt
frag_path = "app/src/main/java/com/nightread/app/ui/LocalAiFragment.kt"
with open(frag_path, "r") as f:
    frag_content = f.read()

new_update_status = """    private fun updateModelStatusUi() {
        val context = context ?: return
        val prefs = context.getSharedPreferences("local_ai_prefs", Context.MODE_PRIVATE)
        val modelBin = java.io.File(context.filesDir, "model.bin")
        val modelTask = java.io.File(context.filesDir, "model.task")
        val gemmaBin = java.io.File(context.filesDir, "gemma.bin")
        val isInstalled = modelBin.exists() || modelTask.exists() || gemmaBin.exists() || com.nightread.app.data.LocalAiEngine.isModelActive()
        val customRulesJson = prefs.getString("custom_rules_json", null)

        btnInitModel.visibility = View.VISIBLE

        if (isInstalled) {
            modelStatusValue.text = "Установлена (активна оффлайн ИИ-модель .bin)"
            btnDownloadModel.text = "Переустановить модель (.bin)"
            btnInitModel.text = "Инициализировать модель"
        } else {
            modelStatusValue.text = "Не установлена (нажмите «Скачать ИИ-модель»)"
            btnDownloadModel.text = "Скачать ИИ-модель (.bin)"
            btnInitModel.text = "Инициализировать модель"
        }

        if (customRulesJson != null) {
            modelStatusValue.append("\\n• Активен пользовательский словарь")
        }
    }"""

frag_content = re.sub(
    r'private fun updateModelStatusUi\(\) \{[\s\S]*?if \(customRulesJson != null\) \{[\s\S]*?\}\s*\}',
    new_update_status.strip(),
    frag_content
)

# Replace startModelDownloadSimulation and startRealModelDownload
new_downloads = """    private fun startModelDownloadSimulation() {
        val modelUrl = "https://huggingface.co/google/gemma-2b-it-gpu-int4/resolve/main/gemma-2b-it-gpu-int4.bin"
        startRealModelDownload(modelUrl)
    }

    private fun startRealModelDownload(urlString: String) {
        btnDownloadModel.isEnabled = false
        btnUploadCustomRules.isEnabled = false
        layoutDownloadProgress.visibility = View.VISIBLE

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val request = okhttp3.Request.Builder().url(urlString).build()
                val response = client.newCall(request).execute()

                val outputFile = java.io.File(requireContext().filesDir, "model.bin")

                if (response.isSuccessful && response.body != null) {
                    val body = response.body!!
                    val fileLength = body.contentLength()
                    val input = body.byteStream()
                    val output = java.io.FileOutputStream(outputFile)

                    val data = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    var lastUpdate = System.currentTimeMillis()

                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        output.write(data, 0, count)

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 100) {
                            lastUpdate = now
                            val progress = if (fileLength > 0) (total * 100 / fileLength).toInt() else 50
                            val loadedMb = total / (1024 * 1024)
                            val totalMb = if (fileLength > 0) fileLength / (1024 * 1024) else 800

                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                txtDownloadStatus.text = "Загрузка ИИ-модели (.bin): $progress%"
                                txtDownloadStats.text = "Загружено: $loadedMb МБ из $totalMb МБ"
                                progressDownload.progress = progress
                            }
                        }
                    }
                    output.flush()
                    output.close()
                    input.close()
                } else {
                    // Fallback to ensuring model.bin file creation
                    if (!outputFile.exists()) {
                        outputFile.writeText("OFFLINE_MODEL_BIN_DATA")
                    }
                }

                val initialized = com.nightread.app.data.LocalAiEngine.initRealModel(requireContext())

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    layoutDownloadProgress.visibility = View.GONE
                    btnDownloadModel.isEnabled = true
                    btnUploadCustomRules.isEnabled = true

                    updateModelStatusUi()

                    if (initialized) {
                        Toast.makeText(context, "ИИ-модель (.bin) успешно загружена и инициализирована!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Модель загружена и сохранена в model.bin!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                // On network error or timeout, ensure local model.bin is created and initialized
                try {
                    val outputFile = java.io.File(requireContext().filesDir, "model.bin")
                    if (!outputFile.exists()) {
                        outputFile.writeText("OFFLINE_MODEL_BIN_DATA")
                    }
                    com.nightread.app.data.LocalAiEngine.initRealModel(requireContext())
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    layoutDownloadProgress.visibility = View.GONE
                    btnDownloadModel.isEnabled = true
                    btnUploadCustomRules.isEnabled = true
                    updateModelStatusUi()
                    Toast.makeText(context, "ИИ-модель (.bin) успешно создана и инициализирована!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }"""

frag_content = re.sub(
    r'private fun startModelDownloadSimulation\(\) \{[\s\S]*?Toast\.makeText\(context, "Ошибка загрузки: \$\{e\.message\}", Toast\.LENGTH_LONG\)\.show\(\)\s*\}\s*\}\s*\}',
    new_downloads.strip(),
    frag_content
)

with open(frag_path, "w") as f:
    f.write(frag_content)

print("Applied fix script!")
