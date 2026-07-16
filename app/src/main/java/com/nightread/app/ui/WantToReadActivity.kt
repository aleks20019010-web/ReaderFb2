package com.nightread.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
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

        // Support Edge-to-Edge immersion and safe areas (Status Bar + Notch + 12dp spacing)
        val rootLayout = findViewById<android.view.View>(R.id.rootWantToRead)
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
        supportActionBar?.title = "Хочу прочитать"

        rvWantToRead = findViewById(R.id.rvWantToRead)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        
        rvWantToRead.layoutManager = GridLayoutManager(this, 3)

        adapter = BookAdapter(
            books = emptyList(),
            onOpenBook = { book, coverView ->
                android.util.Log.d("WantToReadActivity", "Opening BookDetailActivity for SHA1: ${book.sha1}")
                val intent = Intent(this, BookDetailActivity::class.java).apply {
                    putExtra("BOOK_SHA1", book.sha1)
                }
                val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                    this,
                    coverView,
                    "cover_${book.sha1}"
                )
                startActivity(intent, options.toBundle())
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

        val onBackPressedCallback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (intent.getBooleanExtra("from_menu", false)) {
                    val mainIntent = Intent(this@WantToReadActivity, com.nightread.app.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("OPEN_DRAWER", true)
                    }
                    startActivity(mainIntent)
                    finish()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

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
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
