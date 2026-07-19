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
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull
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
import com.nightread.app.service.EpubParser
import com.nightread.app.service.Fb2Parser
import com.nightread.app.service.TxtParser
import com.google.android.material.navigation.NavigationView
import android.graphics.Color
import android.text.TextPaint
import android.widget.TextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Button
import android.view.View
import android.content.res.ColorStateList
import android.view.animation.OvershootInterpolator
import android.view.animation.DecelerateInterpolator
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import java.util.zip.ZipInputStream

class MainActivity : BaseActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        com.nightread.app.ui.customlayout.PageSplitter.init(this)
        com.nightread.app.ui.HyphenatorHelper.init(this)

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
        val drawerLayoutContainer = findViewById<LinearLayout>(R.id.drawer_layout_container)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            // Apply top and bottom padding to the main content container
            fragmentContainer.setPadding(0, insets.top, 0, insets.bottom)
            // Apply top and bottom padding to the Drawer Container
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
        }

        // Inform user if previous sync was interrupted
        if (com.nightread.app.data.SyncSettingsManager.wasInterrupted(this)) {
            com.nightread.app.data.SyncSettingsManager.setInterruptedFlag(this, false)
            CustomToast.show(this, "Предыдущая фоновая синхронизация была прервана")
        }

        // Set up Splash Screen
        val splashOverlay = findViewById<FrameLayout>(R.id.splash_overlay)
        
        // Remove test book if it was previously injected
        lifecycleScope.launch(Dispatchers.IO) {
            val db = com.nightread.app.data.AppDatabase.getDatabase(this@MainActivity)
            try {
                db.bookDao().deleteBookBySha1("test_book_1")
            } catch (e: Exception) {
                // Ignore
            }
        }

        if (hasShownSplash) {
            splashOverlay?.visibility = android.view.View.GONE
            isSplashActive = false
        } else {
            hasShownSplash = true
            isSplashActive = true
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
        val layoutSplashLoading = findViewById<LinearLayout>(R.id.layout_splash_loading)
        val tvSplashLoadingStatus = findViewById<TextView>(R.id.tv_splash_loading_status)
        val pbSplashLoading = findViewById<ProgressBar>(R.id.pb_splash_loading)
        val starryBg = findViewById<com.nightread.app.ui.StarryNightView>(R.id.starry_bg)
        val splashStarryBg = findViewById<com.nightread.app.ui.StarryNightView>(R.id.splash_starry_bg)

        // Configure the background live particles to match our premium Golden accent initially
        starryBg?.setFireflyThemeColor(Color.parseColor("#FFE3A8"))
        splashStarryBg?.setFireflyThemeColor(Color.parseColor("#FFE3A8"))

        // Infinite, hypnotic ambient animations matching the premium style
        // 1. Glow pulsation
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

        // 2. Slow breathing floating effect of the entire logo container
        layoutGlowingIcon?.let { iconContainer ->
            ObjectAnimator.ofFloat(iconContainer, "translationY", -12f, 12f).apply {
                duration = 2500
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }

        // 3. Gentle infinite rotation of the logo itself for cosmic movement
        ivLogo?.let { logo ->
            ObjectAnimator.ofFloat(logo, "rotation", 0f, 360f).apply {
                duration = 32000
                repeatCount = ValueAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                start()
            }
        }

        // Initial hidden states for a beautiful timed staggered reveal
        tvSplashTitle?.alpha = 0f
        tvSplashTitle?.translationY = -30f
        tvSplashSubtitle?.alpha = 0f
        tvSplashSubtitle?.translationY = -30f
        cardMood?.alpha = 0f
        cardMood?.scaleX = 0.88f
        cardMood?.scaleY = 0.88f
        layoutSplashLoading?.alpha = 0f
        layoutSplashLoading?.translationY = 40f

        // Staggered reveal animations
        tvSplashTitle?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(800)?.setInterpolator(DecelerateInterpolator())?.start()
        tvSplashSubtitle?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(800)?.setStartDelay(150)?.setInterpolator(DecelerateInterpolator())?.start()
        
        cardMood?.animate()?.alpha(1f)?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(950)?.setStartDelay(300)?.setInterpolator(OvershootInterpolator(1.1f))?.start()
        
        layoutSplashLoading?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(850)?.setStartDelay(750)?.setInterpolator(OvershootInterpolator(1.2f))?.start()

        // Background loading & pre-splitting configuration
        val splashStartTime = System.currentTimeMillis()
        var lastReadBookSha1: String? = null
        var shouldAutoOpen = false

        val preventAutoOpen = intent.getBooleanExtra("PREVENT_AUTO_OPEN", false)
        val lastReadSha1 = SettingsManager.getLastReadBookSha1(this)

        var isTransitionStarted = false
        fun proceedFromSplash() {
            if (isTransitionStarted) return
            isTransitionStarted = true

            if (shouldAutoOpen && lastReadBookSha1 != null) {
                val openIntent = Intent(this@MainActivity, com.nightread.app.ui.BookReaderActivity::class.java).apply {
                    putExtra("BOOK_SHA1", lastReadBookSha1)
                    putExtra("FROM_SPLASH", true)
                }
                startActivity(openIntent)
                overridePendingTransition(0, 0)
            } else {
                tvSplashTitle?.animate()?.alpha(0f)?.translationY(-60f)?.setDuration(550)?.start()
                tvSplashSubtitle?.animate()?.alpha(0f)?.translationY(-60f)?.setDuration(550)?.start()
                cardMood?.animate()?.alpha(0f)?.scaleX(0.92f)?.scaleY(0.92f)?.setDuration(550)?.start()
                layoutSplashLoading?.animate()?.alpha(0f)?.scaleX(0.95f)?.scaleY(0.95f)?.setDuration(450)?.start()

                splashOverlay.animate()
                    ?.alpha(0f)
                    ?.scaleX(1.12f)
                    ?.scaleY(1.12f)
                    ?.setDuration(750)
                    ?.setInterpolator(DecelerateInterpolator())
                    ?.withEndAction {
                        splashOverlay.visibility = View.GONE
                        isSplashActive = false
                    }
                    ?.start()
            }
        }

        // Helper to update progress on the main thread safely
        fun updateLoadingProgress(progress: Int, statusText: String) {
            runOnUiThread {
                tvSplashLoadingStatus?.text = statusText
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    pbSplashLoading?.setProgress(progress, true)
                } else {
                    pbSplashLoading?.progress = progress
                }
            }
        }

        // Start background pre-processing as soon as layout size is known
        splashOverlay.post {
            val width = splashOverlay.width
            val height = splashOverlay.height
            if (width <= 0 || height <= 0) return@post

            lifecycleScope.launch {
                val bookViewModel = androidx.lifecycle.ViewModelProvider(this@MainActivity).get(com.nightread.app.ui.BookViewModel::class.java)

                // 1. Run background incremental scanning if storage permission is granted
                updateLoadingProgress(10, "Инициализация библиотечной базы...")
                delay(300) // Small warm up delay

                var isScanningTriggered = false
                if (hasStoragePermission()) {
                    isScanningTriggered = true
                    android.util.Log.d("MainActivity", "Storage permissions granted, starting background scanner on splash")
                    updateLoadingProgress(25, "Поиск новых книг в памяти...")
                    bookViewModel.startIncrementalBookScan()
                } else {
                    // Smoothly animate progress bar to make the entrance look fluid and premium
                    for (p in 25..45 step 2) {
                        updateLoadingProgress(p, "Подготовка космической полки...")
                        delay(50)
                    }
                }

                // 2. Prepare/pre-split last read book if available
                if (!lastReadSha1.isNullOrEmpty() && !preventAutoOpen) {
                    val db = com.nightread.app.data.AppDatabase.getDatabase(this@MainActivity)
                    val book = db.bookDao().getBookBySha1(lastReadSha1)
                    if (book != null) {
                        val percent = if (book.totalCharacters > 0) {
                            ((book.currentProgressChar.toFloat() / book.totalCharacters) * 100).toInt()
                        } else {
                            0
                        }
                        lastReadBookSha1 = lastReadSha1
                        shouldAutoOpen = true
                    }
                }

                // 3. Warm up the database and book list stream
                try {
                    updateLoadingProgress(45, "Обновление книжной полки...")
                    bookViewModel.allBooks.firstOrNull()
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Database warm up failed in background", e)
                }

                // 4. Await background scan completion (up to a 4s total timeout to prevent hanging)
                if (isScanningTriggered) {
                    val scanTimeoutLimit = splashStartTime + 4000L
                    while (bookViewModel.scanState.value.isScanning && System.currentTimeMillis() < scanTimeoutLimit) {
                        val state = bookViewModel.scanState.value
                        val scanProgress = if (state.totalFiles > 0) {
                            (state.processedFiles.toFloat() / state.totalFiles * 25).toInt()
                        } else {
                            0
                        }
                        updateLoadingProgress(45 + scanProgress, "Сканирование: ${state.processedFiles}/${state.totalFiles}...")
                        delay(200)
                    }
                    android.util.Log.d("MainActivity", "Splash book scanning completed or timed out. finalIsScanning=${bookViewModel.scanState.value.isScanning}")
                } else {
                    for (p in 46..70 step 2) {
                        updateLoadingProgress(p, "Загрузка книг...")
                        delay(40)
                    }
                }

                // 5. Precalculate and parse last book pages if needed
                if (shouldAutoOpen && lastReadBookSha1 != null) {
                    updateLoadingProgress(70, "Анализ структуры страниц книги...")
                    withContext(Dispatchers.Default) {
                        try {
                            val db = com.nightread.app.data.AppDatabase.getDatabase(this@MainActivity)
                            val book = db.bookDao().getBookBySha1(lastReadBookSha1!!) ?: return@withContext
                            val filePath = book.filePath ?: return@withContext
                            val file = File(filePath)
                            if (file.exists()) {
                                updateLoadingProgress(75, "Открытие книги: ${book.title ?: "Без названия"}...")
                                val parsedBook = parseBookFile(file)
                                val bookContent = parsedBook.content.trim().trim('\u000C').trim()
                                val bookNotes = parsedBook.notes
                                
                                if (bookContent.isNotEmpty()) {
                                    updateLoadingProgress(80, "Анализ структуры и переносов...")
                                    BookCache.sha1 = lastReadBookSha1!!
                                    BookCache.content = bookContent
                                    BookCache.notes = bookNotes
                                    
                                    val hyphenationEnabled = SettingsManager.isHyphenationEnabled(this@MainActivity)
                                    val textToSplit = if (hyphenationEnabled) {
                                        com.nightread.app.ui.HyphenatorHelper.hyphenate(bookContent, this@MainActivity)
                                    } else {
                                        bookContent
                                    }
                                    BookCache.hyphenatedContent = textToSplit
                                    BookCache.isHyphenated = hyphenationEnabled
                                    
                                    updateLoadingProgress(85, "Отрисовка и верстка страниц...")
                                    val paint = TextPaint().apply {
                                        textSize = SettingsManager.getFontSize(this@MainActivity) * resources.displayMetrics.scaledDensity
                                        val family = SettingsManager.getFontFamily(this@MainActivity)
                                        val numericWeight = SettingsManager.getFontWeightAsInt(this@MainActivity)
                                        typeface = FontUtils.createTypeface(this@MainActivity, family, numericWeight)
                                    }
                                    
                                    val paddingHorizontal = (32 * resources.displayMetrics.density).toInt()
                                    val paddingVertical = (24 * resources.displayMetrics.density).toInt() + getTopInset() + getBottomInset()
                                    
                                    val availableWidth = width - paddingHorizontal
                                    val availableHeight = height - paddingVertical
                                    
                                    val alignment = getSharedPreferences("reader_prefs", android.content.Context.MODE_PRIVATE).getString("saved_font_alignment", "justify") ?: "justify"
                                    val lineSpacing = SettingsManager.getLineSpacing(this@MainActivity)
                                    
                                    val formattedText = com.nightread.app.ui.TextFormatter.formatChapterSpans(this@MainActivity, textToSplit, paint.textSize)
                                    val builder = com.nightread.app.ui.customlayout.TextLayoutBuilder()
                                        .setText(formattedText)
                                        .setWidth(availableWidth)
                                        .setHeight(availableHeight)
                                        .setPaint(paint)
                                        .setLineSpacing(0f, lineSpacing)
                                        .setHyphenation(hyphenationEnabled)
                                        
                                    val offsets = builder.buildPagination()
                                    val pages = java.util.ArrayList<CharSequence>()
                                    for (i in offsets.indices) {
                                        val startIdx = offsets[i]
                                        val endIdx = if (i < offsets.size - 1) offsets[i + 1] else formattedText.length
                                        pages.add(formattedText.subSequence(startIdx, endIdx))
                                    }
                                    val splitResult = com.nightread.app.ui.TextFormatter.PageResult(pages, java.util.ArrayList(offsets), true)
                                    
                                    val currentKey = "${availableWidth}_${availableHeight}_${paint.textSize}_${SettingsManager.getFontFamily(this@MainActivity)}_${SettingsManager.getFontWeightAsInt(this@MainActivity)}_${lineSpacing}_align=${alignment}_hyphen=$hyphenationEnabled"
                                    BookCache.layoutKey = currentKey
                                    BookCache.splitResult = splitResult
                                    
                                    android.util.Log.d("MainActivity", "Precalculated splitting finished during splash. Total pages: ${splitResult.pages.size}")
                                    updateLoadingProgress(95, "Рассчитано страниц: ${splitResult.pages.size}. Открываем...")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Pre-splitting failed in splash background", e)
                        }
                    }
                } else {
                    // Smooth transition to 95% if not auto-opening
                    for (p in 71..95) {
                        updateLoadingProgress(p, "Завершение загрузки...")
                        delay(20)
                    }
                }

                // 6. Ensure the splash screen is visible for at least 1.5 seconds (1500ms)
                val elapsed = System.currentTimeMillis() - splashStartTime
                val remaining = 1500L - elapsed
                if (remaining > 0) {
                    val steps = (remaining / 50L).toInt().coerceAtLeast(1)
                    val stepIncrement = (100 - 95) / steps.toFloat()
                    var currentP = 95f
                    for (i in 0 until steps) {
                        currentP += stepIncrement
                        updateLoadingProgress(currentP.toInt().coerceAtMost(100), "Готово к чтению...")
                        delay(50L)
                    }
                } else {
                    updateLoadingProgress(100, "Готово к чтению...")
                }

                // Proceed with auto-open or library fade-out
                proceedFromSplash()
            }
        }
    }

    private fun getTopInset(): Int {
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView)
        if (insets != null) {
            val displayCutoutInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            val statusBarInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            return maxOf(statusBarInsets.top, displayCutoutInsets.top)
        }
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun getBottomInset(): Int {
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView)
        if (insets != null) {
            val navBarInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            return navBarInsets.bottom
        }
        return 0
    }

    private fun parseBookFile(file: File): BookParser.ParsedBook {
        val ext = file.extension.lowercase(Locale.ROOT)
        return when (ext) {
            "fb2", "xml" -> {
                Fb2Parser.parse(file, file.nameWithoutExtension)
            }
            "txt" -> {
                TxtParser.parse(file, file.nameWithoutExtension)
            }
            "epub" -> {
                EpubParser.parse(file, file.nameWithoutExtension)
            }
            "zip" -> {
                var parsed = BookParser.ParsedBook(file.nameWithoutExtension, "Неизвестен", "")
                try {
                    FileInputStream(file).use { fis ->
                        ZipInputStream(fis).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory && (entry.name.endsWith(".fb2") || entry.name.endsWith(".xml"))) {
                                    val tempFile = File.createTempFile("zip_fb2", ".fb2")
                                    tempFile.deleteOnExit()
                                    tempFile.outputStream().use { fos ->
                                        zis.copyTo(fos)
                                    }
                                    parsed = Fb2Parser.parse(tempFile, file.nameWithoutExtension)
                                    tempFile.delete()
                                    break
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error parsing zip in splash", e)
                }
                parsed
            }
            else -> BookParser.ParsedBook(file.nameWithoutExtension, "Неизвестен", "")
        }
    }

    private fun openLibraryFragment(filter: String) {
        val fragment = LibraryFragment.newInstance(filter)
        supportFragmentManager.beginTransaction()
            .setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun openSyncFragment() {
        val fragment = com.nightread.app.ui.YandexSyncFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun openBookmarksFragment() {
        val fragment = com.nightread.app.ui.BookmarksLibraryFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun openStatsFragment() {
        val fragment = com.nightread.app.ui.StatsFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE)
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

    override fun onStop() {
        super.onStop()
        val splashOverlay = findViewById<android.view.View>(R.id.splash_overlay)
        splashOverlay?.visibility = android.view.View.GONE
        isSplashActive = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("OPEN_DRAWER", false)) {
            openDrawer()
        }
    }

    private fun hasStoragePermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return try {
                android.os.Environment.isExternalStorageManager()
            } catch (e: Exception) {
                false
            }
        } else {
            return androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        var hasShownSplash = false
        var isSplashActive = true
    }
}
