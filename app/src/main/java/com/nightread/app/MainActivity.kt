package com.nightread.app

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.nightread.app.data.SettingsManager
import com.nightread.app.ui.BaseActivity
import com.nightread.app.ui.BookmarksFragment
import com.nightread.app.ui.YandexSyncFragment
import com.nightread.app.ui.LibraryFragment
import com.nightread.app.ui.CustomToast
import com.google.android.material.navigation.NavigationView

class MainActivity : BaseActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        val starryBg = findViewById<com.nightread.app.ui.StarryNightView>(R.id.starry_bg)
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: android.view.View, slideOffset: Float) {
                starryBg?.setDrawerSlideOffset(slideOffset)
            }
            override fun onDrawerOpened(drawerView: android.view.View) {}
            override fun onDrawerClosed(drawerView: android.view.View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        // Handle back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                    if (currentFragment !is LibraryFragment) {
                        openLibraryFragment("all")
                        navView.setCheckedItem(R.id.nav_library)
                    } else {
                        finish()
                    }
                }
            }
        })

        // Handle WindowInsets for Edge-to-Edge
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val fragmentContainer = findViewById<FrameLayout>(R.id.fragment_container)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            // Apply top and bottom padding to the main content container
            fragmentContainer.setPadding(0, insets.top, 0, insets.bottom)
            // Apply top padding to the NavigationView
            navView.setPadding(0, insets.top, 0, insets.bottom)
            windowInsets
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            if (menuItem.itemId == R.id.nav_sync) {
                openSyncFragment()
            } else if (menuItem.itemId == R.id.nav_stats) {
                openStatsFragment()
            } else if (menuItem.itemId == R.id.nav_bookmarks) {
                openBookmarksFragment()
            } else if (menuItem.itemId == R.id.nav_favorites) {
                val intent = Intent(this, com.nightread.app.ui.FavoriteBooksActivity::class.java)
                startActivity(intent)
            } else if (menuItem.itemId == R.id.nav_new_books) {
                val intent = Intent(this, com.nightread.app.ui.NewBooksActivity::class.java).apply {
                    putExtra("from_menu", true)
                }
                startActivity(intent)
            } else if (menuItem.itemId == R.id.nav_want_to_read) {
                val intent = Intent(this, com.nightread.app.ui.WantToReadActivity::class.java).apply {
                    putExtra("from_menu", true)
                }
                startActivity(intent)
            } else if (menuItem.itemId == R.id.nav_settings) {
                // Open SettingsActivity
                val intent = Intent(this, com.nightread.app.ui.SettingsActivity::class.java)
                startActivity(intent)
            } else {
                val filter = when (menuItem.itemId) {
                    R.id.nav_reading -> "reading"
                    R.id.nav_read -> "read"
                    else -> "all"
                }
                
                // Save selected menu in preferences
                getSharedPreferences("nav_prefs", MODE_PRIVATE).edit()
                    .putString("last_selected_filter", filter)
                    .apply()
                    
                openLibraryFragment(filter)
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        if (savedInstanceState == null) {
            val lastFilter = getSharedPreferences("nav_prefs", MODE_PRIVATE)
                .getString("last_selected_filter", "all") ?: "all"
                
            val menuId = when (lastFilter) {
                "reading" -> R.id.nav_reading
                "read" -> R.id.nav_read
                else -> R.id.nav_library
            }
            navView.setCheckedItem(menuId)
            
            if (intent.getBooleanExtra("OPEN_SYNC", false)) {
                openSyncFragment()
                navView.setCheckedItem(R.id.nav_sync)
            } else {
                openLibraryFragment(lastFilter)
            }

            // Check if there is a last read book and immediately launch ReadingActivity
            val preventAutoOpen = intent.getBooleanExtra("PREVENT_AUTO_OPEN", false)
            val lastReadSha1 = SettingsManager.getLastReadBookSha1(this)
            if (!lastReadSha1.isNullOrEmpty() && !preventAutoOpen) {
                lifecycleScope.launch {
                    val db = com.nightread.app.data.AppDatabase.getDatabase(this@MainActivity)
                    val repository = com.nightread.app.data.BookRepository(db.bookDao(), db.noteDao())
                    val book = repository.getBookBySha1(lastReadSha1)
                    if (book != null) {
                        val percent = if (book.totalCharacters > 0) {
                            ((book.currentProgressChar.toFloat() / book.totalCharacters) * 100).toInt()
                        } else {
                            0
                        }
                        if (percent < 100) {
                            android.util.Log.d("MainActivity", "Auto-opening last read book on startup: SHA1=$lastReadSha1")
                            val openIntent = Intent(this@MainActivity, com.nightread.app.ui.ReadingActivity::class.java).apply {
                                putExtra("BOOK_SHA1", lastReadSha1)
                            }
                            startActivity(openIntent)
                        }
                    }
                }
            }
        }

        // Inform user if previous sync was interrupted
        if (com.nightread.app.data.SyncSettingsManager.wasInterrupted(this)) {
            com.nightread.app.data.SyncSettingsManager.setInterruptedFlag(this, false)
            CustomToast.show(this, "Предыдущая фоновая синхронизация была прервана")
        }
    }

    private fun openLibraryFragment(filter: String) {
        val fragment = LibraryFragment.newInstance(filter)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun openSyncFragment() {
        val fragment = com.nightread.app.ui.YandexSyncFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun openBookmarksFragment() {
        val fragment = com.nightread.app.ui.BookmarksFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun openStatsFragment() {
        val fragment = com.nightread.app.ui.StatsFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }


    fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START)
    }

    override fun onResume() {
        super.onResume()
        if (intent.getBooleanExtra("OPEN_DRAWER", false)) {
            intent.removeExtra("OPEN_DRAWER")
            openDrawer()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("OPEN_DRAWER", false)) {
            openDrawer()
        }
    }
}
