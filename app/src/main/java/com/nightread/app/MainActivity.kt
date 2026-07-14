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
import android.graphics.Color
import android.widget.TextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Button
import android.view.View
import android.content.res.ColorStateList
import android.view.animation.OvershootInterpolator
import android.view.animation.DecelerateInterpolator

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

        // Set up Splash Screen
        val splashOverlay = findViewById<FrameLayout>(R.id.splash_overlay)
        if (hasShownSplash) {
            splashOverlay?.visibility = android.view.View.GONE
        } else {
            hasShownSplash = true
            runSplashAnimation()
        }
    }

    private fun runSplashAnimation() {
        val splashOverlay = findViewById<FrameLayout>(R.id.splash_overlay) ?: return
        val tvSplashTitle = findViewById<TextView>(R.id.tv_splash_title)
        val tvSplashSubtitle = findViewById<TextView>(R.id.tv_splash_subtitle)
        val cardMood = findViewById<View>(R.id.card_mood)
        val layoutGlowingIcon = findViewById<View>(R.id.layout_glowing_icon)
        val viewIconGlow = findViewById<View>(R.id.view_icon_glow)
        val ivLogo = findViewById<ImageView>(R.id.iv_splash_logo)
        val tvMoodTitle = findViewById<TextView>(R.id.tv_mood_title)
        val tvMoodSubtitle = findViewById<TextView>(R.id.tv_mood_subtitle)
        val tvMoodQuote = findViewById<TextView>(R.id.tv_mood_quote)
        val btnEnterApp = findViewById<Button>(R.id.btn_enter_app)
        val starryBg = findViewById<com.nightread.app.ui.StarryNightView>(R.id.starry_bg)

        // Configure the background live particles to match our premium Golden accent initially
        starryBg?.setFireflyThemeColor(Color.parseColor("#FFE3A8"))

        // Add interactive premium splash touch! Tap on card or icon to shoot stars and trigger ring pulses
        val triggerInteraction = View.OnClickListener {
            // 1. Shake/Rotate the logo slightly with overshoot
            ivLogo?.clearAnimation()
            ivLogo?.animate()
                ?.rotation(360f)
                ?.scaleX(1.15f)
                ?.scaleY(1.15f)
                ?.setDuration(700)
                ?.setInterpolator(OvershootInterpolator(1.3f))
                ?.withEndAction {
                    ivLogo.rotation = 0f
                    ivLogo.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(200)?.start()
                }
                ?.start()

            // 2. Pulse the background glow
            viewIconGlow?.clearAnimation()
            viewIconGlow?.alpha = 0.6f
            viewIconGlow?.scaleX = 1.3f
            viewIconGlow?.scaleY = 1.3f
            viewIconGlow?.animate()
                ?.alpha(0.25f)
                ?.scaleX(1.0f)
                ?.scaleY(1.0f)
                ?.setDuration(600)
                ?.start()

            // 3. Trigger a shooting star in the live background!
            starryBg?.triggerShootingStar()
        }

        layoutGlowingIcon?.setOnClickListener(triggerInteraction)
        cardMood?.setOnClickListener(triggerInteraction)

        // Initial hidden states for a beautiful timed staggered reveal
        tvSplashTitle?.alpha = 0f
        tvSplashTitle?.translationY = -30f
        tvSplashSubtitle?.alpha = 0f
        tvSplashSubtitle?.translationY = -30f
        cardMood?.alpha = 0f
        cardMood?.scaleX = 0.88f
        cardMood?.scaleY = 0.88f
        btnEnterApp?.alpha = 0f
        btnEnterApp?.translationY = 40f

        // Staggered reveal animations
        tvSplashTitle?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(800)?.setInterpolator(DecelerateInterpolator())?.start()
        tvSplashSubtitle?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(800)?.setStartDelay(150)?.setInterpolator(DecelerateInterpolator())?.start()
        
        cardMood?.animate()?.alpha(1f)?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(950)?.setStartDelay(300)?.setInterpolator(OvershootInterpolator(1.1f))?.start()
        
        btnEnterApp?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(850)?.setStartDelay(750)?.setInterpolator(OvershootInterpolator(1.2f))?.start()

        // Transition from splash to primary app content
        btnEnterApp?.setOnClickListener {
            // Outward transition animation
            tvSplashTitle?.animate()?.alpha(0f)?.translationY(-60f)?.setDuration(550)?.start()
            tvSplashSubtitle?.animate()?.alpha(0f)?.translationY(-60f)?.setDuration(550)?.start()
            
            cardMood?.animate()?.alpha(0f)?.scaleX(0.92f)?.scaleY(0.92f)?.setDuration(550)?.start()
            btnEnterApp.animate()?.alpha(0f)?.scaleX(0.95f)?.scaleY(0.95f)?.setDuration(450)?.start()

            splashOverlay.animate()
                ?.alpha(0f)
                ?.scaleX(1.12f)
                ?.scaleY(1.12f)
                ?.setDuration(750)
                ?.setInterpolator(DecelerateInterpolator())
                ?.withEndAction {
                    splashOverlay.visibility = View.GONE
                }
                ?.start()
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

    companion object {
        private var hasShownSplash = false
    }
}
