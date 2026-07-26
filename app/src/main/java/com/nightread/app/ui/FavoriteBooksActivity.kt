package com.nightread.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FavoriteBooksActivity : BaseActivity() {

    private lateinit var rvBooks: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: BookAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorite_books)

        findViewById<StarryNightView>(R.id.starryOverlay)?.transparentBackground = true

        // Edge-to-Edge support
        val rootLayout = findViewById<View>(R.id.rootFavoriteBooks)
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

        // ✅ КНОПКА МЕНЮ (гамбургер) — открывает боковое меню
        val btnMenu = findViewById<ImageButton>(R.id.btnMenu)
        btnMenu.setOnClickListener {
            (this as? com.nightread.app.MainActivity)?.openDrawer()
        }

        // Инициализация списка
        rvBooks = findViewById(R.id.rvBooks)
        tvEmpty = findViewById(R.id.tvEmpty)
        
        rvBooks.layoutManager = GridLayoutManager(this, 3)

        adapter = BookAdapter(
            books = emptyList(),
            onOpenBook = { book, coverView ->
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
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(this@FavoriteBooksActivity)
                    db.bookDao().updateFavorite(book.sha1, false)
                }
            }
        )
        rvBooks.adapter = adapter

        loadFavoriteBooks()
    }

    private fun loadFavoriteBooks() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@FavoriteBooksActivity)
            db.bookDao().getFavoritesBooks().collect { books ->
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
