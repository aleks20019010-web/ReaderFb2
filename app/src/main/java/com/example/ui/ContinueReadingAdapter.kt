package com.example.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.BookEntity
import java.io.File

class ContinueReadingAdapter(
    private var books: List<BookEntity>,
    private val onOpenBook: (BookEntity) -> Unit
) : RecyclerView.Adapter<ContinueReadingAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvBookTitle: TextView = itemView.findViewById(R.id.tvBookTitle)
        private val tvProgressText: TextView = itemView.findViewById(R.id.tvProgressText)
        private val ivCover: ImageView = itemView.findViewById(R.id.ivCover)
        private val vCoverBackground: View = itemView.findViewById(R.id.vCoverBackground)
        private val pbReadingProgress: ProgressBar = itemView.findViewById(R.id.pbReadingProgress)

        fun bind(book: BookEntity, onOpenBook: (BookEntity) -> Unit) {
            tvBookTitle.text = book.title

            // Set reading progress percent and text
            val progressPercent = if (book.totalCharacters > 0) {
                ((book.currentProgressChar.toFloat() / book.totalCharacters) * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }
            tvProgressText.text = "Прочитано: $progressPercent%"
            pbReadingProgress.progress = progressPercent

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

            // Click interactions
            itemView.setOnClickListener { onOpenBook(book) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_continue_reading, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(books[position], onOpenBook)
        
        // Smooth entry animation for continue reading items
        holder.itemView.alpha = 0f
        holder.itemView.translationY = 24f * holder.itemView.resources.displayMetrics.density
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setStartDelay(position.toLong() * 30)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    override fun getItemCount(): Int = books.size

    fun updateBooks(newBooks: List<BookEntity>) {
        books = newBooks
        notifyDataSetChanged()
    }
}
