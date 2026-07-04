package com.example.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.AppDatabase
import kotlinx.coroutines.launch

class SeriesBooksActivity : AppCompatActivity() {

    private lateinit var rvBooks: RecyclerView
    private lateinit var adapter: BookAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series_books)

        val seriesName = intent.getStringExtra("SERIES_NAME") ?: "Неизвестная"

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = seriesName
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        rvBooks = findViewById(R.id.rvBooks)
        rvBooks.layoutManager = LinearLayoutManager(this)

        adapter = BookAdapter(emptyList(), { book ->
            val intent = Intent(this, BookDetailActivity::class.java).apply {
                putExtra("BOOK_SHA1", book.sha1)
            }
            startActivity(intent)
        }, {})
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
