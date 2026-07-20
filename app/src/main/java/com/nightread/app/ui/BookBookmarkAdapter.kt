package com.nightread.app.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.R
import com.nightread.app.data.BookmarkEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookBookmarkAdapter(
    private val themeKey: String,
    private val onBookmarkClicked: (BookmarkEntity) -> Unit,
    private val onBookmarkDeleteClicked: (BookmarkEntity) -> Unit,
    private val onBookmarkLongClicked: (BookmarkEntity) -> Unit = {},
    private val onBookmarkEditClicked: (BookmarkEntity) -> Unit = {},
    private val onBookmarkShareClicked: (BookmarkEntity) -> Unit = {}
) : RecyclerView.Adapter<BookBookmarkAdapter.ViewHolder>() {

    private var list: List<BookmarkEntity> = emptyList()
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    fun submitList(newList: List<BookmarkEntity>) {
        list = newList
        notifyDataSetChanged()
    }

    fun getBookmarkAt(position: Int): BookmarkEntity? {
        return list.getOrNull(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_book_bookmark, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int = list.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        private val tvPageNumber: TextView = itemView.findViewById(R.id.tvPageNumber)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val btnShare: ImageButton = itemView.findViewById(R.id.btnShare)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
        private val tvSnippet: TextView = itemView.findViewById(R.id.tvSnippet)

        fun bind(bookmark: BookmarkEntity) {
            tvPageNumber.text = "Страница ${bookmark.pageIndex + 1}"
            
            val dateStr = dateFormat.format(Date(bookmark.timestamp))
            tvTimestamp.text = dateStr
            
            tvSnippet.text = bookmark.snippet.trim().ifEmpty { "(Пустая страница)" }

            // Theme colors mapping
            val cardBgHex: String
            val itemBgHex: String
            val accentHex: String
            val textPrimaryHex: String
            val textSecondaryHex: String

            when (themeKey) {
                "light", "beige" -> {
                    cardBgHex = "#FAF6F0"
                    itemBgHex = "#EFE9E2"
                    accentHex = "#D35400"
                    textPrimaryHex = "#2C3E50"
                    textSecondaryHex = "#7F8C8D"
                }
                "sepia", "sepia_contrast" -> {
                    cardBgHex = "#F4ECD8"
                    itemBgHex = "#EADCB9"
                    accentHex = "#8E44AD"
                    textPrimaryHex = "#5B3A29"
                    textSecondaryHex = "#8F7365"
                }
                "contrast" -> {
                    cardBgHex = "#000000"
                    itemBgHex = "#1A1A1A"
                    accentHex = "#FFFFFF"
                    textPrimaryHex = "#FFFFFF"
                    textSecondaryHex = "#AAAAAA"
                }
                else -> { // "dark"
                    cardBgHex = "#1A0D2A"
                    itemBgHex = "#2A1A3E"
                    accentHex = "#9B59B6"
                    textPrimaryHex = "#E8D8F0"
                    textSecondaryHex = "#B8A0C8"
                }
            }

            val itemBgColor = Color.parseColor(itemBgHex)
            val accentColor = Color.parseColor(accentHex)
            val textPrimaryColor = Color.parseColor(textPrimaryHex)
            val textSecondaryColor = Color.parseColor(textSecondaryHex)

            // Stylize the item container with appropriate colors
            val containerBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(itemBgColor)
            }
            itemView.background = containerBg

            // Apply text colors
            tvPageNumber.setTextColor(textPrimaryColor)
            tvTimestamp.setTextColor(textSecondaryColor)
            tvSnippet.setTextColor(textSecondaryColor)

            // Styled background for snippet
            val snippetBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f
                setColor(Color.parseColor(when (themeKey) {
                    "light", "beige" -> "#0D000000"
                    "sepia", "sepia_contrast" -> "#0A000000"
                    "contrast" -> "#22ffffff"
                    else -> "#15ffffff"
                }))
            }
            tvSnippet.background = snippetBg

            // Tint icons
            ivIcon.imageTintList = ColorStateList.valueOf(accentColor)
            btnShare.imageTintList = ColorStateList.valueOf(if (themeKey == "contrast") Color.parseColor("#FFFFFF") else Color.parseColor("#3498DB"))
            btnEdit.imageTintList = ColorStateList.valueOf(if (themeKey == "contrast") Color.parseColor("#FFFFFF") else Color.parseColor("#F1C40F"))
            btnDelete.imageTintList = ColorStateList.valueOf(if (themeKey == "contrast") Color.parseColor("#FFFFFF") else Color.parseColor("#E74C3C"))

            itemView.setOnClickListener {
                onBookmarkClicked(bookmark)
            }

            itemView.setOnLongClickListener {
                onBookmarkLongClicked(bookmark)
                true
            }

            btnShare.setOnClickListener {
                onBookmarkShareClicked(bookmark)
            }

            btnEdit.setOnClickListener {
                onBookmarkEditClicked(bookmark)
            }

            btnDelete.setOnClickListener {
                onBookmarkDeleteClicked(bookmark)
            }
        }
    }
}
