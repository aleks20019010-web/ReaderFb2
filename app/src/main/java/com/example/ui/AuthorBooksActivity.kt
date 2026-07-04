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
import com.example.data.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthorBooksActivity : AppCompatActivity() {

    private lateinit var rvBooks: RecyclerView
    private lateinit var adapter: BookAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_author_books)

        val authorName = intent.getStringExtra("AUTHOR_NAME") ?: "Неизвестен"

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = authorName
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

        loadBooks(authorName)
    }

    private fun loadBooks(author: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@AuthorBooksActivity)
            db.bookDao().getBooksByAuthor(author).collect { books ->
                adapter.updateData(books)
            }
        }
    }
}
