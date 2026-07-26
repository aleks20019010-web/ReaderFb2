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

        findViewById<StarryNightView>(R.id.starryOverlay)?.transparentBackground = true

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

        val glassHeader = findViewById<android.view.View>(R.id.glassHeader)
        glassHeader.findViewById<android.widget.TextView>(R.id.header_title).text = "Новые поступления"
        val btnLeft = glassHeader.findViewById<android.widget.ImageButton>(R.id.header_btn_left)
        btnLeft.setImageResource(android.R.drawable.ic_menu_sort_by_size)
        btnLeft.setOnClickListener {
            val intent = Intent(this, com.nightread.app.MainActivity::class.java).apply {
                putExtra("OPEN_DRAWER", true)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }

        rvNewBooks = findViewById(R.id.rvNewBooks)
        rvNewBooks.layoutManager = GridLayoutManager(this, 3)

        adapter = BookAdapter(
            books = emptyList(),
            onOpenBook = { book, coverView ->
                android.util.Log.d("NewBooksActivity", "Opening BookDetailActivity for SHA1: ${book.sha1}")
                val intent = Intent(this, BookDetailActivity::class.java).apply {
                    putExtra("BOOK_SHA1", book.sha1)
                }
                val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                    this,
                    coverView,
                    "cover_${book.sha1}"
                )
                startActivity(intent, options.toBundle())
            }
        )
        rvNewBooks.adapter = adapter

        val onBackPressedCallback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (intent.getBooleanExtra("from_menu", false)) {
                    val mainIntent = Intent(this@NewBooksActivity, com.nightread.app.MainActivity::class.java).apply {
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
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
