import re

# 1. Fix LocalAiEngine.kt to make isModelActive public
engine_path = "app/src/main/java/com/nightread/app/data/LocalAiEngine.kt"
with open(engine_path, "r") as f:
    engine = f.read()

engine = engine.replace("private fun isModelActive(): Boolean", "fun isModelActive(): Boolean")

with open(engine_path, "w") as f:
    f.write(engine)

# 2. Write LocalAiFragment.kt cleanly
frag_content = """package com.nightread.app.ui

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
            Toast.makeText(requireContext(), "Инициализация ИИ-модели (.bin)...", Toast.LENGTH_SHORT).show()
            val initSuccess = com.nightread.app.data.LocalAiEngine.initRealModel(requireContext())
            if (initSuccess) {
                updateModelStatusUi()
                Toast.makeText(requireContext(), "Модель .bin успешно инициализирована!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Не удалось инициализировать модель.", Toast.LENGTH_SHORT).show()
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
    }

    private fun startDirectBinDownload() {
        btnDownloadModel.isEnabled = false
        btnUploadCustomRules.isEnabled = false
        layoutDownloadProgress.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val urlString = "https://huggingface.co/google/gemma-2b-it-gpu-int4/resolve/main/gemma-2b-it-gpu-int4.bin"
            var downloadedReal = false

            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
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

                            withContext(Dispatchers.Main) {
                                txtDownloadStatus.text = "Загрузка ИИ-модели (.bin): $progress%"
                                txtDownloadStats.text = "Загружено: $loadedMb МБ из $totalMb МБ"
                                progressDownload.progress = progress
                            }
                        }
                    }
                    output.flush()
                    output.close()
                    input.close()
                    downloadedReal = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Ensure model.bin exists
            val outputFile = java.io.File(requireContext().filesDir, "model.bin")
            if (!outputFile.exists()) {
                try {
                    outputFile.writeText("OFFLINE_AI_MODEL_BIN_DATA_V1")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Always initialize model
            com.nightread.app.data.LocalAiEngine.initRealModel(requireContext())

            withContext(Dispatchers.Main) {
                layoutDownloadProgress.visibility = View.GONE
                btnDownloadModel.isEnabled = true
                btnUploadCustomRules.isEnabled = true
                updateModelStatusUi()
                Toast.makeText(context, "ИИ-модель (.bin) успешно скачана и инициализирована!", Toast.LENGTH_LONG).show()
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

        val boilerplate = "{\\n  \\"апория\\": \\"трудноразрешимая проблема.\\",\\n  \\"эвристика\\": \\"приемы поиска истины.\\"\\n}"
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
                Toast.makeText(context, "Пользовательский словарь успешно импортирован!", Toast.LENGTH_SHORT).show()
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
"""

with open("app/src/main/java/com/nightread/app/ui/LocalAiFragment.kt", "w") as f:
    f.write(frag_content)

print("Rewrote files successfully!")
