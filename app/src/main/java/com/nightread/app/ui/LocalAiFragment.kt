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
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nightread.app.MainActivity
import com.nightread.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Фрагмент управления и тестирования локального ИИ-ассистента.
 * Позволяет пользователям скачать/установить оффлайн ИИ модель, импортировать кастомные словари,
 * изучить руководство и протестировать локальный ИИ в реальном времени.
 */
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

        // Bind top bar
        btnMenu = view.findViewById(R.id.btnMenu)
        btnMenu.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        // Bind model management
        modelStatusValue = view.findViewById(R.id.modelStatusValue)
        layoutDownloadProgress = view.findViewById(R.id.layoutDownloadProgress)
        txtDownloadStatus = view.findViewById(R.id.txtDownloadStatus)
        txtDownloadStats = view.findViewById(R.id.txtDownloadStats)
        progressDownload = view.findViewById(R.id.progressDownload)
        btnDownloadModel = view.findViewById(R.id.btnDownloadModel)
        btnUploadCustomRules = view.findViewById(R.id.btnUploadCustomRules)
        btnInitModel = view.findViewById(R.id.btnInitModel)

        // Bind interactive console
        etTestPrompt = view.findViewById(R.id.etTestPrompt)
        btnRunTest = view.findViewById(R.id.btnRunTest)
        layoutTestResponse = view.findViewById(R.id.layoutTestResponse)
        pbTestProgress = view.findViewById(R.id.pbTestProgress)
        tvTestResponse = view.findViewById(R.id.tvTestResponse)

        // Bind chips
        chipPhilosophy = view.findViewById(R.id.chipPhilosophy)
        chipAuthor = view.findViewById(R.id.chipAuthor)
        chipPlot = view.findViewById(R.id.chipPlot)

        setupListeners()
        updateModelStatusUi()

        return view
    }

    private fun setupListeners() {
        btnDownloadModel.setOnClickListener {
            startModelDownloadSimulation()
        }

        
        btnInitModel.setOnClickListener {
            Toast.makeText(requireContext(), "Инициализация ИИ-модели...", Toast.LENGTH_SHORT).show()
            val initSuccess = com.nightread.app.data.LocalAiEngine.initRealModel(requireContext())
            if (initSuccess) {
                Toast.makeText(requireContext(), "Модель успешно инициализирована!", Toast.LENGTH_SHORT).show()
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
        val isInstalled = java.io.File(context.filesDir, "gemma.bin").exists()
        val customRulesJson = prefs.getString("custom_rules_json", null)

        
        if (isInstalled) {
            modelStatusValue.text = "Установлена (активна модель Llama 3.2 1B - GGUF)"
            btnDownloadModel.text = "Переустановить модель"
            btnInitModel.visibility = View.VISIBLE
        } else {
            modelStatusValue.text = "Не установлена (активен базовый офлайн-пакет)"
            btnDownloadModel.text = "Скачать Llama 3.2 1B (~800 МБ)"
            btnInitModel.visibility = View.GONE
        }
if (customRulesJson != null) {
            modelStatusValue.append("\n• Активен пользовательский словарь")
        }
    }

    
        private fun startModelDownloadSimulation() {
        val modelUrl = "https://huggingface.co/lmstudio-community/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
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
                val isTask = urlString.contains(".task")
                val filename = if (isTask) "model.task" else "model.bin"
                val outputFile = java.io.File(requireContext().filesDir, filename)
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

    private fun showUploadRulesDialog() {
        val context = context ?: return
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
        builder.setTitle("Импорт кастомного словаря")

        val input = EditText(context)
        input.setHint("Вставьте словарь в формате JSON или введите термины...")
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(Color.GRAY)
        
        // Provide clean boilerplate
        val boilerplate = "{\n  \"апория\": \"трудноразрешимая или неразрешимая проблема, связанная с противоречием.\",\n  \"эвристика\": \"совокупность логических приемов и методов облегчающих поиск истины.\"\n}"
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
        
        val context = requireContext().applicationContext
        
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Delay a bit for UI
            kotlinx.coroutines.delay(400)
            
            // Generate response using real LocalAiEngine
            // For general prompts, we will use explainWord as a proxy if it's text generation
            val response = com.nightread.app.data.LocalAiEngine.customAiPrompt(context, prompt, null, "")
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                tvTestResponse.text = response
                btnRunTest.isEnabled = true
            }
        }
    }

    private fun getCustomRule(word: String): String? {
        val context = context ?: return null
        val prefs = context.getSharedPreferences("local_ai_prefs", Context.MODE_PRIVATE)
        val customRulesJson = prefs.getString("custom_rules_json", null) ?: return null
        try {
            val target = "\"${word.lowercase(Locale.ROOT)}\""
            val index = customRulesJson.lowercase(Locale.ROOT).indexOf(target)
            if (index != -1) {
                val valStart = customRulesJson.indexOf(":", index) + 1
                if (valStart != 0) {
                    val strStart = customRulesJson.indexOf("\"", valStart) + 1
                    val strEnd = customRulesJson.indexOf("\"", strStart)
                    if (strStart != 0 && strEnd != -1) {
                        return customRulesJson.substring(strStart, strEnd)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
