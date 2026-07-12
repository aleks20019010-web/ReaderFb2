package com.nightread.app.ui

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightread.app.R

class NoteBottomSheet : BottomSheetDialogFragment() {

    private var noteId: String = ""
    private var noteText: String = ""
    private var onDismissListener: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DarkPurpleBottomSheetDialog)
        noteId = arguments?.getString(ARG_NOTE_ID) ?: ""
        noteText = arguments?.getString(ARG_NOTE_TEXT) ?: "Текст сноски отсутствует."
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
        return inflater.inflate(R.layout.dialog_note, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvNoteTitle = view.findViewById<TextView>(R.id.tvNoteTitle)
        tvNoteTitle.text = "Сноска [$noteId]"

        val tvNoteText = view.findViewById<TextView>(R.id.tvNoteText)
        tvNoteText.text = noteText
    }

    fun setOnDismissListener(listener: () -> Unit) {
        onDismissListener = listener
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.invoke()
    }

    companion object {
        private const val ARG_NOTE_ID = "arg_note_id"
        private const val ARG_NOTE_TEXT = "arg_note_text"

        fun newInstance(noteId: String, noteText: String): NoteBottomSheet {
            val fragment = NoteBottomSheet()
            fragment.arguments = Bundle().apply {
                putString(ARG_NOTE_ID, noteId)
                putString(ARG_NOTE_TEXT, noteText)
            }
            return fragment
        }
    }
}
