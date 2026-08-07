package com.nightread.app.ui

import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.R
import com.nightread.app.data.BookmarkEntity
import com.nightread.app.databinding.ItemBookmarkBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Адаптер для отображения списка закладок с использованием ListAdapter и DiffUtil.
 * 
 * @param onBookmarkClicked Колбэк при клике на закладку
 * @param onBookmarkDeleteClicked Колбэк при удалении закладки
 */
class BookmarkAdapter(
    private val onBookmarkClicked: (BookmarkEntity) -> Unit,
    private val onBookmarkDeleteClicked: (BookmarkEntity) -> Unit
) : ListAdapter<BookmarkEntity, BookmarkAdapter.BookmarkViewHolder>(
    BookmarkDiffCallback
) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val binding = ItemBookmarkBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BookmarkViewHolder(binding, onBookmarkClicked, onBookmarkDeleteClicked)
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder для отображения одной закладки.
     */
    class BookmarkViewHolder(
        private val binding: ItemBookmarkBinding,
        private val onBookmarkClicked: (BookmarkEntity) -> Unit,
        private val onBookmarkDeleteClicked: (BookmarkEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentBookmark: BookmarkEntity? = null

        init {
            binding.root.setOnClickListener {
                currentBookmark?.let(onBookmarkClicked)
            }

            binding.btnDelete.setOnClickListener {
                currentBookmark?.let(onBookmarkDeleteClicked)
            }
        }

        fun bind(bookmark: BookmarkEntity) {
            currentBookmark = bookmark
            
            with(binding) {
                tvBookTitle.text = bookmark.bookTitle
                
                val dateStr = DateFormatter.format(bookmark.timestamp)
                tvPageAndDate.text = root.context.getString(
                    R.string.bookmark_page_date,
                    bookmark.pageIndex + 1,
                    dateStr
                )
                
                tvSnippet.text = bookmark.snippet.trim().ifEmpty {
                    root.context.getString(R.string.bookmark_empty_snippet)
                }
            }
        }
    }

    /**
     * DiffCallback для эффективного обновления списка.
     */
    private object BookmarkDiffCallback : DiffUtil.ItemCallback<BookmarkEntity>() {
        override fun areItemsTheSame(oldItem: BookmarkEntity, newItem: BookmarkEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: BookmarkEntity, newItem: BookmarkEntity): Boolean =
            oldItem == newItem
    }
}

/**
 * Утилита для форматирования дат в закладках.
 * Потокобезопасна для всех версий Android.
 */
private object DateFormatter {
    private const val DATE_FORMAT_PATTERN = "dd.MM.yyyy HH:mm"
    private const val MODERN_API_LEVEL = Build.VERSION_CODES.O
    
    private val isModernApi = Build.VERSION.SDK_INT >= MODERN_API_LEVEL
    
    // Ленивая инициализация для современных API (создаётся только при первом использовании)
    private val modernFormatter: DateTimeFormatter? by lazy {
        if (isModernApi) {
            DateTimeFormatter
                .ofPattern(DATE_FORMAT_PATTERN)
                .withLocale(Locale.getDefault())
        } else {
            null
        }
    }

    /**
     * Форматирует timestamp в строку.
     * Для Android 8+ использует DateTimeFormatter (потокобезопасный).
     * Для старых версий создаёт новый SimpleDateFormat (только для старых API).
     */
    fun format(timestamp: Long): String {
        return if (isModernApi) {
            // Используем потокобезопасный DateTimeFormatter
            modernFormatter!!.format(
                Instant.ofEpochMilli(timestamp)
                    .atZone(ZoneId.systemDefault())
            )
        } else {
            // Для старых API создаём новый экземпляр каждый раз
            // Это потокобезопасно, т.к. экземпляр не используется повторно
            @Suppress("DEPRECATION")
            java.text.SimpleDateFormat(
                DATE_FORMAT_PATTERN,
                Locale.getDefault()
            ).format(java.util.Date(timestamp))
        }
    }
}
