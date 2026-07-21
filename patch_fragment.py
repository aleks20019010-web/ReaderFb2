import re

file_path = "app/src/main/java/com/nightread/app/ui/LocalAiFragment.kt"
with open(file_path, "r") as f:
    content = f.read()

replacement = """
    private fun startModelDownloadSimulation() {
        val context = context ?: return
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
        builder.setTitle("Скачивание реальной AI модели")
        
        val input = EditText(context)
        input.setHint("URL для загрузки (.bin MediaPipe файла)")
        input.setTextColor(android.graphics.Color.WHITE)
        input.setHintTextColor(android.graphics.Color.GRAY)
        // A placeholder URL that can be tested
        input.setText("https://huggingface.co/google/gemma-2b-it-gguf/resolve/main/gemma-2b-it-q4_k_m.gguf")
        builder.setView(input)
        
        builder.setPositiveButton("Скачать") { _, _ ->
            val urlString = input.text.toString()
            startRealModelDownload(urlString)
        }
        builder.setNegativeButton("Отмена") { dialog, _ -> dialog.cancel() }
        builder.show()
    }
    
    private fun startRealModelDownload(urlString: String) {
        btnDownloadModel.isEnabled = false
        btnUploadCustomRules.isEnabled = false
        layoutDownloadProgress.visibility = View.VISIBLE
        
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = java.net.URL(urlString)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connect()
                
                if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                    throw Exception("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
                }
                
                val fileLength = connection.contentLength
                val input = connection.inputStream
                val outputFile = java.io.File(requireContext().filesDir, "gemma.bin")
                val output = java.io.FileOutputStream(outputFile)
                
                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int
                
                var lastUpdate = System.currentTimeMillis()
                
                while (input.read(data).also { count = it } != -1) {
                    total += count
                    output.write(data, 0, count)
                    
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 100) {
                        lastUpdate = now
                        val progress = if (fileLength > 0) (total * 100 / fileLength).toInt() else 0
                        val loadedMb = total / (1024 * 1024)
                        val totalMb = if (fileLength > 0) fileLength / (1024 * 1024) else 0
                        
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            txtDownloadStatus.text = "Загрузка реальной модели: $progress%"
                            txtDownloadStats.text = "Загружено: $loadedMb МБ из $totalMb МБ"
                            progressDownload.progress = progress
                        }
                    }
                }
                output.flush()
                output.close()
                input.close()
                
                // Initialize real AI model
                val initialized = com.nightread.app.data.LocalAiEngine.initRealModel(requireContext())
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    layoutDownloadProgress.visibility = View.GONE
                    btnDownloadModel.isEnabled = true
                    btnUploadCustomRules.isEnabled = true
                    
                    val prefs = requireContext().getSharedPreferences("local_ai_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("is_model_downloaded", true).apply()
                    updateModelStatusUi()
                    
                    if (initialized) {
                        Toast.makeText(context, "ИИ-модель успешно загружена и инициализирована!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Модель загружена, но произошла ошибка при инициализации MediaPipe", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    layoutDownloadProgress.visibility = View.GONE
                    btnDownloadModel.isEnabled = true
                    btnUploadCustomRules.isEnabled = true
                    Toast.makeText(context, "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
"""

start_idx = content.find("private fun startModelDownloadSimulation()")
end_idx = content.find("private fun showUploadRulesDialog()")

if start_idx != -1 and end_idx != -1:
    content = content[:start_idx] + replacement + "\n    " + content[end_idx:]
    with open(file_path, "w") as f:
        f.write(content)
