package com.nightread.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.R
import com.nightread.app.data.BookEntity
import java.io.File

sealed class SeriesItem {
    data class Header(val seriesName: String) : SeriesItem()
    data class Book(val book: BookEntity) : SeriesItem()
}

class SeriesGroupAdapter(
    private val onOpenBook: (BookEntity) -> Unit,
    private val onDeleteBook: ((BookEntity) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<SeriesItem> = emptyList()
    private var isGridView: Boolean = true

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_BOOK_GRID = 1
        const val VIEW_TYPE_BOOK_LIST = 2

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

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SeriesItem.Header -> VIEW_TYPE_HEADER
            is SeriesItem.Book -> if (isGridView) VIEW_TYPE_BOOK_GRID else VIEW_TYPE_BOOK_LIST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_series_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_BOOK_GRID -> {
                val view = inflater.inflate(R.layout.item_book_minimalist, parent, false)
                BookViewHolder(view)
            }
            VIEW_TYPE_BOOK_LIST -> {
                val view = inflater.inflate(R.layout.item_book_detailed_list, parent, false)
                BookViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        
        when (holder) {
            is HeaderViewHolder -> {
                val header = item as SeriesItem.Header
                holder.bind(header.seriesName)
            }
            is BookViewHolder -> {
                val bookItem = item as SeriesItem.Book
                holder.bind(bookItem.book, onOpenBook, onDeleteBook)
                
                holder.itemView.alpha = 0f
                holder.itemView.translationY = 32f * holder.itemView.resources.displayMetrics.density
                holder.itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(350)
                    .setStartDelay((position % 12).toLong() * 30)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newBooks: List<BookEntity>) {
        val groupedMap = newBooks.groupBy { it.series ?: "Без серии" }
        val newItems = mutableListOf<SeriesItem>()
        
        val seriesKeys = groupedMap.keys.sortedWith(compareBy { if (it == "Без серии") 1 else 0 })
        
        for (key in seriesKeys) {
            newItems.add(SeriesItem.Header(key))
            val booksInSeries = groupedMap[key] ?: emptyList()
            val sortedBooks = booksInSeries.sortedBy { it.seriesIndex ?: 9999 }
            for (book in sortedBooks) {
                newItems.add(SeriesItem.Book(book))
            }
        }
        
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = items[oldItemPosition]
                val new = newItems[newItemPosition]
                return if (old is SeriesItem.Header && new is SeriesItem.Header) {
                    old.seriesName == new.seriesName
                } else if (old is SeriesItem.Book && new is SeriesItem.Book) {
                    old.book.sha1 == new.book.sha1
                } else {
                    false
                }
            }
            
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = items[oldItemPosition]
                val new = newItems[newItemPosition]
                return if (old is SeriesItem.Header && new is SeriesItem.Header) {
                    old.seriesName == new.seriesName
                } else if (old is SeriesItem.Book && new is SeriesItem.Book) {
                    old.book.title == new.book.title &&
                    old.book.currentProgressChar == new.book.currentProgressChar &&
                    old.book.coverPath == new.book.coverPath &&
                    old.book.series == new.book.series &&
                    old.book.seriesIndex == new.book.seriesIndex
                } else {
                    false
                }
            }
        }
        
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    fun getBookAt(position: Int): BookEntity? {
        val item = items.getOrNull(position)
        return if (item is SeriesItem.Book) item.book else null
    }

    fun getPositionOfBook(book: BookEntity): Int {
        return items.indexOfFirst { it is SeriesItem.Book && it.book.sha1 == book.sha1 }
    }

    fun setGridView(grid: Boolean) {
        if (this.isGridView != grid) {
            this.isGridView = grid
            notifyDataSetChanged()
        }
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvHeader: TextView = itemView.findViewById(R.id.tvSeriesHeader)
        fun bind(seriesName: String) {
            tvHeader.text = seriesName
        }
    }

    class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvBookTitle: TextView = itemView.findViewById(R.id.tvBookTitle)
        private val tvBookAuthor: TextView = itemView.findViewById(R.id.tvBookAuthor)
        private val tvBookSeries: TextView? = itemView.findViewById(R.id.tvBookSeries)
        private val ivCover: ImageView = itemView.findViewById(R.id.ivCover)
        private val vCoverBackground: View = itemView.findViewById(R.id.vCoverBackground)
        private val btnDelete: View? = itemView.findViewById(R.id.btnDelete)
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
                if (itemView.context is Activity) {
                    (itemView.context as Activity).overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }
            }

            if (tvBookSeries != null) {
                if (book.seriesIndex != null) {
                    tvBookSeries.visibility = View.VISIBLE
                    tvBookSeries.text = "Книга ${book.seriesIndex}"
                } else {
                    tvBookSeries.visibility = View.GONE
                }
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

            // Breathing effect on cover
            val oldAnimator = ivCover.getTag(R.id.breathing_animator) as? ObjectAnimator
            oldAnimator?.cancel()
            val newAnimator = ObjectAnimator.ofFloat(ivCover, "alpha", 0.95f, 1.0f).apply {
                duration = 2000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                startDelay = (Math.random() * 1000).toLong()
                start()
            }
            ivCover.setTag(R.id.breathing_animator, newAnimator)

            // Bounce animation
            itemView.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        triggerGoldShine(tvBookTitle, tvBookAuthor)
                        val scaleDownX = ObjectAnimator.ofFloat(v, "scaleX", 0.95f)
                        val scaleDownY = ObjectAnimator.ofFloat(v, "scaleY", 0.95f)
                        scaleDownX.duration = 100
                        scaleDownY.duration = 100
                        val scaleDown = AnimatorSet()
                        scaleDown.play(scaleDownX).with(scaleDownY)
                        scaleDown.start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val scaleUpX = ObjectAnimator.ofFloat(v, "scaleX", 1.0f)
                        val scaleUpY = ObjectAnimator.ofFloat(v, "scaleY", 1.0f)
                        scaleUpX.duration = 300
                        scaleUpY.duration = 300
                        scaleUpX.interpolator = OvershootInterpolator(1.5f)
                        scaleUpY.interpolator = OvershootInterpolator(1.5f)
                        val scaleUp = AnimatorSet()
                        scaleUp.play(scaleUpX).with(scaleUpY)
                        scaleUp.start()
                        
                        if (event.action == MotionEvent.ACTION_UP) {
                            v.performClick()
                        }
                    }
                }
                true
            }
            itemView.setOnClickListener {
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
            
            btnDelete?.visibility = View.GONE
            btnDelete?.setOnClickListener(null)
        }
    }
}
