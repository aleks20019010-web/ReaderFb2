package com.nightread.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.R
import com.nightread.app.data.BookmarkEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookmarkAdapter(
    private val onBookmarkClicked: (BookmarkEntity) -> Unit,
    private val onBookmarkDeleteClicked: (BookmarkEntity) -> Unit
) : RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder>() {

    private var bookmarksList: List<BookmarkEntity> = emptyList()
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    fun submitList(newList: List<BookmarkEntity>) {
        bookmarksList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bookmark, parent, false)
        return BookmarkViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        val bookmark = bookmarksList[position]
        holder.bind(bookmark)
    }

    override fun getItemCount(): Int = bookmarksList.size

    inner class BookmarkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvBookTitle: TextView = itemView.findViewById(R.id.tvBookTitle)
        private val tvPageAndDate: TextView = itemView.findViewById(R.id.tvPageAndDate)
        private val tvSnippet: TextView = itemView.findViewById(R.id.tvSnippet)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(bookmark: BookmarkEntity) {
            tvBookTitle.text = bookmark.bookTitle
            
            val dateStr = dateFormat.format(Date(bookmark.timestamp))
            tvPageAndDate.text = "Страница ${bookmark.pageIndex + 1} • $dateStr"
            
            tvSnippet.text = bookmark.snippet.trim().ifEmpty { "(Пустая страница)" }

            itemView.setOnClickListener {
                onBookmarkClicked(bookmark)
            }

            btnDelete.setOnClickListener {
                onBookmarkDeleteClicked(bookmark)
            }
        }
    }
}
