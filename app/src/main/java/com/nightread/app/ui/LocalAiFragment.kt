package com.nightread.app.ui

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nightread.app.MainActivity
import com.nightread.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class LocalAiFragment : Fragment() {

    companion object {
        fun newInstance(): LocalAiFragment {
            return LocalAiFragment()
        }
    }

    private lateinit var btnMenu: ImageButton
    private lateinit var modelStatusValue: TextView
    private lateinit var layoutDownloadProgress: View
    private lateinit var txtDownloadStatus: TextView
    private lateinit var txtDownloadStats: TextView
    private lateinit var progressDownload: ProgressBar
    private lateinit var btnDownloadModel: Button
    private lateinit var btnUploadCustomRules: Button
    private lateinit var btnInitModel: Button
    private lateinit var etTestPrompt: EditText
    private lateinit var btnRunTest: Button
    private lateinit var layoutTestResponse: View
    private lateinit var pbTestProgress: ProgressBar
    private lateinit var tvTestResponse: TextView
    private lateinit var chipPhilosophy: Button
    private lateinit var chipAuthor: Button
    private lateinit var chipPlot: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_local_ai, container, false)

        btnMenu = view.findViewById(R.id.btnMenu)
        btnMenu.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        modelStatusValue = view.findViewById(R.id.modelStatusValue)
        layoutDownloadProgress = view.findViewById(R.id.layoutDownloadProgress)
        txtDownloadStatus = view.findViewById(R.id.txtDownloadStatus)
        txtDownloadStats = view.findViewById(R.id.txtDownloadStats)
        progressDownload = view.findViewById(R.id.progressDownload)
        btnDownloadModel = view.findViewById(R.id.btnDownloadModel)
        btnUploadCustomRules = view.findViewById(R.id.btnUploadCustomRules)
        btnInitModel = view.findViewById(R.id.btnInitModel)

        etTestPrompt = view.findViewById(R.id.etTestPrompt)
        btnRunTest = view.findViewById(R.id.btnRunTest)
        layoutTestResponse = view.findViewById(R.id.layoutTestResponse)
        pbTestProgress = view.findViewById(R.id.pbTestProgress)
        tvTestResponse = view.findViewById(R.id.tvTestResponse)

        chipPhilosophy = view.findViewById(R.id.chipPhilosophy)
        chipAuthor = view.findViewById(R.id.chipAuthor)
        chipPlot = view.findViewById(R.id.chipPlot)

        setupListeners()
        updateModelStatusUi()

        return view
    }

    private fun setupListeners() {
        btnDownloadModel.setOnClickListener {
            startDirectBinDownload()
        }

        btnInitModel.setOnClickListener {
            CustomToast.show(requireContext(), "Инициализация ИИ-модели (.bin)...", Toast.LENGTH_SHORT)
            val initSuccess = com.nightread.app.data.LocalAiEngine.initRealModel(requireContext())
            if (initSuccess) {
                updateModelStatusUi()
                CustomToast.show(requireContext(), "Модель .bin успешно инициализирована!", Toast.LENGTH_LONG)
            } else {
                CustomToast.show(requireContext(), "Не удалось инициализировать локальный файл модели.", Toast.LENGTH_SHORT)
            }
        }

        btnUploadCustomRules.setOnClickListener {
            showUploadRulesDialog()
        }

        btnRunTest.setOnClickListener {
            runLocalAiTest()
        }

        chipPhilosophy.setOnClickListener {
            etTestPrompt.setText("Что такое бытие?")
        }

        chipAuthor.setOnClickListener {
            etTestPrompt.setText("Каков стиль автора?")
        }

        chipPlot.setOnClickListener {
            etTestPrompt.setText("О чем сюжет книги?")
        }
    }

    private fun updateModelStatusUi() {
        val context = context ?: return
        val prefs = context.getSharedPreferences("local_ai_prefs", Context.MODE_PRIVATE)

        val modelFile = com.nightread.app.data.LlamaEngine.getModelFile(context)
        val validFileOnDisk = modelFile.exists() && modelFile.length() > 1000000
        val isLoadedInMemory = com.nightread.app.data.LlamaEngine.isModelLoaded() || com.nightread.app.data.LocalAiEngine.hasLoadedLocalModel()
        val customRulesJson = prefs.getString("custom_rules_json", null)

        btnInitModel.visibility = View.VISIBLE

        val statusSb = java.lang.StringBuilder()
        if (isLoadedInMemory) {
            statusSb.append("🟢 Локальная модель Bonsai 27B: Загружена в память (.gguf)")
            btnDownloadModel.text = "Переустановить Bonsai 27B (.gguf)"
            btnInitModel.text = "Модель инициализирована ✓"
        } else if (validFileOnDisk) {
            statusSb.append("🟡 Локальная модель Bonsai 27B: Файл найден на диске (нажмите «Инициализировать»)")
            btnDownloadModel.text = "Переустановить Bonsai 27B (.gguf)"
            btnInitModel.text = "Инициализировать модель"
        } else {
            statusSb.append("⚪ Локальная модель Bonsai 27B: Готова к скачиванию")
            btnDownloadModel.text = "Скачать ИИ-модель Bonsai 27B (.gguf)"
            btnInitModel.text = "Инициализировать модель"
        }

        if (customRulesJson != null) {
            statusSb.append("\n• Активен пользовательский словарь")
        }

        modelStatusValue.text = statusSb.toString()
    }

    private fun startDirectBinDownload() {
        btnDownloadModel.isEnabled = false
        btnUploadCustomRules.isEnabled = false
        layoutDownloadProgress.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val urlsToTry = listOf(
                "https://huggingface.co/lmstudio-community/Bonsai-27B-GGUF/resolve/main/Bonsai-27B-Q4_K_M.gguf",
                "https://huggingface.co/QuantFactory/Bonsai-27B-GGUF/resolve/main/Bonsai-27B.Q4_K_M.gguf",
                "https://huggingface.co/bartowski/Bonsai-27B-GGUF/resolve/main/Bonsai-27B-Q4_K_M.gguf"
            )
            
            var downloadedReal = false
            var downloadErrorMsg = ""

            val modelDir = java.io.File(requireContext().filesDir, "models")
            if (!modelDir.exists()) modelDir.mkdirs()
            val outputFile = java.io.File(modelDir, "bonsai-27b-q4_k_m.gguf")
            if (outputFile.exists()) {
                try { outputFile.delete() } catch (e: Exception) { e.printStackTrace() }
            }

            val client = okhttp3.OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            for (url in urlsToTry) {
                if (downloadedReal) break
                try {
                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()

                    val response = client.newCall(request).execute()

                    if (response.isSuccessful && response.body != null) {
                        val body = response.body!!
                        val fileLength = body.contentLength()
                        val input = body.byteStream()
                        val output = java.io.FileOutputStream(outputFile)

                        val data = ByteArray(16384)
                        var total: Long = 0
                        var count: Int
                        var startTime = System.currentTimeMillis()
                        var lastUpdate = startTime

                        while (input.read(data).also { count = it } != -1) {
                            total += count
                            output.write(data, 0, count)

                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 150) {
                                lastUpdate = now
                                val progress = if (fileLength > 0) (total * 100 / fileLength).toInt() else 5
                                val loadedMb = total / (1024 * 1024)
                                val totalMb = if (fileLength > 0) fileLength / (1024 * 1024) else 15800
                                val elapsedTimeSec = maxOf(1L, (now - startTime) / 1000)
                                val speedMb = loadedMb / elapsedTimeSec

                                withContext(Dispatchers.Main) {
                                    txtDownloadStatus.text = "Загрузка ИИ-модели Bonsai 27B (.gguf): $progress%"
                                    txtDownloadStats.text = "$loadedMb МБ из $totalMb МБ (~$speedMb МБ/сек)"
                                    progressDownload.progress = progress
                                }
                            }
                        }
                        output.flush()
                        output.close()
                        input.close()

                        if (outputFile.length() > 50000000) { // Valid binary > 50MB
                            downloadedReal = true
                        }
                    } else {
                        downloadErrorMsg = "HTTP ${response.code}"
                    }
                } catch (e: Exception) {
                    downloadErrorMsg = e.localizedMessage ?: "Ошибка сети"
                    e.printStackTrace()
                }
            }

            if (!downloadedReal) {
                if (outputFile.exists()) {
                    try { outputFile.delete() } catch (e: Exception) { e.printStackTrace() }
                }
            }

            val initSuccess = if (downloadedReal) {
                com.nightread.app.data.LocalAiEngine.initRealModel(requireContext())
            } else false

            withContext(Dispatchers.Main) {
                val ctx = context ?: return@withContext
                layoutDownloadProgress.visibility = View.GONE
                btnDownloadModel.isEnabled = true
                btnUploadCustomRules.isEnabled = true
                updateModelStatusUi()

                if (initSuccess) {
                    CustomToast.show(ctx, "🟢 ИИ-модель Bonsai 27B (.gguf) успешно загружена и инициализирована!", Toast.LENGTH_LONG)
                } else if (downloadedReal) {
                    CustomToast.show(ctx, "⚠️ Модель Bonsai 27B скачана, нажмите «Инициализировать модель».", Toast.LENGTH_LONG)
                } else {
                    CustomToast.show(ctx, "❌ Не удалось скачать файл модели ($downloadErrorMsg).", Toast.LENGTH_LONG)
                }
            }
        }
    }

    private fun showUploadRulesDialog() {
        val context = context ?: return
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
        builder.setTitle("Импорт кастомного словаря")
        val input = EditText(context)
        input.setHint("Вставьте словарь в формате JSON...")
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(Color.GRAY)

        val boilerplate = "{\n  \"апория\": \"трудноразрешимая проблема.\",\n  \"эвристика\": \"приемы поиска истины.\"\n}"
        input.setText(boilerplate)
        input.minLines = 6
        input.gravity = android.view.Gravity.TOP

        val padding = (16 * context.resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(context)
        val params = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(padding, padding, padding, padding)
        input.layoutParams = params
        container.addView(input)
        builder.setView(container)

        builder.setPositiveButton("Импортировать") { dialog, _ ->
            val jsonText = input.text.toString().trim()
            if (jsonText.isNotEmpty()) {
                context.getSharedPreferences("local_ai_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("custom_rules_json", jsonText)
                    .apply()

                updateModelStatusUi()
                CustomToast.show(context, "Пользовательский словарь успешно импортирован!", Toast.LENGTH_SHORT)
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Отмена") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    private fun runLocalAiTest() {
        val prompt = etTestPrompt.text.toString().trim()
        if (prompt.isEmpty()) return
        btnRunTest.isEnabled = false
        layoutTestResponse.visibility = View.VISIBLE
        tvTestResponse.text = "Локальный ИИ анализирует (без интернета)..."

        val appContext = requireContext().applicationContext

        lifecycleScope.launch(Dispatchers.IO) {
            delay(400)
            val response = com.nightread.app.data.LocalAiEngine.customAiPrompt(appContext, prompt, null, "")

            withContext(Dispatchers.Main) {
                tvTestResponse.text = response
                btnRunTest.isEnabled = true
            }
        }
    }
}
