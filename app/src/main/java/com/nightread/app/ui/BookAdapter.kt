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
    private val onOpenBook: (BookEntity, View) -> Unit,
    private val onDeleteBook: ((BookEntity) -> Unit)? = null
) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {

    var isSelectionMode: Boolean = false
        private set
    val selectedSha1s = mutableSetOf<String>()

    var onSelectionModeChanged: ((Boolean) -> Unit)? = null
    var onSelectionCountChanged: ((Int) -> Unit)? = null

    fun enterSelectionMode(initialBook: BookEntity) {
        isSelectionMode = true
        selectedSha1s.clear()
        selectedSha1s.add(initialBook.sha1)
        notifyDataSetChanged()
        onSelectionModeChanged?.invoke(true)
        onSelectionCountChanged?.invoke(selectedSha1s.size)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedSha1s.clear()
        notifyDataSetChanged()
        onSelectionModeChanged?.invoke(false)
        onSelectionCountChanged?.invoke(0)
    }

    fun toggleSelection(book: BookEntity) {
        if (selectedSha1s.contains(book.sha1)) {
            selectedSha1s.remove(book.sha1)
        } else {
            selectedSha1s.add(book.sha1)
        }
        val pos = books.indexOfFirst { it.sha1 == book.sha1 }
        if (pos != -1) {
            notifyItemChanged(pos)
        }
        if (selectedSha1s.isEmpty()) {
            exitSelectionMode()
        } else {
            onSelectionCountChanged?.invoke(selectedSha1s.size)
        }
    }

    fun selectAll() {
        if (!isSelectionMode) {
            isSelectionMode = true
            onSelectionModeChanged?.invoke(true)
        }
        selectedSha1s.clear()
        selectedSha1s.addAll(books.map { it.sha1 })
        notifyDataSetChanged()
        onSelectionCountChanged?.invoke(selectedSha1s.size)
    }

    fun getSelectedBooks(): List<BookEntity> {
        return books.filter { selectedSha1s.contains(it.sha1) }
    }
    
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
    private var lastAnimatedPosition: Int = -1

    // Buffer for batching updates during scanning to prevent cover flickering
    private val bookBuffer = mutableListOf<BookEntity>()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastFlushTime: Long = 0L
    private var isScanMode: Boolean = false

    private val flushRunnable = Runnable {
        flushBuffer()
    }

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
        holder.bind(
            book,
            onOpenBook,
            onDeleteBook,
            isSelectionMode,
            selectedSha1s.contains(book.sha1),
            ::enterSelectionMode,
            ::toggleSelection
        )
        
        holder.itemView.animate().cancel()
        
        if (position > lastAnimatedPosition) {
            val density = holder.itemView.context.resources.displayMetrics.density
            val startTranslationY = 16f * density
            holder.itemView.translationY = startTranslationY
            holder.itemView.alpha = 0.3f
            
            holder.itemView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(220)
                .setStartDelay(0)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
            
            lastAnimatedPosition = position
        } else {
            holder.itemView.alpha = 1f
            holder.itemView.translationY = 0f
        }
    }

    override fun onViewRecycled(holder: BookViewHolder) {
        super.onViewRecycled(holder)
        holder.itemView.animate().cancel()
        holder.itemView.alpha = 1f
        holder.itemView.translationY = 0f
    }

    override fun onViewAttachedToWindow(holder: BookViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.registerParallax()
        holder.startPulseAnimation()
    }

    override fun onViewDetachedFromWindow(holder: BookViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.unregisterParallax()
        holder.stopPulseAnimation()
    }


    override fun getItemCount(): Int = books.size

    fun addBooks(addedBooks: List<BookEntity>, newFilteredList: List<BookEntity>) {
        addedBooks.forEach { newlyAddedSha1s.add(it.sha1) }
        updateData(newFilteredList, isScanning = true)
    }

    /**
     * Updates adapter data. If [isScanning] is true, incoming updates are held in [bookBuffer]
     * and flushed every 500ms or when 50 items accumulate.
     */
    fun updateData(newBooks: List<BookEntity>, isScanning: Boolean = false) {
        if (!isScanning) {
            handler.removeCallbacks(flushRunnable)
            isScanMode = false
            bookBuffer.clear()
            bookBuffer.addAll(newBooks)
            flushBufferImmediately(newBooks)
            return
        }

        isScanMode = true
        bookBuffer.clear()
        bookBuffer.addAll(newBooks)

        val pendingDiffSize = Math.abs(bookBuffer.size - books.size)
        val currentTime = System.currentTimeMillis()
        val timeSinceLastFlush = currentTime - lastFlushTime

        if (pendingDiffSize >= 50 || timeSinceLastFlush >= 500L) {
            handler.removeCallbacks(flushRunnable)
            flushBuffer()
        } else {
            handler.removeCallbacks(flushRunnable)
            val delay = (500L - timeSinceLastFlush).coerceAtLeast(0L)
            handler.postDelayed(flushRunnable, delay)
        }
    }

    /**
     * Add a single book to buffer directly during scanning.
     */
    fun addBookToBuffer(book: BookEntity) {
        if (!bookBuffer.contains(book)) {
            bookBuffer.add(book)
        }
        checkFlushCondition()
    }

    /**
     * Add a list of books to buffer directly during scanning.
     */
    fun addBooksToBuffer(newBooks: List<BookEntity>) {
        newBooks.forEach { book ->
            if (!bookBuffer.contains(book)) {
                bookBuffer.add(book)
            }
        }
        checkFlushCondition()
    }

    private fun checkFlushCondition() {
        val pendingDiffSize = Math.abs(bookBuffer.size - books.size)
        val currentTime = System.currentTimeMillis()
        val timeSinceLastFlush = currentTime - lastFlushTime

        if (pendingDiffSize >= 50 || timeSinceLastFlush >= 500L) {
            handler.removeCallbacks(flushRunnable)
            flushBuffer()
        } else {
            handler.removeCallbacks(flushRunnable)
            val delay = (500L - timeSinceLastFlush).coerceAtLeast(0L)
            handler.postDelayed(flushRunnable, delay)
        }
    }

    /**
     * Immediately flushes remaining buffered books to the main list and dispatches updates via DiffUtil.
     */
    fun flushBuffer() {
        handler.removeCallbacks(flushRunnable)
        val targetList = bookBuffer.toList()
        if (books == targetList) return
        flushBufferImmediately(targetList)
    }

    private fun flushBufferImmediately(newBooksList: List<BookEntity>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize() = books.size
            override fun getNewListSize() = newBooksList.size
            
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return books[oldItemPosition].sha1 == newBooksList[newItemPosition].sha1
            }
            
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = books[oldItemPosition]
                val new = newBooksList[newItemPosition]
                return old.title == new.title &&
                       old.currentProgressChar == new.currentProgressChar &&
                       old.currentPageIndex == new.currentPageIndex &&
                       old.totalCharacters == new.totalCharacters &&
                       old.coverPath == new.coverPath
            }
        }
        
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.books = newBooksList
        diffResult.dispatchUpdatesTo(this)
        lastFlushTime = System.currentTimeMillis()
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

    class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), ParallaxSensorManager.ParallaxListener {
        private val tvBookTitle: TextView = itemView.findViewById(R.id.tvBookTitle)
        private val tvBookAuthor: TextView = itemView.findViewById(R.id.tvBookAuthor)
        private val tvBookSeries: TextView = itemView.findViewById(R.id.tvBookSeries)
        private val tvBookAnnotation: TextView? = itemView.findViewById(R.id.tvBookAnnotation)
        private val ivCover: ImageView = itemView.findViewById(R.id.ivCover)
        private val tvCoverLetter: TextView = itemView.findViewById(R.id.tvCoverLetter)
        private val vCoverBackground: View = itemView.findViewById(R.id.vCoverBackground)
        private val shimmerCover: com.facebook.shimmer.ShimmerFrameLayout? = itemView.findViewById(R.id.shimmerCover)
        private val vCoverGlow: View? = itemView.findViewById(R.id.vCoverGlow)
        private val cvBookCover: View = itemView.findViewById(R.id.cvBookCover)
        private val cbSelect: android.widget.CheckBox? = itemView.findViewById(R.id.cbSelect)
        private val vSelectionOverlay: View? = itemView.findViewById(R.id.vSelectionOverlay)
        private val vReadingProgressTrack: View? = itemView.findViewById(R.id.vReadingProgressTrack)
        private val vReadingProgress: View? = itemView.findViewById(R.id.vReadingProgress)
        private val vReadingProgressRemaining: View? = itemView.findViewById(R.id.vReadingProgressRemaining)

        override fun onTiltChanged(tiltX: Float, tiltY: Float) {
            val maxRotation = 12f // degrees max tilt
            val maxTranslation = 8f // translation depth (dp)
            
            val density = itemView.context.resources.displayMetrics.density
            val translationPx = maxTranslation * density
            
            // Set 3D perspective camera distance
            cvBookCover.cameraDistance = 8000f * density
            
            // Apply 3D rotation and translation to the book card cover
            cvBookCover.rotationY = -tiltX * maxRotation
            cvBookCover.rotationX = tiltY * maxRotation
            cvBookCover.translationX = -tiltX * translationPx
            cvBookCover.translationY = tiltY * translationPx
            
            // Translate glow in the opposite direction for parallax separation
            vCoverGlow?.let { glow ->
                glow.translationX = tiltX * (translationPx * 1.6f)
                glow.translationY = -tiltY * (translationPx * 1.6f)
                glow.scaleX = 1.03f + (Math.abs(tiltX) * 0.05f)
                glow.scaleY = 1.03f + (Math.abs(tiltY) * 0.05f)
            }
        }

        fun registerParallax() {
            ParallaxSensorManager.init(itemView.context.applicationContext)
            ParallaxSensorManager.registerListener(this)
        }

        fun unregisterParallax() {
            ParallaxSensorManager.unregisterListener(this)
            
            // Reset values to flat state
            cvBookCover.rotationX = 0f
            cvBookCover.rotationY = 0f
            cvBookCover.translationX = 0f
            cvBookCover.translationY = 0f
            
            vCoverGlow?.let { glow ->
                glow.translationX = 0f
                glow.translationY = 0f
                glow.scaleX = 1f
                glow.scaleY = 1f
            }
        }

        private var pulseAnimX: ObjectAnimator? = null
        private var pulseAnimY: ObjectAnimator? = null

        fun startPulseAnimation() {
            if (pulseAnimX == null) {
                pulseAnimX = ObjectAnimator.ofFloat(cvBookCover, View.SCALE_X, 1f, 1.03f).apply {
                    duration = 1500
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                }
                pulseAnimY = ObjectAnimator.ofFloat(cvBookCover, View.SCALE_Y, 1f, 1.03f).apply {
                    duration = 1500
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                }
            }
            if (pulseAnimX?.isRunning == false) pulseAnimX?.start()
            if (pulseAnimY?.isRunning == false) pulseAnimY?.start()
        }

        fun stopPulseAnimation() {
            pulseAnimX?.cancel()
            pulseAnimY?.cancel()
            cvBookCover.scaleX = 1f
            cvBookCover.scaleY = 1f
        }

        private fun getDominantColor(bitmap: android.graphics.Bitmap): Int {
            val width = bitmap.width
            val height = bitmap.height
            var redSum = 0L
            var greenSum = 0L
            var blueSum = 0L
            var count = 0
            
            val stepX = (width / 8).coerceAtLeast(1)
            val stepY = (height / 8).coerceAtLeast(1)
            
            for (x in 0 until width step stepX) {
                for (y in 0 until height step stepY) {
                    val pixel = bitmap.getPixel(x, y)
                    val alpha = (pixel shr 24) and 0xff
                    if (alpha > 128) {
                        redSum += (pixel shr 16) and 0xff
                        greenSum += (pixel shr 8) and 0xff
                        blueSum += pixel and 0xff
                        count++
                    }
                }
            }
            
            if (count == 0) return Color.parseColor("#E94560")
            
            val r = (redSum / count).toInt()
            val g = (greenSum / count).toInt()
            val b = (blueSum / count).toInt()
            return Color.rgb(r, g, b)
        }

        private fun applyGlow(baseColor: Int) {
            val context = itemView.context
            val density = context.resources.displayMetrics.density
            
            val hsv = FloatArray(3)
            Color.colorToHSV(baseColor, hsv)
            hsv[1] = (hsv[1] * 1.3f).coerceAtMost(1.0f) // boost saturation
            hsv[2] = (hsv[2] * 1.3f).coerceIn(0.6f, 1.0f) // boost brightness
            val glowColor = Color.HSVToColor(hsv)

            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * density
                
                val c1 = androidx.core.graphics.ColorUtils.setAlphaComponent(glowColor, 150)
                val c2 = androidx.core.graphics.ColorUtils.setAlphaComponent(glowColor, 35)
                val c3 = Color.TRANSPARENT
                
                colors = intArrayOf(c1, c2, c3)
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = 120f * density
                setGradientCenter(0.5f, 0.5f)
            }
            vCoverGlow?.background = drawable
        }

        fun bind(
            book: BookEntity,
            onOpenBook: (BookEntity, View) -> Unit,
            onDeleteBook: ((BookEntity) -> Unit)?,
            isSelectionMode: Boolean,
            isSelected: Boolean,
            onEnterSelectionMode: (BookEntity) -> Unit,
            onToggleSelection: (BookEntity) -> Unit
        ) {
            android.util.Log.d("BookAdapter", "Binding book in ViewHolder: title='${book.title}', author='${book.author}', sha1='${book.sha1}', coverPath='${book.coverPath}'")
            ivCover.transitionName = "cover_${book.sha1}"
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
                    (itemView.context as Activity).overridePendingTransition(R.anim.fade_in_custom, R.anim.fade_out_custom)
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
                    (itemView.context as Activity).overridePendingTransition(R.anim.fade_in_custom, R.anim.fade_out_custom)
                }
                }
            } else {
                tvBookSeries.visibility = View.GONE
            }

            // Set background gradient fallback
            val startColorHex = if (book.coverGradientStart.startsWith("#")) book.coverGradientStart else "#E94560"
            val endColorHex = if (book.coverGradientEnd.startsWith("#")) book.coverGradientEnd else "#1A1A2E"
            
            val fallbackColor = try {
                val startColor = Color.parseColor(startColorHex)
                val endColor = Color.parseColor(endColorHex)
                val gradient = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(startColor, endColor)
                )
                gradient.cornerRadius = 6f * itemView.resources.displayMetrics.density
                vCoverBackground.background = gradient
                startColor
            } catch (e: Exception) {
                vCoverBackground.setBackgroundColor(Color.LTGRAY)
                Color.parseColor("#E94560")
            }

            // Load cover if present
            val coverFile = if (!book.coverPath.isNullOrEmpty()) File(book.coverPath) else null
            if (coverFile != null && coverFile.exists()) {
                ivCover.visibility = View.VISIBLE
                tvCoverLetter.visibility = View.GONE
                shimmerCover?.visibility = View.VISIBLE
                shimmerCover?.startShimmer()
                try {
                    ivCover.load(coverFile) {
                        crossfade(true)
                        allowHardware(false)
                        memoryCacheKey(book.sha1)
                        diskCacheKey(book.sha1)
                        listener(
                            onSuccess = { _, result ->
                                shimmerCover?.stopShimmer()
                                shimmerCover?.visibility = View.GONE
                                val bitmapDrawable = result.drawable as? android.graphics.drawable.BitmapDrawable
                                val bitmap = bitmapDrawable?.bitmap
                                if (bitmap != null) {
                                    val dominant = getDominantColor(bitmap)
                                    applyGlow(dominant)
                                } else {
                                    applyGlow(fallbackColor)
                                }
                            },
                            onError = { _, _ ->
                                shimmerCover?.stopShimmer()
                                shimmerCover?.visibility = View.GONE
                                ivCover.visibility = View.GONE
                                tvCoverLetter.visibility = View.VISIBLE
                                tvCoverLetter.text = if (!book.title.isNullOrEmpty()) book.title.trim().take(1).uppercase() else "?"
                                applyGlow(fallbackColor)
                            }
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BookAdapter", "Error loading cover with Coil: ${e.message}")
                    shimmerCover?.stopShimmer()
                    shimmerCover?.visibility = View.GONE
                    ivCover.visibility = View.GONE
                    tvCoverLetter.visibility = View.VISIBLE
                    tvCoverLetter.text = if (!book.title.isNullOrEmpty()) book.title.trim().take(1).uppercase() else "?"
                    applyGlow(fallbackColor)
                }
            } else {
                ivCover.setImageDrawable(null)
                ivCover.visibility = View.GONE
                shimmerCover?.visibility = View.VISIBLE
                shimmerCover?.startShimmer()
                tvCoverLetter.visibility = View.VISIBLE
                tvCoverLetter.text = if (!book.title.isNullOrEmpty()) book.title.trim().take(1).uppercase() else "?"
                applyGlow(fallbackColor)
            }

            // Set reading progress
            val progressPercent = if (book.totalCharacters > 0) {
                val calculated = ((book.currentProgressChar.toFloat() / book.totalCharacters) * 100).toInt().coerceIn(0, 100)
                val finalPercent = if (calculated >= 98) 100 else calculated
                if (finalPercent == 0 && (book.currentProgressChar > 0 || book.currentPageIndex > 0)) {
                    1
                } else {
                    finalPercent
                }
            } else {
                0
            }
            if (progressPercent > 0) {
                vReadingProgressTrack?.visibility = View.VISIBLE
                val pParam = vReadingProgress?.layoutParams as? android.widget.LinearLayout.LayoutParams
                val rParam = vReadingProgressRemaining?.layoutParams as? android.widget.LinearLayout.LayoutParams
                if (pParam != null && rParam != null) {
                    pParam.weight = progressPercent.toFloat()
                    rParam.weight = (100 - progressPercent).toFloat()
                    vReadingProgress?.layoutParams = pParam
                    vReadingProgressRemaining?.layoutParams = rParam
                }
            } else {
                vReadingProgressTrack?.visibility = View.GONE
            }


            if (isSelectionMode) {
                cbSelect?.visibility = View.VISIBLE
                cbSelect?.isChecked = isSelected
                vSelectionOverlay?.visibility = if (isSelected) View.VISIBLE else View.GONE
                if (cvBookCover is com.google.android.material.card.MaterialCardView) {
                    if (isSelected) {
                        cvBookCover.strokeColor = Color.parseColor("#6C5CE7")
                        cvBookCover.strokeWidth = (2.5f * itemView.resources.displayMetrics.density).toInt()
                    } else {
                        cvBookCover.strokeColor = Color.parseColor("#25FFFFFF")
                        cvBookCover.strokeWidth = (1f * itemView.resources.displayMetrics.density).toInt()
                    }
                }
            } else {
                cbSelect?.visibility = View.GONE
                vSelectionOverlay?.visibility = View.GONE
                if (cvBookCover is com.google.android.material.card.MaterialCardView) {
                    cvBookCover.strokeColor = Color.parseColor("#25FFFFFF")
                    cvBookCover.strokeWidth = (1f * itemView.resources.displayMetrics.density).toInt()
                }
            }

            // Click interactions
            itemView.setOnClickListener {
                if (isSelectionMode) {
                    onToggleSelection(book)
                } else {
                    android.util.Log.d("BookAdapter", "Book clicked: title='${book.title}', author='${book.author}', sha1='${book.sha1}'")
                    onOpenBook(book, ivCover)
                }
            }

            // Long click interactions (enables selection mode)
            itemView.setOnLongClickListener { view ->
                if (!isSelectionMode) {
                    onEnterSelectionMode(book)
                    true
                } else {
                    onToggleSelection(book)
                    true
                }
            }
            
            // Hide delete button and remove listener
            val btnDelete: View? = itemView.findViewById(R.id.btnDelete)
            btnDelete?.visibility = View.GONE
            btnDelete?.setOnClickListener(null)
        }
    }
}
