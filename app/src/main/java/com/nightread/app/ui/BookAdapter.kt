package com.nightread.app.ui

import android.view.ViewGroup

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.animation.AnimatorListenerAdapter
import android.animation.Animator
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.nightread.app.R
import com.nightread.app.data.BookEntity
import java.io.File

class BookAdapter(
    private var books: List<BookEntity>,
    private val onOpenBook: (BookEntity) -> Unit,
    private val onDeleteBook: ((BookEntity) -> Unit)? = null
) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {

    
    companion object {
        private const val VIEW_TYPE_GRID = 0
        private const val VIEW_TYPE_LIST = 1

        private fun triggerGoldShine(vararg textViews: TextView) {
            for (tv in textViews) {
                val width = tv.width.toFloat()
                if (width <= 0f) continue
                val textShader = LinearGradient(
                    0f, 0f, width, 0f,
                    intArrayOf(
                        Color.parseColor("#D4A373"),
                        Color.parseColor("#B8860B"),
                        Color.parseColor("#DAA520"),
                        Color.parseColor("#D4A373")
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                tv.paint.shader = textShader
                
                val matrix = Matrix()
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 800
                    addUpdateListener { anim ->
                        val progress = anim.animatedValue as Float
                        matrix.setTranslate(progress * width * 2 - width, 0f)
                        textShader.setLocalMatrix(matrix)
                        tv.invalidate()
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            tv.paint.shader = null
                            tv.invalidate()
                        }
                    })
                    start()
                }
            }
        }
    }


    val newlyAddedSha1s = mutableSetOf<String>()
    private var isGridView: Boolean = true

    override fun getItemViewType(position: Int): Int {
        return if (isGridView) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val layoutRes = if (viewType == VIEW_TYPE_GRID) {
            R.layout.item_book_grid
        } else {
            R.layout.item_book_list
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return BookViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = books[position]
        holder.bind(book, onOpenBook, onDeleteBook)
        
        holder.itemView.animate().cancel(); holder.itemView.alpha = 1f; holder.itemView.translationY = 0f
    }


    override fun getItemCount(): Int = books.size

    fun addBooks(addedBooks: List<BookEntity>, newFilteredList: List<BookEntity>) {
        addedBooks.forEach { newlyAddedSha1s.add(it.sha1) }
        updateData(newFilteredList)
    }

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
        private val tvBookAnnotation: TextView? = itemView.findViewById(R.id.tvBookAnnotation)
        private val ivCover: ImageView = itemView.findViewById(R.id.ivCover)
        private val tvCoverLetter: TextView = itemView.findViewById(R.id.tvCoverLetter)
        private val vCoverBackground: View = itemView.findViewById(R.id.vCoverBackground)
        private val btnDelete: View = itemView.findViewById(R.id.btnDelete)
        private val pbReadingProgress: ProgressBar = itemView.findViewById(R.id.pbReadingProgress)

        fun bind(
            book: BookEntity,
            onOpenBook: (BookEntity) -> Unit,
            onDeleteBook: ((BookEntity) -> Unit)?
        ) {
            android.util.Log.d("BookAdapter", "Binding book in ViewHolder: title='${book.title}', author='${book.author}', sha1='${book.sha1}', coverPath='${book.coverPath}'")
            tvBookTitle.text = book.title

            if (tvBookAnnotation != null) {
                if (!book.annotation.isNullOrBlank()) {
                    tvBookAnnotation.visibility = View.VISIBLE
                    tvBookAnnotation.text = book.annotation
                } else {
                    tvBookAnnotation.visibility = View.GONE
                }
            }
            tvBookAuthor.text = book.author ?: "Неизвестен"
            tvBookAuthor.setOnClickListener {
                val intent = Intent(itemView.context, AuthorBooksActivity::class.java).apply {
                    putExtra("AUTHOR_NAME", book.author)
                }
                itemView.context.startActivity(intent)
                if (itemView.context is Activity) {
                    (itemView.context as Activity).overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }
            }

            if (!book.series.isNullOrEmpty()) {
                tvBookSeries.visibility = View.VISIBLE
                tvBookSeries.text = book.series
                tvBookSeries.setOnClickListener {
                    val intent = Intent(itemView.context, SeriesBooksActivity::class.java).apply {
                        putExtra("SERIES_NAME", book.series)
                    }
                    itemView.context.startActivity(intent)
                if (itemView.context is Activity) {
                    (itemView.context as Activity).overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }
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
            val coverFile = if (!book.coverPath.isNullOrEmpty()) File(book.coverPath) else null
            if (coverFile != null && coverFile.exists()) {
                ivCover.visibility = View.VISIBLE
                tvCoverLetter.visibility = View.GONE
                try {
                    ivCover.load(coverFile) {
                        crossfade(true)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BookAdapter", "Error loading cover with Coil: ${e.message}")
                    ivCover.visibility = View.GONE
                    tvCoverLetter.visibility = View.VISIBLE
                    tvCoverLetter.text = if (!book.title.isNullOrEmpty()) book.title.trim().take(1).uppercase() else "?"
                }
            } else {
                ivCover.setImageDrawable(null)
                ivCover.visibility = View.GONE
                tvCoverLetter.visibility = View.VISIBLE
                tvCoverLetter.text = if (!book.title.isNullOrEmpty()) book.title.trim().take(1).uppercase() else "?"
            }

            // Set reading progress
            val progressPercent = if (book.totalCharacters > 0) {
                val calculated = ((book.currentProgressChar.toFloat() / book.totalCharacters) * 100).toInt().coerceIn(0, 100)
                if (calculated == 0 && (book.currentProgressChar > 0 || book.currentPageIndex > 0)) {
                    1
                } else {
                    calculated
                }
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
            itemView.setOnClickListener {
                android.util.Log.d("BookAdapter", "Book clicked: title='${book.title}', author='${book.author}', sha1='${book.sha1}'")
                onOpenBook(book)
            }


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
