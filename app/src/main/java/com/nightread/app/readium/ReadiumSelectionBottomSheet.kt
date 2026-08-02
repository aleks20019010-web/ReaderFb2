package com.nightread.app.readium

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightread.app.R
import org.readium.r2.shared.publication.Locator

class ReadiumSelectionBottomSheet : BottomSheetDialogFragment() {

    private var selectedText: String = ""
    private var locator: Locator? = null

    var onHighlightListener: ((Locator, Int, String) -> Unit)? = null
    var onNoteListener: ((Locator, String, String) -> Unit)? = null
    var onDictionaryListener: ((String) -> Unit)? = null
    var onTtsListener: ((String) -> Unit)? = null

    override fun getTheme(): Int = R.style.DarkPurpleBottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_selection_actions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvSnippet = view.findViewById<TextView>(R.id.tvSelectionSnippet)
        tvSnippet?.text = selectedText

        val btnYellow = view.findViewById<View>(R.id.btnColorYellow)
        val btnGreen = view.findViewById<View>(R.id.btnColorGreen)
        val btnBlue = view.findViewById<View>(R.id.btnColorBlue)
        val btnPink = view.findViewById<View>(R.id.btnColorPink)

        btnYellow?.setOnClickListener { applyHighlight(0xFFFFEE58.toInt()) }
        btnGreen?.setOnClickListener { applyHighlight(0xFF81C784.toInt()) }
        btnBlue?.setOnClickListener { applyHighlight(0xFF64B5F6.toInt()) }
        btnPink?.setOnClickListener { applyHighlight(0xFFF06292.toInt()) }

        val btnAddNote = view.findViewById<View>(R.id.btnAddNote)
        btnAddNote?.setOnClickListener {
            showAddNoteDialog()
        }

        val btnDictionary = view.findViewById<View>(R.id.btnDictionaryLookup)
        btnDictionary?.setOnClickListener {
            onDictionaryListener?.invoke(selectedText)
            dismiss()
        }

        val btnSpeak = view.findViewById<View>(R.id.btnSpeakText)
        btnSpeak?.setOnClickListener {
            onTtsListener?.invoke(selectedText)
            dismiss()
        }

        val btnCopy = view.findViewById<View>(R.id.btnCopyText)
        btnCopy?.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Selected Text", selectedText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Текст скопирован", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    private fun applyHighlight(colorInt: Int) {
        val loc = locator ?: return
        onHighlightListener?.invoke(loc, colorInt, selectedText)
        Toast.makeText(requireContext(), "Цитата сохранена", Toast.LENGTH_SHORT).show()
        dismiss()
    }

    private fun showAddNoteDialog() {
        val loc = locator ?: return
        val etNote = EditText(requireContext()).apply {
            hint = "Введите текст заметки..."
            setPadding(32, 32, 32, 32)
        }

        AlertDialog.Builder(requireContext(), R.style.DarkPurpleBottomSheetDialog)
            .setTitle("Добавить заметку")
            .setView(etNote)
            .setPositiveButton("Сохранить") { _, _ ->
                val noteText = etNote.text.toString().trim()
                if (noteText.isNotEmpty()) {
                    onNoteListener?.invoke(loc, selectedText, noteText)
                    Toast.makeText(requireContext(), "Заметка сохранена", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    companion object {
        fun newInstance(text: String, loc: Locator): ReadiumSelectionBottomSheet {
            return ReadiumSelectionBottomSheet().apply {
                selectedText = text
                locator = loc
            }
        }
    }
}
