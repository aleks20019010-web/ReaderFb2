package com.nightread.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightread.app.R

class WordActionBottomSheet : BottomSheetDialogFragment() {

    private var word: String = ""
    private var contextSnippet: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DarkPurpleBottomSheetDialog)
        word = arguments?.getString(ARG_WORD) ?: ""
        contextSnippet = arguments?.getString(ARG_CONTEXT) ?: ""
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

        view.findViewById<View>(R.id.btnExplain).setOnClickListener {
            WordExplanationBottomSheet.newInstance(word, contextSnippet)
                .show(parentFragmentManager, "WordExplanation")
            dismiss()
        }

        view.findViewById<View>(R.id.btnTranslate).setOnClickListener {
            WordTranslationBottomSheet.newInstance(word, contextSnippet)
                .show(parentFragmentManager, "WordTranslation")
            dismiss()
        }

        view.findViewById<View>(R.id.btnFind).setOnClickListener {
            val readingActivity = activity as? ReadingActivity
            if (readingActivity != null) {
                readingActivity.performSmartSearch(word)
            } else {
                Toast.makeText(requireContext(), "Функция доступна только на экране чтения", Toast.LENGTH_SHORT).show()
            }
            dismiss()
        }

        view.findViewById<View>(R.id.btnCopy).setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Word", word)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Слово скопировано", Toast.LENGTH_SHORT).show()
            dismiss()
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
