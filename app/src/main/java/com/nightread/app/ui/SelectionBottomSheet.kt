package com.nightread.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightread.app.R
import com.nightread.app.data.SettingsManager

class SelectionBottomSheet : BottomSheetDialogFragment() {

    private var selectedText: String = ""
    private var onTtsListener: ((String) -> Unit)? = null

    companion object {
        fun newInstance(text: String): SelectionBottomSheet {
            val fragment = SelectionBottomSheet()
            fragment.selectedText = text
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_selection_simple, container, false)
        
        val theme = SettingsManager.getReadingTheme(requireContext())
        val (bgColor, textColor, btnColor) = when (theme) {
            "light", "beige" -> Triple("#FFFBF0", "#1A1A1A", "#E53935")
            "sepia", "sepia_contrast" -> Triple("#F4ECD8", "#5C4033", "#D32F2F")
            "dark", "contrast" -> Triple("#121212", "#E0E0E0", "#FF8A80")
            "amoled" -> Triple("#000000", "#FFFFFF", "#B388FF")
            else -> Triple("#FFFBF0", "#1A1A1A", "#E53935")
        }
        
        view.setBackgroundColor(Color.parseColor(bgColor))
        
        val tvSelected = view.findViewById<TextView>(R.id.tvSelectedText)
        tvSelected.text = selectedText
        tvSelected.setTextColor(Color.parseColor(textColor))

        val btnCopy = view.findViewById<Button>(R.id.btnCopy)
        val btnDict = view.findViewById<Button>(R.id.btnDictionary)
        val btnTts = view.findViewById<Button>(R.id.btnSpeak)

        val btnBg = ColorStateList.valueOf(Color.parseColor(btnColor))
        btnCopy.backgroundTintList = btnBg
        btnDict.backgroundTintList = btnBg
        btnTts.backgroundTintList = btnBg

        btnCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Text", selectedText)
            clipboard.setPrimaryClip(clip)
            dismiss()
        }

        btnDict.setOnClickListener {
            val act = activity as? BookReaderActivity
            act?.fetchAndShowFreeDictionary(selectedText)
            dismiss()
        }

        btnTts.setOnClickListener {
            onTtsListener?.invoke(selectedText)
            dismiss()
        }

        return view
    }

    fun setTtsListener(listener: (String) -> Unit) {
        this.onTtsListener = listener
    }
}
