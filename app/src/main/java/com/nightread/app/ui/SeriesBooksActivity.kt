package com.nightread.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import kotlinx.coroutines.launch

class SeriesBooksActivity : BaseActivity() {

    private lateinit var rvBooks: RecyclerView
    private lateinit var adapter: BookAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series_books)

        findViewById<StarryNightView>(R.id.starryOverlay)?.transparentBackground = true

        val seriesName = intent.getStringExtra("SERIES_NAME") ?: "Неизвестная"

        val glassHeader = findViewById<android.view.View>(R.id.glassHeader)
        glassHeader.findViewById<android.widget.TextView>(R.id.header_title).text = seriesName
        val btnLeft = glassHeader.findViewById<android.widget.ImageButton>(R.id.header_btn_left)
        btnLeft.setImageResource(R.drawable.ic_arrow_back)
        btnLeft.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        rvBooks = findViewById(R.id.rvBooks)
        rvBooks.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 3)

        adapter = BookAdapter(emptyList(), { book, coverView ->
            val intent = Intent(this, BookDetailActivity::class.java).apply {
                putExtra("BOOK_SHA1", book.sha1)
            }
            val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                this,
                coverView,
                "cover_${book.sha1}"
            )
            startActivity(intent, options.toBundle())
        })
        rvBooks.adapter = adapter

        loadBooks(seriesName)
    }

    private fun loadBooks(series: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@SeriesBooksActivity)
            db.bookDao().getBooksBySeries(series).collect { books ->
                adapter.updateData(books)
            }
        }
    }
}
