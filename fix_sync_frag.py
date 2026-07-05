import re

with open("app/src/main/java/com/nightread/app/ui/SyncFragment.kt", "r") as f:
    content = f.read()

# I will just extract everything before showStatsAndSync and append my correct version
idx = content.find("private fun showStatsAndSync()")
if idx != -1:
    content = content[:idx]
    
new_code = """private fun showStatsAndSync() {
        val context = requireContext()
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val hasInternet = networkCapabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        
        if (!hasInternet) {
            Toast.makeText(context, "Отсутствует подключение к интернету", Toast.LENGTH_SHORT).show()
            return
        }
        
        btnSyncNow.isEnabled = false
        layoutSyncProgress.visibility = View.VISIBLE
        txtSyncStatus.text = "Анализ файлов..."
        progressSync.isIndeterminate = true
        txtSyncProgressCount.text = ""

        lifecycleScope.launch {
            val stats = YandexDiskManager.calculateSyncStats(context) { status ->
                lifecycleScope.launch(Dispatchers.Main) {
                    txtSyncStatus.text = status
                }
            }
            
            if (stats == null) {
                Toast.makeText(context, "Ошибка при получении данных", Toast.LENGTH_SHORT).show()
                btnSyncNow.isEnabled = true
                layoutSyncProgress.visibility = View.GONE
                return@launch
            }
            
            progressSync.isIndeterminate = false
            
            val builder = android.app.AlertDialog.Builder(context)
            builder.setTitle("Синхронизация")
            val msg = "Книг на диске: ${stats.booksOnDisk}\\n" +
                      "Книг в библиотеке: ${stats.booksLocal}\\n" +
                      "Будет скачано: ${stats.toDownload.size} (новых)\\n" +
                      "Будет загружено: ${stats.toUpload.size} (новых)\\n" +
                      "Пропущено (дубликаты): ${stats.duplicates}"
            builder.setMessage(msg)
            builder.setPositiveButton("Начать") { _, _ ->
                executeSync(stats)
            }
            builder.setNegativeButton("Отмена") { dialog, _ ->
                btnSyncNow.isEnabled = true
                layoutSyncProgress.visibility = View.GONE
                dialog.dismiss()
            }
            builder.setCancelable(false)
            builder.show()
        }
    }

    private fun executeSync(stats: com.nightread.app.data.SyncStats) {
        val context = requireContext()
        layoutSyncProgress.visibility = View.VISIBLE
        btnSyncNow.isEnabled = false
        lifecycleScope.launch {
            val success = YandexDiskManager.executeSync(context, stats) { status, completed, total ->
                lifecycleScope.launch(Dispatchers.Main) {
                    txtSyncStatus.text = status
                    if (total > 0) {
                        progressSync.max = total
                        progressSync.progress = completed
                        txtSyncProgressCount.text = "$completed / $total"
                    } else {
                        progressSync.max = 100
                        progressSync.progress = 0
                        txtSyncProgressCount.text = ""
                    }
                }
            }
            if (success) {
                val report = "Скачано: ${stats.toDownload.size} книг\\n" +
                             "Загружено: ${stats.toUpload.size} книг\\n" +
                             "Пропущено (дубликаты): ${stats.duplicates} книг"
                android.app.AlertDialog.Builder(context)
                    .setTitle("Отчет о синхронизации")
                    .setMessage(report)
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                Toast.makeText(context, "Ошибка при синхронизации", Toast.LENGTH_SHORT).show()
            }
            btnSyncNow.isEnabled = true
            updateUi()
        }
    }
}
"""

with open("app/src/main/java/com/nightread/app/ui/SyncFragment.kt", "w") as f:
    f.write(content + new_code)
print("Done")
