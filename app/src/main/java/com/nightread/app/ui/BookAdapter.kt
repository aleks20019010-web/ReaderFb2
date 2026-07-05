package com.nightread.app.ui

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.R
import com.nightread.app.data.BookEntity
import java.io.File
import androidx.recyclerview.widget.DiffUtil


class BookAdapter(
    private var books: List<BookEntity>,
    private val onOpenBook: (BookEntity) -> Unit,
    private val onDeleteBook: ((BookEntity) -> Unit)? = null
) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {

    companion object {
        private const val VIEW_TYPE_GRID = 0
        private const val VIEW_TYPE_LIST = 1
    }

    private var isGridView: Boolean = true

    override fun getItemViewType(position: Int): Int {
        return if (isGridView) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val layoutRes = if (viewType == VIEW_TYPE_GRID) {
            R.layout.item_book_minimalist
        } else {
            R.layout.item_book_detailed_list
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return BookViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = books[position]
        holder.bind(book, onOpenBook, onDeleteBook)
        
        // Staggered cascade animation for a premium Material 3 entry effect
        holder.itemView.alpha = 0f
        holder.itemView.translationY = 32f * holder.itemView.resources.displayMetrics.density
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setStartDelay((position % 12).toLong() * 30) // Cap the delay to keep interactions snappy
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    override fun getItemCount(): Int = books.size

    fun updateData(newBooks: List<BookEntity>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize() = books.size
            override fun getNewListSize() = newBooks.size
            
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return books[oldItemPosition].sha1 == newBooks[newItemPosition].sha1
            }
            
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = books[oldItemPosition]
                val new = newBooks[newItemPosition]
                return old.title == new.title &&
                       old.currentProgressChar == new.currentProgressChar &&
                       old.coverPath == new.coverPath
            }
        }
        
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.books = newBooks
        diffResult.dispatchUpdatesTo(this)
    }

    fun getBookAt(position: Int): BookEntity {
        return books[position]
    }

    fun getPositionOfBook(book: BookEntity): Int {
        return books.indexOfFirst { it.sha1 == book.sha1 }
    }

    fun setGridView(grid: Boolean) {
        if (this.isGridView != grid) {
            this.isGridView = grid
            notifyDataSetChanged()
        }
    }

    class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvBookTitle: TextView = itemView.findViewById(R.id.tvBookTitle)
        private val tvBookAuthor: TextView = itemView.findViewById(R.id.tvBookAuthor)
        private val tvBookSeries: TextView = itemView.findViewById(R.id.tvBookSeries)
        private val ivCover: ImageView = itemView.findViewById(R.id.ivCover)
        private val vCoverBackground: View = itemView.findViewById(R.id.vCoverBackground)
        private val btnDelete: View = itemView.findViewById(R.id.btnDelete)
        private val pbReadingProgress: ProgressBar = itemView.findViewById(R.id.pbReadingProgress)

        fun bind(
            book: BookEntity,
            onOpenBook: (BookEntity) -> Unit,
            onDeleteBook: ((BookEntity) -> Unit)?
        ) {
            tvBookTitle.text = book.title
            tvBookAuthor.text = book.author ?: "Неизвестен"
            tvBookAuthor.setOnClickListener {
                val intent = Intent(itemView.context, AuthorBooksActivity::class.java).apply {
                    putExtra("AUTHOR_NAME", book.author)
                }
                itemView.context.startActivity(intent)
            }

            if (!book.series.isNullOrEmpty()) {
                tvBookSeries.visibility = View.VISIBLE
                tvBookSeries.text = book.series
                tvBookSeries.setOnClickListener {
                    val intent = Intent(itemView.context, SeriesBooksActivity::class.java).apply {
                        putExtra("SERIES_NAME", book.series)
                    }
                    itemView.context.startActivity(intent)
                }
            } else {
                tvBookSeries.visibility = View.GONE
            }

            // Set background gradient fallback
            val startColorHex = if (book.coverGradientStart.startsWith("#")) book.coverGradientStart else "#E94560"
            val endColorHex = if (book.coverGradientEnd.startsWith("#")) book.coverGradientEnd else "#1A1A2E"
            
            try {
                val startColor = Color.parseColor(startColorHex)
                val endColor = Color.parseColor(endColorHex)
                val gradient = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(startColor, endColor)
                )
                gradient.cornerRadius = 6f * itemView.resources.displayMetrics.density
                vCoverBackground.background = gradient
            } catch (e: Exception) {
                vCoverBackground.setBackgroundColor(Color.LTGRAY)
            }

            // Load cover if present
            if (book.coverPath != null && File(book.coverPath).exists()) {
                ivCover.visibility = View.VISIBLE
                try {
                    val bitmap = BitmapFactory.decodeFile(book.coverPath)
                    ivCover.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    ivCover.visibility = View.GONE
                }
            } else {
                ivCover.visibility = View.GONE
            }

            // Set reading progress
            val progressPercent = if (book.totalCharacters > 0) {
                ((book.currentProgressChar.toFloat() / book.totalCharacters) * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }
            if (progressPercent > 0) {
                pbReadingProgress.visibility = View.VISIBLE
                pbReadingProgress.progress = progressPercent
            } else {
                pbReadingProgress.visibility = View.GONE
            }

            // Click interactions
            itemView.setOnClickListener { onOpenBook(book) }

            // Long click interactions (Context Menu)
            itemView.setOnLongClickListener { view ->
                val popup = androidx.appcompat.widget.PopupMenu(view.context, view)
                popup.menu.add(0, 1, 0, "Открыть детали") // View Details
                if (onDeleteBook != null) {
                    popup.menu.add(0, 2, 1, "Удалить книгу") // Delete Book
                }

                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        1 -> {
                            onOpenBook(book)
                            true
                        }
                        2 -> {
                            onDeleteBook?.invoke(book)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
                true
            }
            
            // Hide delete button and remove listener
            btnDelete.visibility = View.GONE
            btnDelete.setOnClickListener(null)
        }
    }
}
