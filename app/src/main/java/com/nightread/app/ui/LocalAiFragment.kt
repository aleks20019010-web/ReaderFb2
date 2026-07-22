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
import com.nightread.app.data.CotypeModelManager
import com.nightread.app.data.LlamaEngine
import com.nightread.app.data.LocalAiEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private lateinit var progressDownload: ProgressBar
    private lateinit var btnCancelDownload: Button
    private lateinit var btnRetryDownload: Button
    private lateinit var layoutMemoryLoad: View
    private lateinit var btnDownloadModel: Button
    private lateinit var btnInitModel: Button
    private lateinit var btnUploadCustomRules: Button
    private lateinit var etTestPrompt: EditText
    private lateinit var btnRunTest: Button
    private lateinit var layoutTestResponse: View
    private lateinit var pbTestProgress: ProgressBar
    private lateinit var tvTestResponse: TextView
    private lateinit var chipPhilosophy: Button
    private lateinit var chipAuthor: Button
    private lateinit var chipPlot: Button

    private lateinit var btnUnloadModel: Button
    private lateinit var btnClearCache: Button
    private lateinit var btnDeleteModel: Button

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
        progressDownload = view.findViewById(R.id.progressDownload)
        btnCancelDownload = view.findViewById(R.id.btnCancelDownload)
        btnRetryDownload = view.findViewById(R.id.btnRetryDownload)

        layoutMemoryLoad = view.findViewById(R.id.layoutMemoryLoad)
        btnDownloadModel = view.findViewById(R.id.btnDownloadModel)
        btnInitModel = view.findViewById(R.id.btnInitModel)
        btnUnloadModel = view.findViewById(R.id.btnUnloadModel)
        btnUploadCustomRules = view.findViewById(R.id.btnUploadCustomRules)
        btnClearCache = view.findViewById(R.id.btnClearCache)
        btnDeleteModel = view.findViewById(R.id.btnDeleteModel)

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
            startModelDownload()
        }

        btnCancelDownload.setOnClickListener {
            CotypeModelManager.cancelDownload()
            layoutDownloadProgress.visibility = View.GONE
            btnDownloadModel.visibility = View.VISIBLE
            updateModelStatusUi()
        }

        btnRetryDownload.setOnClickListener {
            startModelDownload()
        }

        btnInitModel.setOnClickListener {
            loadModelIntoMemory()
        }

        btnUnloadModel.setOnClickListener {
            LocalAiEngine.unloadFromMemory()
            updateModelStatusUi()
            CustomToast.show(requireContext(), "Модель выгружена из ОЗУ", Toast.LENGTH_SHORT)
        }

        btnClearCache.setOnClickListener {
            val context = context ?: return@setOnClickListener
            LocalAiEngine.clearCache(context)
            CustomToast.show(context, "Кэш ответов очищен", Toast.LENGTH_SHORT)
        }

        btnDeleteModel.setOnClickListener {
            val context = context ?: return@setOnClickListener
            com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Удалить модель?")
                .setMessage("Файл модели Vikhr 0.5B (~400 МБ) будет удален с устройства. Доступное место освободится.")
                .setPositiveButton("Удалить") { dialog, _ ->
                    LocalAiEngine.unloadFromMemory()
                    val success = CotypeModelManager.deleteModel(context)
                    if (success) {
                        CustomToast.show(context, "Модель удалена с устройства", Toast.LENGTH_SHORT)
                    } else {
                        CustomToast.show(context, "Не удалось удалить файл модели", Toast.LENGTH_SHORT)
                    }
                    updateModelStatusUi()
                    dialog.dismiss()
                }
                .setNegativeButton("Отмена") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

        btnUploadCustomRules.setOnClickListener {
            showUploadRulesDialog()
        }

        btnRunTest.setOnClickListener {
            runLocalAiTest()
        }

        chipPhilosophy.setOnClickListener {
            etTestPrompt.setText("Проанализируй главного героя книги")
        }

        chipAuthor.setOnClickListener {
            etTestPrompt.setText("Составь краткое содержание книги")
        }

        chipPlot.setOnClickListener {
            etTestPrompt.setText("Объясни значение термина")
        }
    }

    private fun updateModelStatusUi() {
        val context = context ?: return
        val isDownloaded = CotypeModelManager.isModelDownloaded(context)
        val isLoaded = LlamaEngine.isLoaded()

        if (isLoaded) {
            modelStatusValue.text = "🟢 Vikhr 0.5B Instruct загружена в память и готова к работе (Офлайн)"
            btnDownloadModel.visibility = View.GONE
            btnInitModel.visibility = View.GONE
            btnUnloadModel.visibility = View.VISIBLE
            btnDeleteModel.visibility = View.VISIBLE
            layoutMemoryLoad.visibility = View.GONE
            layoutDownloadProgress.visibility = View.GONE
        } else if (isDownloaded) {
            modelStatusValue.text = "🟡 Vikhr 0.5B Instruct скачана на устройство. Нажмите «Загрузить в память»."
            btnDownloadModel.visibility = View.GONE
            btnInitModel.visibility = View.VISIBLE
            btnUnloadModel.visibility = View.GONE
            btnDeleteModel.visibility = View.VISIBLE
            layoutMemoryLoad.visibility = View.GONE
            layoutDownloadProgress.visibility = View.GONE
        } else {
            val compat = CotypeModelManager.checkDeviceCompatibility(context)
            if (compat.first) {
                modelStatusValue.text = "⚪ Vikhr 0.5B Instruct не скачана (~400 МБ). Совместимость подтверждена."
            } else {
                modelStatusValue.text = "⚠️ ${compat.second}"
            }
            btnDownloadModel.visibility = View.VISIBLE
            btnInitModel.visibility = View.GONE
            btnUnloadModel.visibility = View.GONE
            btnDeleteModel.visibility = View.GONE
            layoutMemoryLoad.visibility = View.GONE
        }
    }

    private fun startModelDownload() {
        val context = context ?: return

        btnDownloadModel.visibility = View.GONE
        layoutDownloadProgress.visibility = View.VISIBLE
        btnRetryDownload.visibility = View.GONE
        btnCancelDownload.visibility = View.VISIBLE
        progressDownload.progress = 0
        txtDownloadStatus.text = "Инициализация скачивания..."

        lifecycleScope.launch(Dispatchers.IO) {
            CotypeModelManager.downloadModel(
                context = context,
                onProgress = { percent, downloadedMb, totalMb, statusText ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        progressDownload.progress = percent
                        txtDownloadStatus.text = statusText
                    }
                },
                onSuccess = { file ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        CustomToast.show(requireContext(), "Модель Vikhr 0.5B Instruct успешно скачана!", Toast.LENGTH_SHORT)
                        updateModelStatusUi()
                        loadModelIntoMemory()
                    }
                },
                onError = { errorMessage ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        txtDownloadStatus.text = "Ошибка: $errorMessage"
                        btnRetryDownload.visibility = View.VISIBLE
                        btnCancelDownload.visibility = View.VISIBLE
                        CustomToast.show(requireContext(), errorMessage, Toast.LENGTH_LONG)
                    }
                }
            )
        }
    }

    private fun loadModelIntoMemory() {
        val context = context ?: return
        btnInitModel.isEnabled = false
        layoutMemoryLoad.visibility = View.VISIBLE

        val availableRam = LocalAiEngine.getAvailableRamInGB(context)
        if (availableRam < 1.0) {
            CustomToast.show(context, "Свободно ${String.format("%.1f", availableRam)} ГБ ОЗУ. Закройте фоновые приложения для стабильной работы.", Toast.LENGTH_LONG)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val modelFile = CotypeModelManager.getModelFile(context)
            val fileExists = modelFile.exists()
            val fileLength = if (fileExists) modelFile.length() else 0L

            val success = LocalAiEngine.initRealModel(context)

            withContext(Dispatchers.Main) {
                btnInitModel.isEnabled = true
                layoutMemoryLoad.visibility = View.GONE
                if (success) {
                    CustomToast.show(requireContext(), "Модель Vikhr 0.5B Instruct загружена в ОЗУ!", Toast.LENGTH_SHORT)
                } else {
                    val message = when {
                        !fileExists -> "Файл модели не найден. Скачайте модель перед загрузкой."
                        fileLength < 10_000_000L -> "Файл модели поврежден или недокачан (${fileLength / (1024 * 1024)} МБ). Удалите и скачайте заново."
                        else -> "Не удалось загрузить модель в ОЗУ. Проверьте свободную оперативную память."
                    }
                    CustomToast.show(requireContext(), message, Toast.LENGTH_LONG)
                }
                updateModelStatusUi()
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
                context.getSharedPreferences("cotype_ai_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("custom_rules_json", jsonText)
                    .apply()

                updateModelStatusUi()
                CustomToast.show(context, "Пользовательский словарь импортирован!", Toast.LENGTH_SHORT)
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Отмена") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    private fun runLocalAiTest() {
        if (LocalAiEngine.isProcessing) {
            CustomToast.show(requireContext(), "Модель уже работает, подождите...", Toast.LENGTH_SHORT)
            return
        }
        val prompt = etTestPrompt.text.toString().trim()
        if (prompt.isEmpty()) return
        btnRunTest.isEnabled = false
        layoutTestResponse.visibility = View.VISIBLE
        pbTestProgress.visibility = View.VISIBLE
        tvTestResponse.text = "Cotype Nano 1.5B генерирует ответ..."

        val appContext = requireContext().applicationContext

        lifecycleScope.launch(Dispatchers.IO) {
            val response = LocalAiEngine.customAiPrompt(appContext, prompt)

            withContext(Dispatchers.Main) {
                pbTestProgress.visibility = View.GONE
                tvTestResponse.text = response
                btnRunTest.isEnabled = true
            }
        }
    }
}
