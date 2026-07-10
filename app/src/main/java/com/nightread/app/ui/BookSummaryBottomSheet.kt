package com.nightread.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import com.nightread.app.service.LocalAIManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookSummaryBottomSheet : BottomSheetDialogFragment() {

    private var sha1: String = ""
    private var bookText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sha1 = arguments?.getString(ARG_SHA1) ?: ""
        bookText = arguments?.getString(ARG_BOOK_TEXT) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_full_book_summary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvSummary = view.findViewById<TextView>(R.id.tvSummary)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val btnClose = view.findViewById<View>(R.id.btnClose)

        btnClose.setOnClickListener { dismiss() }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val book = withContext(Dispatchers.IO) { db.bookDao().getBookBySha1(sha1) }
            
            if (book?.summary != null) {
                tvSummary.text = book.summary
                progressBar.visibility = View.GONE
            } else {
                progressBar.visibility = View.VISIBLE
                val summary = LocalAIManager.summarizeFullBook(requireContext(), bookText.take(20000))
                tvSummary.text = summary
                progressBar.visibility = View.GONE
                
                if (book != null) {
                    withContext(Dispatchers.IO) {
                        db.bookDao().updateBook(book.copy(summary = summary))
                    }
                }
            }
        }
    }

    companion object {
        private const val ARG_SHA1 = "arg_sha1"
        private const val ARG_BOOK_TEXT = "arg_book_text"

        fun newInstance(sha1: String, bookText: String): BookSummaryBottomSheet {
            val fragment = BookSummaryBottomSheet()
            fragment.arguments = Bundle().apply {
                putString(ARG_SHA1, sha1)
                putString(ARG_BOOK_TEXT, bookText)
            }
            return fragment
        }
    }
}
