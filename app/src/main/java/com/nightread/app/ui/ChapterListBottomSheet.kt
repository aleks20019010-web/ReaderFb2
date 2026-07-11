package com.nightread.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class ChapterListBottomSheet : BottomSheetDialogFragment() {

    private var sha1: String = ""
    private var bookContent: String = ""
    private var onChapterClick: ((Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sha1 = arguments?.getString(ARG_SHA1) ?: ""
        bookContent = arguments?.getString(ARG_BOOK_CONTENT) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_chapter_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvChapters = view.findViewById<RecyclerView>(R.id.rvChapters)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val btnClose = view.findViewById<View>(R.id.btnClose)

        rvChapters.layoutManager = LinearLayoutManager(context)
        btnClose.setOnClickListener { dismiss() }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val book = withContext(Dispatchers.IO) { db.bookDao().getBookBySha1(sha1) }
            
            // Find chapters (simple simulation: split by \n\n\n or \u000C)
            val chapterOffsets = mutableListOf<Int>()
            val chapterTexts = mutableListOf<String>()
            
            var lastPos = 0
            val markers = listOf("\n\n\n", "\u000C")
            
            for (marker in markers) {
                var pos = bookContent.indexOf(marker)
                while (pos != -1) {
                    if (pos > lastPos + 500) { // Min chapter length
                        chapterOffsets.add(lastPos)
                        chapterTexts.add(bookContent.substring(lastPos, pos).take(5000))
                        lastPos = pos + marker.length
                    }
                    pos = bookContent.indexOf(marker, pos + marker.length)
                }
            }
            // Add last chapter
            if (lastPos < bookContent.length) {
                chapterOffsets.add(lastPos)
                chapterTexts.add(bookContent.substring(lastPos).take(5000))
            }

            rvChapters.adapter = ChapterAdapter(chapterOffsets, emptyList()) { offset ->
                onChapterClick?.invoke(offset)
                dismiss()
            }
            progressBar.visibility = View.GONE
        }
    }

    fun setOnChapterClickListener(listener: (Int) -> Unit) {
        onChapterClick = listener
    }

    private fun parseDescriptions(json: String): List<String> {
        return emptyList()
    }

    private fun serializeDescriptions(list: List<String>): String {
        return ""
    }

    private class ChapterAdapter(
        private val offsets: List<Int>,
        private val descriptions: List<String>,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<ChapterAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvChapterTitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chapter, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val offset = offsets[position]
            holder.tvTitle.text = "Глава ${position + 1}"
            holder.itemView.setOnClickListener { onClick(offset) }
        }

        override fun getItemCount(): Int = offsets.size
    }

    companion object {
        private const val ARG_SHA1 = "arg_sha1"
        private const val ARG_BOOK_CONTENT = "arg_book_content"

        fun newInstance(sha1: String, bookContent: String): ChapterListBottomSheet {
            val fragment = ChapterListBottomSheet()
            fragment.arguments = Bundle().apply {
                putString(ARG_SHA1, sha1)
                putString(ARG_BOOK_CONTENT, bookContent)
            }
            return fragment
        }
    }
}
