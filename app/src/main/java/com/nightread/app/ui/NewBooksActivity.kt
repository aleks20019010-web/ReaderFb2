package com.nightread.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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

        // Support Edge-to-Edge immersion and safe areas (Status Bar + Notch + 12dp spacing)
        val rootLayout = findViewById<android.view.View>(R.id.rootNewBooks)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.displayCutout()
            )
            val topPadding = insets.top + (12 * resources.displayMetrics.density).toInt()
            val bottomPadding = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(0, topPadding, 0, bottomPadding)
            windowInsets
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Новые поступления"

        rvNewBooks = findViewById(R.id.rvNewBooks)
        rvNewBooks.layoutManager = GridLayoutManager(this, 3)

        adapter = BookAdapter(
            books = emptyList(),
            onOpenBook = { book ->
                android.util.Log.d("NewBooksActivity", "Opening BookDetailActivity for SHA1: ${book.sha1}")
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
            val recentlyAdded = withContext(Dispatchers.IO) {
                db.bookDao().getRecentlyAddedBooks()
            }
            adapter.updateData(recentlyAdded)
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
