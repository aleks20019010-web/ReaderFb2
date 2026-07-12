package com.nightread.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WantToReadActivity : BaseActivity() {

    private lateinit var rvWantToRead: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var adapter: BookAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_want_to_read)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Хочу прочитать"

        rvWantToRead = findViewById(R.id.rvWantToRead)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        
        rvWantToRead.layoutManager = GridLayoutManager(this, 3)

        adapter = BookAdapter(
            books = emptyList(),
            onOpenBook = { book ->
                val intent = Intent(this, BookDetailActivity::class.java).apply {
                    putExtra("BOOK_SHA1", book.sha1)
                }
                startActivity(intent)
            },
            onDeleteBook = { book ->
                lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(this@WantToReadActivity)
                    withContext(Dispatchers.IO) {
                        db.bookDao().updateWantToRead(book.sha1, false)
                    }
                }
            }
        )
        rvWantToRead.adapter = adapter

        observeWantToReadBooks()
    }

    private fun observeWantToReadBooks() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@WantToReadActivity)
            db.bookDao().getWantToReadBooks().collectLatest { books ->
                adapter.updateData(books)
                if (books.isEmpty()) {
                    layoutEmptyState.visibility = View.VISIBLE
                    rvWantToRead.visibility = View.GONE
                } else {
                    layoutEmptyState.visibility = View.GONE
                    rvWantToRead.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (intent.getBooleanExtra("from_menu", false)) {
            val mainIntent = Intent(this, com.nightread.app.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("OPEN_DRAWER", true)
            }
            startActivity(mainIntent)
            finish()
        } else {
            super.onBackPressed()
        }
    }
}
