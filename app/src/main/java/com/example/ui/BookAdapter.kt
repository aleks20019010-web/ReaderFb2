package com.example.ui

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.BookEntity
import java.io.File

class BookAdapter(
    private var books: List<BookEntity>,
    private val onOpenBook: (BookEntity) -> Unit,
    private val onDeleteBook: (BookEntity) -> Unit
) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_book_minimalist, parent, false)
        return BookViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = books[position]
        holder.bind(book, onOpenBook, onDeleteBook)
    }

    override fun getItemCount(): Int = books.size

    fun updateData(newBooks: List<BookEntity>) {
        this.books = newBooks
        notifyDataSetChanged()
    }

    class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvBookTitle: TextView = itemView.findViewById(R.id.tvBookTitle)
        private val tvBookAuthor: TextView = itemView.findViewById(R.id.tvBookAuthor)
        private val ivCover: ImageView = itemView.findViewById(R.id.ivCover)
        private val vCoverBackground: View = itemView.findViewById(R.id.vCoverBackground)
        private val btnDelete: View = itemView.findViewById(R.id.btnDelete)

        fun bind(
            book: BookEntity,
            onOpenBook: (BookEntity) -> Unit,
            onDeleteBook: (BookEntity) -> Unit
        ) {
            tvBookTitle.text = book.title
            tvBookAuthor.text = book.author ?: "Неизвестен"

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
            btnDelete.setOnClickListener { onDeleteBook(book) }
        }
    }
}
