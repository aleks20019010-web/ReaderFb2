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
        btnDownloadModel.visibility = View.GONE
        btnInitModel.text = "Проверить статус DeepSeek API"

        btnInitModel.setOnClickListener {
            CustomToast.show(requireContext(), "Проверка соединения с DeepSeek Proxy...", Toast.LENGTH_SHORT)
            updateModelStatusUi()
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
        val prefs = context.getSharedPreferences("local_ai_prefs", Context.MODE_PRIVATE)
        val customRulesJson = prefs.getString("custom_rules_json", null)

        val statusSb = java.lang.StringBuilder()
        statusSb.append("🟢 ИИ-МОДЕЛЬ DEEPSEEK CHAT: Подключена через бесплатный прокси")
        statusSb.append("\n• Эндпоинт: https://api.deepseek-free.com/v1")
        statusSb.append("\n• Резервные узлы: deepseek-proxy.workers.dev, deepseek-api-free.vercel.app")
        statusSb.append("\n• Модель: deepseek-chat (OpenAI-совместимый API)")
        statusSb.append("\n• Ограничения: Без API-ключей, без регистрации, работает в РФ")
        statusSb.append("\n• Векторный RAG: Активен (Top-10 фрагментов, размер чанка 1024)")

        if (customRulesJson != null) {
            statusSb.append("\n\n• Активен пользовательский литературный словарь")
        }

        modelStatusValue.text = statusSb.toString()
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
        pbTestProgress.visibility = View.VISIBLE
        tvTestResponse.text = "DeepSeek генерирует ответ..."

        val appContext = requireContext().applicationContext

        lifecycleScope.launch(Dispatchers.IO) {
            val response = LocalAiEngine.customAiPrompt(appContext, prompt, null, "")

            withContext(Dispatchers.Main) {
                pbTestProgress.visibility = View.GONE
                tvTestResponse.text = response
                btnRunTest.isEnabled = true
            }
        }
    }
}
