package com.nightread.app

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.animation.AnimatorSet
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ProgressBar
import com.nightread.app.ui.BookReaderActivity
import com.nightread.app.data.SettingsManager
import com.nightread.app.ui.BaseActivity
import com.nightread.app.ui.BookmarksFragment
import com.nightread.app.ui.YandexSyncFragment
import com.nightread.app.ui.LibraryFragment
import com.nightread.app.ui.CustomToast
import android.widget.Toast
import com.nightread.app.ui.FontUtils
import com.nightread.app.ui.TextFormatter
import com.nightread.app.ui.BookCache
import com.nightread.app.service.BookParser
import com.google.android.material.navigation.NavigationView
import android.graphics.Color
import android.text.TextPaint
import android.widget.TextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.DecelerateInterpolator
import java.io.File

class MainActivity : BaseActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView

    private var isMainUiInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Theme and super setup, then immediate splash view
        com.nightread.app.data.ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }

        // Show splash screen instantly in Dark Glassmorphism style
        val nightMode = androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode()
        val isNightMode = when (nightMode) {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES -> true
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> com.nightread.app.data.ThemeHelper.shouldBeNightMode(this)
        }

        if (hasShownSplash) {
            setContentView(R.layout.activity_main)
            initMainUI(savedInstanceState)
        } else {
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#0F1523")))
            window.statusBarColor = Color.parseColor("#0F1523")
            setContentView(R.layout.activity_splash)
            hasShownSplash = true
            isSplashActive = true
            runSplashAndLoadData(savedInstanceState, isNightMode)
        }
    }

    private fun runSplashAndLoadData(savedInstanceState: Bundle?, isNightMode: Boolean) {
        // Ensure dark starry background and hide sunbeam overlay during splash
        val splashBgRoot = findViewById<android.view.View>(R.id.splash_starry_bg)
        val starryBg = splashBgRoot?.findViewById<com.nightread.app.ui.StarryNightView>(R.id.starryOverlay)
        val sunbeamBg = splashBgRoot?.findViewById<android.view.View>(R.id.sunbeamOverlay)
        sunbeamBg?.visibility = View.GONE
        starryBg?.visibility = View.VISIBLE
        starryBg?.setFireflyThemeColor(Color.parseColor("#FFE3A8"))

        runSplashAnimation()

        val preventAutoOpen = intent.getBooleanExtra("PREVENT_AUTO_OPEN", false)
        var lastReadBookSha1: String? = null
        var shouldAutoOpen = false

        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()

            // 2. Load last read book info with safe timeout
            val taskLoadBooks = async(Dispatchers.IO) {
                if (!preventAutoOpen && !hasAutoOpenedInSession) {
                    try {
                        val progressMgr = com.nightread.app.data.SafeProgressManager.getInstance(this@MainActivity)
                        val lastBookId = progressMgr.getLastOpenedBookId()
                        val spSha1 = lastBookId ?: com.nightread.app.data.SettingsManager.getLastReadBookSha1(this@MainActivity)
                        val db = com.nightread.app.data.AppDatabase.getDatabase(this@MainActivity)
                        val dbLastRead = db.bookDao().getLastReadBook()
                        val spBook = if (!spSha1.isNullOrEmpty()) db.bookDao().getBookBySha1(spSha1) else null

                        val candidate = spBook ?: dbLastRead
                        if (candidate != null && !candidate.sha1.isNullOrEmpty()) {
                            lastReadBookSha1 = candidate.sha1
                            shouldAutoOpen = true
                            hasAutoOpenedInSession = true
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }

            try {
                withTimeoutOrNull(1500L) {
                    taskLoadBooks.await()
                }
            } catch (e: Exception) {
                // Ignore
            }

            // Ensure minimum splash duration of 1200ms for smooth UX animation
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 1200L) {
                delay(1200L - elapsed)
            }

            if (shouldAutoOpen && lastReadBookSha1 != null) {
                isSplashActive = false
                val openIntent = Intent(this@MainActivity, com.nightread.app.ui.BookReaderActivity::class.java).apply {
                    putExtra("BOOK_SHA1", lastReadBookSha1)
                    putExtra("FROM_SPLASH", true)
                }
                startActivity(openIntent)
                overridePendingTransition(0, 0)
            } else {
                // 3. Apply active theme to window and set main layout with smooth fade in
                isSplashActive = false
                if (isNightMode) {
                    window.setBackgroundDrawable(com.nightread.app.ui.StarryNightDrawable())
                    window.statusBarColor = Color.TRANSPARENT
                } else {
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor(com.nightread.app.ui.GalaxyBgHelper.LIGHT_BG_COLOR)))
                    window.statusBarColor = Color.parseColor(com.nightread.app.ui.GalaxyBgHelper.LIGHT_BG_COLOR)
                }
                setContentView(R.layout.activity_main)
                val mainRoot = findViewById<View>(R.id.drawer_layout) ?: findViewById<View>(R.id.fragment_container)
                mainRoot?.alpha = 0f
                mainRoot?.animate()?.alpha(1f)?.setDuration(300)?.start()
                initMainUI(savedInstanceState)
            }

            // 4. Run heavy tasks (library scan & cloud sync) in background AFTER main UI is shown
            launch(Dispatchers.IO) {
                try {
                    if (hasStoragePermission()) {
                        withContext(Dispatchers.Main) {
                            val bookViewModel = androidx.lifecycle.ViewModelProvider(this@MainActivity).get(com.nightread.app.ui.BookViewModel::class.java)
                            bookViewModel.startIncrementalBookScan()
                        }
                    }
                    if (com.nightread.app.data.SettingsManager.isAutoSyncEnabled(this@MainActivity)) {
                        com.nightread.app.service.AutoSyncScheduler.scheduleAutoSync(this@MainActivity, forceReplace = false)
                    }
                } catch (e: Exception) {
                    // Ignore background sync errors
                }
            }
        }
    }

    private fun runSplashAnimation() {
        val tvSplashTitle = findViewById<TextView>(R.id.tv_splash_title)
        val tvSplashSubtitle = findViewById<TextView>(R.id.tv_splash_subtitle)
        val cardMood = findViewById<View>(R.id.card_mood)
        val layoutGlowingIcon = findViewById<View>(R.id.layout_glowing_icon)
        val viewIconGlow = findViewById<View>(R.id.view_icon_glow)
        val ivLogo = findViewById<ImageView>(R.id.iv_splash_logo)
        val layoutSplashLoading = findViewById<LinearLayout>(R.id.layout_splash_loading)

        viewIconGlow?.let { glow ->
            val scaleXAnimator = ObjectAnimator.ofFloat(glow, "scaleX", 1.0f, 1.25f).apply {
                duration = 2000
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleYAnimator = ObjectAnimator.ofFloat(glow, "scaleY", 1.0f, 1.25f).apply {
                duration = 2000
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }
            val alphaAnimator = ObjectAnimator.ofFloat(glow, "alpha", 0.18f, 0.45f).apply {
                duration = 2000
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }
            AnimatorSet().apply {
                playTogether(scaleXAnimator, scaleYAnimator, alphaAnimator)
                start()
            }
        }

        layoutGlowingIcon?.let { iconContainer ->
            ObjectAnimator.ofFloat(iconContainer, "translationY", -12f, 12f).apply {
                duration = 2500
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }

        ivLogo?.let { logo ->
            ObjectAnimator.ofFloat(logo, "rotation", 0f, 360f).apply {
                duration = 32000
                repeatCount = ValueAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                start()
            }
        }

        tvSplashTitle?.alpha = 0f
        tvSplashTitle?.translationY = -30f
        tvSplashSubtitle?.alpha = 0f
        tvSplashSubtitle?.translationY = -30f
        cardMood?.alpha = 0f
        cardMood?.scaleX = 0.88f
        cardMood?.scaleY = 0.88f
        layoutSplashLoading?.alpha = 0f
        layoutSplashLoading?.translationY = 40f

        tvSplashTitle?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(800)?.setInterpolator(DecelerateInterpolator())?.start()
        tvSplashSubtitle?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(800)?.setStartDelay(150)?.setInterpolator(DecelerateInterpolator())?.start()
        cardMood?.animate()?.alpha(1f)?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(950)?.setStartDelay(300)?.setInterpolator(OvershootInterpolator(1.1f))?.start()
        layoutSplashLoading?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(850)?.setStartDelay(750)?.setInterpolator(OvershootInterpolator(1.2f))?.start()
    }

    private fun initMainUI(savedInstanceState: Bundle?) {
        if (isMainUiInitialized) return
        isMainUiInitialized = true

        lifecycleScope.launch(Dispatchers.IO) {
            com.nightread.app.ui.HyphenatorHelper.init(this@MainActivity)
        }

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        navView.itemIconTintList = null

        val starryBg = findViewById<android.view.View>(R.id.starry_bg)?.findViewById<com.nightread.app.ui.StarryNightView>(R.id.starryOverlay)
        val sunbeamBg = findViewById<android.view.View>(R.id.starry_bg)?.findViewById<com.nightread.app.ui.SunbeamParticlesView>(R.id.sunbeamOverlay)
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: android.view.View, slideOffset: Float) {
                starryBg?.setDrawerSlideOffset(slideOffset)
                sunbeamBg?.setDrawerSlideOffset(slideOffset)
                drawerView.alpha = 0.2f + 0.8f * slideOffset
                val innerLayout = drawerView.findViewById<android.view.View>(R.id.nav_view)?.parent as? android.view.View
                innerLayout?.alpha = slideOffset
                innerLayout?.translationX = -40f * (1f - slideOffset)
            }
            override fun onDrawerOpened(drawerView: android.view.View) {
                drawerView.alpha = 1f
                val innerLayout = drawerView.findViewById<android.view.View>(R.id.nav_view)?.parent as? android.view.View
                innerLayout?.alpha = 1f
                innerLayout?.translationX = 0f
            }
            override fun onDrawerClosed(drawerView: android.view.View) {
                drawerView.alpha = 0.2f
            }
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
        val drawerLayoutContainer = findViewById<FrameLayout>(R.id.drawer_layout_container)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            fragmentContainer.setPadding(0, insets.top, 0, insets.bottom)
            drawerLayoutContainer?.setPadding(0, insets.top, 0, insets.bottom)
            windowInsets
        }

        findViewById<View>(R.id.btn_searchfloor)?.setOnClickListener {
            val url = "https://searchfloor.org"
            try {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                startActivity(intent)
            } catch (e: Exception) {
                CustomToast.show(this, "Не удалось открыть браузер", Toast.LENGTH_SHORT)
            }
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_audiobooks -> openAudiobooksFragment()
                R.id.nav_sync -> openSyncFragment()
                R.id.nav_stats -> openStatsFragment()
                R.id.nav_favorites -> openFavoritesFragment()
                R.id.nav_new_books -> openNewBooksFragment()
                R.id.nav_want_to_read -> openWantToReadFragment()
                R.id.nav_settings -> openSettingsFragment()
                else -> {
                    val filter = when (menuItem.itemId) {
                        R.id.nav_reading -> "reading"
                        R.id.nav_read -> "read"
                        else -> "all"
                    }
                    getSharedPreferences("nav_prefs", MODE_PRIVATE).edit()
                        .putString("last_selected_filter", filter)
                        .apply()
                    openLibraryFragment(filter)
                }
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

            if (intent.getBooleanExtra("OPEN_AUDIOBOOKS", false)) {
                openAudiobooksFragment()
                navView.setCheckedItem(R.id.nav_audiobooks)
            } else if (intent.getBooleanExtra("OPEN_SYNC", false)) {
                openSyncFragment()
                navView.setCheckedItem(R.id.nav_sync)
            } else {
                openLibraryFragment(lastFilter)
            }
        }

        if (com.nightread.app.data.SyncSettingsManager.wasInterrupted(this)) {
            com.nightread.app.data.SyncSettingsManager.setInterruptedFlag(this, false)
            CustomToast.show(this, "Предыдущая фоновая синхронизация была прервана")
        }

        handleIncomingBookIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (!isSplashActive && !isMainUiInitialized) {
            setContentView(R.layout.activity_main)
            initMainUI(null)
        }
        com.nightread.app.service.ReminderWorker.updateLastOpenTime(this)
        com.nightread.app.service.ReminderWorker.schedule(this)
    }

    override fun onPause() {
        super.onPause()
    }

    fun openLibraryFragment(filterType: String = "all") {
        val fragment = com.nightread.app.ui.LibraryFragment().apply {
            arguments = android.os.Bundle().apply {
                putString("FILTER_TYPE", filterType)
            }
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun openAudiobooksFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, com.nightread.app.ui.AudiobooksFragment())
            .commit()
    }

    fun openSyncFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, com.nightread.app.ui.YandexSyncFragment())
            .commit()
    }

    fun openStatsFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, com.nightread.app.ui.StatsFragment())
            .commit()
    }

    fun openFavoritesFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, com.nightread.app.ui.FavoriteBooksFragment())
            .commit()
    }

    fun openNewBooksFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, com.nightread.app.ui.NewBooksFragment())
            .commit()
    }

    fun openWantToReadFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, com.nightread.app.ui.WantToReadFragment())
            .commit()
    }

    fun openSettingsFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, com.nightread.app.ui.SettingsFragment())
            .commit()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::drawerLayout.isInitialized && intent.getBooleanExtra("OPEN_DRAWER", false)) {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }
        handleIncomingBookIntent(intent)
    }

    private fun handleIncomingBookIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val uri = intent.data
        if (Intent.ACTION_VIEW == action && uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val rootDir = android.os.Environment.getExternalStorageDirectory()
                    var booksDir = File(rootDir, "Books")
                    if (!booksDir.exists()) {
                        val created = booksDir.mkdirs()
                        if (!created) {
                            val extFiles = getExternalFilesDir(null)
                            booksDir = File(extFiles ?: filesDir, "Books").apply { if (!exists()) mkdirs() }
                        }
                    }

                    var fileName = "imported_book"
                    val cursor = contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                val name = it.getString(nameIndex)
                                if (!name.isNullOrEmpty()) fileName = name
                            }
                        }
                    }
                    if (fileName == "imported_book" && uri.path != null) {
                        val pathName = File(uri.path!!).name
                        if (pathName.isNotBlank()) fileName = pathName
                    }

                    val targetFile = File(booksDir, fileName)
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        java.io.FileOutputStream(targetFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    val fileUri = android.net.Uri.fromFile(targetFile)
                    withContext(Dispatchers.Main) {
                        val bookViewModel = androidx.lifecycle.ViewModelProvider(this@MainActivity).get(com.nightread.app.ui.BookViewModel::class.java)
                        bookViewModel.importBookFromUri(fileUri, this@MainActivity) { success, message ->
                            if (success) {
                                CustomToast.show(this@MainActivity, "Книга \"$fileName\" успешно импортирована!", Toast.LENGTH_SHORT)
                            } else {
                                CustomToast.show(this@MainActivity, message, Toast.LENGTH_SHORT)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error importing book from intent", e)
                    withContext(Dispatchers.Main) {
                        CustomToast.show(this@MainActivity, "Ошибка импорта книги: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    fun openDrawer() {
        if (::drawerLayout.isInitialized) {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        var isSplashActive = true
        var hasShownSplash = false
        var hasAutoOpenedInSession = false
    }
}
