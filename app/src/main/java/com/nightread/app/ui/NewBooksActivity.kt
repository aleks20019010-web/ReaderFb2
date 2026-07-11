package com.nightread.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NewBooksActivity : BaseActivity() {

    private lateinit var rvNewBooks: RecyclerView
    private lateinit var adapter: BookAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_books)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        rvNewBooks = findViewById(R.id.rvNewBooks)
        rvNewBooks.layoutManager = GridLayoutManager(this, 3)

        adapter = BookAdapter(
            books = emptyList(),
            onOpenBook = { book ->
                val intent = Intent(this, BookDetailActivity::class.java).apply {
                    putExtra("BOOK_SHA1", book.sha1)
                }
                startActivity(intent)
            }
        )
        rvNewBooks.adapter = adapter

        loadNewBooks()
    }

    private fun loadNewBooks() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@NewBooksActivity)
            val newBooks = withContext(Dispatchers.IO) {
                db.bookDao().getNewBooks()
            }
            adapter.updateData(newBooks)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear the new books flag when the activity is closed
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@NewBooksActivity)
            db.bookDao().clearNewBooksFlag()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
