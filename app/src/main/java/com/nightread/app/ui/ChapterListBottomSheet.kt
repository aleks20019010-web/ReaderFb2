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
        setStyle(STYLE_NORMAL, R.style.DarkPurpleBottomSheetDialog)
        sha1 = arguments?.getString(ARG_SHA1) ?: ""
        bookContent = arguments?.getString(ARG_BOOK_CONTENT) ?: ""
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
            val chapters = withContext(Dispatchers.Default) {
                extractChapters(bookContent)
            }

            rvChapters.adapter = ChapterAdapter(chapters) { offset ->
                onChapterClick?.invoke(offset)
                dismiss()
            }
            progressBar.visibility = View.GONE
        }
    }

    fun setOnChapterClickListener(listener: (Int) -> Unit) {
        onChapterClick = listener
    }

    data class ChapterItem(val title: String, val offset: Int)

    private fun extractChapters(content: String): List<ChapterItem> {
        if (content.isEmpty()) return emptyList()

        val chapters = mutableListOf<ChapterItem>()

        // 1. Try finding [CHAPTER]...[/CHAPTER] tags
        val chapterTagRegex = Regex("""\[CHAPTER\](.*?)\[/CHAPTER\]""", RegexOption.DOT_MATCHES_ALL)
        val tagMatches = chapterTagRegex.findAll(content).toList()
        if (tagMatches.isNotEmpty()) {
            for (match in tagMatches) {
                val rawTitle = match.groupValues[1].replace("\n", " ").trim()
                val cleanTitle = if (rawTitle.isNotEmpty()) rawTitle.take(80) else "Глава ${chapters.size + 1}"
                chapters.add(ChapterItem(cleanTitle, match.range.first))
            }
            return chapters
        }

        // 2. Try finding explicit headings using regex: Глава N, Chapter N, Пролог, Эпилог, Часть N, etc.
        val headingRegex = Regex("""(?m)^\s*(Глава\s+\d+.*|Chapter\s+\d+.*|Пролог.*|Эпилог.*|Часть\s+\d+.*|Книга\s+\d+.*|\b\d+\.\s+[A-ZА-ЯЁ].*)""")
        val headingMatches = headingRegex.findAll(content).toList()

        var lastOffset = -1000
        for (match in headingMatches) {
            val offset = match.range.first
            if (offset - lastOffset >= 200) { // minimum length between chapters
                val rawTitle = match.value.trim()
                val cleanTitle = rawTitle.take(80)
                chapters.add(ChapterItem(cleanTitle, offset))
                lastOffset = offset
            }
        }

        if (chapters.isNotEmpty()) {
            return chapters
        }

        // 3. Fallback: split by \u000C or \n\n\n
        val markers = listOf("\u000C", "\n\n\n")
        var lastPos = 0
        for (marker in markers) {
            var pos = content.indexOf(marker)
            while (pos != -1) {
                if (pos > lastPos + 400) {
                    val nextChunk = content.substring(lastPos, pos).trim()
                    val firstLine = nextChunk.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
                    val title = if (firstLine.isNotEmpty() && firstLine.length <= 60) firstLine else "Глава ${chapters.size + 1}"
                    chapters.add(ChapterItem(title, lastPos))
                    lastPos = pos + marker.length
                }
                pos = content.indexOf(marker, pos + marker.length)
            }
            if (chapters.isNotEmpty()) break
        }

        if (lastPos < content.length && chapters.isNotEmpty()) {
            val nextChunk = content.substring(lastPos).trim()
            val firstLine = nextChunk.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
            val title = if (firstLine.isNotEmpty() && firstLine.length <= 60) firstLine else "Глава ${chapters.size + 1}"
            chapters.add(ChapterItem(title, lastPos))
        }

        // Fallback: if no chapters detected at all, return single chapter
        if (chapters.isEmpty()) {
            chapters.add(ChapterItem("Начало книги", 0))
        }

        return chapters
    }

    private class ChapterAdapter(
        private val items: List<ChapterItem>,
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
            val item = items[position]
            holder.tvTitle.text = item.title
            holder.itemView.setOnClickListener { onClick(item.offset) }
        }

        override fun getItemCount(): Int = items.size
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
