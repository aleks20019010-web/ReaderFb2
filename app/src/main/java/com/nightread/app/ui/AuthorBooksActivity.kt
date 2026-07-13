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
import com.nightread.app.data.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthorBooksActivity : BaseActivity() {

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
