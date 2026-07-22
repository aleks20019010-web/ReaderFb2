package com.nightread.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightread.app.R
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
        dialog?.window?.apply {
            setDimAmount(0f)
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
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

        view.findViewById<View>(R.id.btnFind).setOnClickListener {
            val bookReaderActivity = activity as? BookReaderActivity
            if (bookReaderActivity != null) {
                bookReaderActivity.performSmartSearch(word)
            } else {
                CustomToast.show(requireContext(), "Функция доступна только на экране чтения", Toast.LENGTH_SHORT)
            }
            dismiss()
        }

        view.findViewById<View>(R.id.btnCopy).setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Word", word)
            clipboard.setPrimaryClip(clip)
            CustomToast.show(requireContext(), "Текст скопирован", Toast.LENGTH_SHORT)
            dismiss()
        }

        val btnCreateNote = view.findViewById<View>(R.id.btnCreateNote)
        val layoutNoteInput = view.findViewById<View>(R.id.layoutNoteInput)
        val etNoteText = view.findViewById<android.widget.EditText>(R.id.etNoteText)
        val btnSaveNote = view.findViewById<View>(R.id.btnSaveNote)

        btnCreateNote.setOnClickListener {
            layoutNoteInput.visibility = View.VISIBLE
            etNoteText.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(etNoteText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        btnSaveNote.setOnClickListener {
            val noteText = etNoteText.text.toString().trim()
            if (noteText.isEmpty()) {
                CustomToast.show(requireContext(), "Пожалуйста, введите текст заметки", Toast.LENGTH_SHORT)
                return@setOnClickListener
            }
            val bookReaderActivity = activity as? BookReaderActivity
            if (bookReaderActivity != null) {
                bookReaderActivity.saveNoteForBook(word, noteText)
                CustomToast.show(requireContext(), "Заметка успешно сохранена", Toast.LENGTH_SHORT)
                dismiss()
            } else {
                CustomToast.show(requireContext(), "Ошибка: экран чтения недоступен", Toast.LENGTH_SHORT)
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
