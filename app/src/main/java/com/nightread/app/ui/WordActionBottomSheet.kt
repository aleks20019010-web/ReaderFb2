package com.nightread.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightread.app.BuildConfig
import com.nightread.app.R
import com.nightread.app.data.GeminiClient
import com.nightread.app.data.GeminiContent
import com.nightread.app.data.GeminiPart
import com.nightread.app.data.GeminiRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WordActionBottomSheet : BottomSheetDialogFragment() {

    private var word: String = ""
    private var contextSnippet: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DarkPurpleBottomSheetDialog)
        word = arguments?.getString(ARG_WORD) ?: ""
        contextSnippet = arguments?.getString(ARG_CONTEXT) ?: ""
    }

    override fun onStart() {
        super.onStart()
        dialog?.applyStarryBackground()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_word_action, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvSelectedWord = view.findViewById<TextView>(R.id.tvSelectedWord)
        tvSelectedWord.text = word

        val layoutAiResponse = view.findViewById<View>(R.id.layoutAiResponse)
        val pbAiLoading = view.findViewById<ProgressBar>(R.id.pbAiLoading)
        val tvAiResponse = view.findViewById<TextView>(R.id.tvAiResponse)

        view.findViewById<View>(R.id.btnFind).setOnClickListener {
            val readingActivity = activity as? ReadingActivity
            if (readingActivity != null) {
                readingActivity.performSmartSearch(word)
            } else {
                CustomToast.show(requireContext(), "Функция доступна только на экране чтения", Toast.LENGTH_SHORT)
            }
            dismiss()
        }

        view.findViewById<View>(R.id.btnCopy).setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Word", word)
            clipboard.setPrimaryClip(clip)
            CustomToast.show(requireContext(), "Слово скопировано", Toast.LENGTH_SHORT)
            dismiss()
        }

        view.findViewById<View>(R.id.btnExplain).setOnClickListener {
            val prompt = "Вы — профессиональный литературный помощник и толковый словарь. Объясните значение слова или выражения \"$word\" в контексте чтения (если предоставлен контекст: \"$contextSnippet\"). Дайте краткое, ёмкое и понятное толкование на русском языке."
            queryGemini(prompt, layoutAiResponse, pbAiLoading, tvAiResponse)
        }

        view.findViewById<View>(R.id.btnTranslate).setOnClickListener {
            val prompt = "Вы — профессиональный переводчик. Переведите слово или выражение \"$word\" на русский язык (или на английский, если оно уже на русском). Если есть контекст: \"$contextSnippet\", переведите с его учётом. Дайте только перевод и краткие варианты перевода без лишнего текста."
            queryGemini(prompt, layoutAiResponse, pbAiLoading, tvAiResponse)
        }
    }

    private fun queryGemini(
        prompt: String,
        layoutAiResponse: View,
        pbAiLoading: ProgressBar,
        tvAiResponse: TextView
    ) {
        layoutAiResponse.visibility = View.VISIBLE
        pbAiLoading.visibility = View.VISIBLE
        tvAiResponse.text = "ИИ думает..."

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            pbAiLoading.visibility = View.GONE
            tvAiResponse.text = "Ключ API Gemini не настроен. Настройте его через панель Secrets в AI Studio, чтобы использовать ИИ-ассистента."
            return
        }

        lifecycleScope.launch {
            try {
                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(GeminiPart(text = prompt))
                        )
                    )
                )
                val response = withContext(Dispatchers.IO) {
                    GeminiClient.service.generateContent(apiKey, request)
                }
                val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

                if (isAdded) {
                    pbAiLoading.visibility = View.GONE
                    if (textResponse != null) {
                        tvAiResponse.text = textResponse.trim()
                    } else {
                        tvAiResponse.text = "Не удалось получить ответ от ИИ."
                    }
                }
            } catch (e: Exception) {
                if (isAdded) {
                    pbAiLoading.visibility = View.GONE
                    tvAiResponse.text = "Ошибка соединения: ${e.localizedMessage}"
                }
            }
        }
    }

    companion object {
        private const val ARG_WORD = "arg_word"
        private const val ARG_CONTEXT = "arg_context"

        fun newInstance(word: String, contextSnippet: String): WordActionBottomSheet {
            val fragment = WordActionBottomSheet()
            fragment.arguments = Bundle().apply {
                putString(ARG_WORD, word)
                putString(ARG_CONTEXT, contextSnippet)
            }
            return fragment
        }
    }
}
