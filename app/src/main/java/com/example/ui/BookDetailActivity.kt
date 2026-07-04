package com.example.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.example.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookDetailActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvAuthor: TextView
    private lateinit var tvSeries: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvAnnotation: TextView
    private lateinit var pbProgress: ProgressBar
    private lateinit var btnRead: Button
    private lateinit var vCoverBackground: View
    private lateinit var ivCover: ImageView

    private var bookSha1: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_detail)

        // Handle WindowInsets
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val rootView = findViewById<View>(android.R.id.content)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, insets.top, 0, insets.bottom)
            windowInsets
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        tvTitle = findViewById(R.id.tvTitle)
        tvAuthor = findViewById(R.id.tvAuthor)
        tvSeries = findViewById(R.id.tvSeries)
        tvProgress = findViewById(R.id.tvProgress)
        tvAnnotation = findViewById(R.id.tvAnnotation)
        pbProgress = findViewById(R.id.pbProgress)
        btnRead = findViewById(R.id.btnRead)
        vCoverBackground = findViewById(R.id.vCoverBackground)
        ivCover = findViewById(R.id.ivCover)

        bookSha1 = intent.getStringExtra("BOOK_SHA1")

        if (bookSha1.isNullOrEmpty()) {
            finish()
            return
        }

        btnRead.setOnClickListener {
            val intent = Intent(this, ReaderActivity::class.java).apply {
                putExtra("BOOK_SHA1", bookSha1)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadBookData()
    }

    private fun loadBookData() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@BookDetailActivity)
            val book = withContext(Dispatchers.IO) {
                db.bookDao().getBookBySha1(bookSha1!!)
            }

            if (book != null) {
                tvTitle.text = book.title
                tvAuthor.text = book.author ?: "Неизвестен"
                
                if (!book.series.isNullOrEmpty()) {
                    tvSeries.visibility = View.VISIBLE
                    val indexText = if (book.seriesIndex != null && book.seriesIndex > 0) " (#${book.seriesIndex})" else ""
                    tvSeries.text = "Серия: ${book.series}$indexText"
                } else {
                    tvSeries.visibility = View.GONE
                }

                tvAnnotation.text = if (book.content.length > 500) book.content.take(500) + "..." else book.content

                val percent = if (book.totalCharacters > 0) {
                    ((book.currentProgressChar.toFloat() / book.totalCharacters) * 100).toInt().coerceIn(0, 100)
                } else {
                    0
                }
                pbProgress.progress = percent
                tvProgress.text = "$percent%"

                // Cover gradient
                val colors = intArrayOf(
                    Color.parseColor(book.coverGradientStart),
                    Color.parseColor(book.coverGradientEnd)
                )
                val gradient = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
                vCoverBackground.background = gradient

            } else {
                finish()
            }
        }
    }
}
