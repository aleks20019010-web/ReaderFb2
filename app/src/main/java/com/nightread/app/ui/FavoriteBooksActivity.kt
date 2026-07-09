package com.nightread.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import kotlinx.coroutines.launch

class FavoriteBooksActivity : AppCompatActivity() {

    private lateinit var rvBooks: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: BookAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorite_books)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Избранное"
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        rvBooks = findViewById(R.id.rvBooks)
        tvEmpty = findViewById(R.id.tvEmpty)
        
        rvBooks.layoutManager = GridLayoutManager(this, 3)

        adapter = BookAdapter(emptyList(), { book ->
            val intent = Intent(this, BookDetailActivity::class.java).apply {
                putExtra("BOOK_SHA1", book.sha1)
            }
            startActivity(intent)
        })
        rvBooks.adapter = adapter

        loadFavoriteBooks()
    }

    private fun loadFavoriteBooks() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@FavoriteBooksActivity)
            db.bookDao().getFavoriteBooks().collect { books ->
                adapter.updateData(books)
                if (books.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    rvBooks.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    rvBooks.visibility = View.VISIBLE
                }
            }
        }
    }
}
