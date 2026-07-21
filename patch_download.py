import re

file_path = "app/src/main/java/com/nightread/app/ui/LocalAiFragment.kt"
with open(file_path, "r") as f:
    content = f.read()

replacement = """
    private fun startModelDownloadSimulation() {
        // Automatically start downloading Llama 3.2 1B
        val llamaUrl = "https://huggingface.co/lmstudio-community/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        startRealModelDownload(llamaUrl)
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
                    .build()
                    
                val request = okhttp3.Request.Builder().url(urlString).build()
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    throw Exception("Server returned HTTP ${response.code} ${response.message}")
                }
                
                val body = response.body
                if (body == null) {
                    throw Exception("Response body is null")
                }
                
                val fileLength = body.contentLength()
                val input = body.byteStream()
                val outputFile = java.io.File(requireContext().filesDir, "gemma.bin")
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
                        val progress = if (fileLength > 0) (total * 100 / fileLength).toInt() else 0
                        val loadedMb = total / (1024 * 1024)
                        val totalMb = if (fileLength > 0) fileLength / (1024 * 1024) else 0
                        
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            txtDownloadStatus.text = "Загрузка Llama 3.2 1B: $progress%"
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
                        Toast.makeText(context, "ИИ-модель Llama 3.2 1B успешно загружена и инициализирована!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Модель загружена, но произошла ошибка при инициализации MediaPipe. Убедитесь, что формат модели поддерживается.", Toast.LENGTH_LONG).show()
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

# Replace the methods
start_idx = content.find("private fun startModelDownloadSimulation()")
end_idx = content.find("private fun showUploadRulesDialog()")

if start_idx != -1 and end_idx != -1:
    content = content[:start_idx] + replacement.strip() + "\n\n    " + content[end_idx:]
    with open(file_path, "w") as f:
        f.write(content)
