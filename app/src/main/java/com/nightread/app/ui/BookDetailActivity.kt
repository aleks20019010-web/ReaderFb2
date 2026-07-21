package com.nightread.app.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.BookEntity
import com.nightread.app.data.SettingsManager
import com.nightread.app.data.GeminiClient
import com.nightread.app.data.GeminiRequest
import com.nightread.app.data.GeminiContent
import com.nightread.app.data.GeminiPart
import android.widget.ProgressBar
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookDetailActivity : BaseActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvAuthor: TextView
    private lateinit var tvSeries: TextView
    private lateinit var tvSeriesBanner: TextView
    private lateinit var tvAnnotation: TextView
    private lateinit var tvLanguage: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvFormatSize: TextView
    private lateinit var tvSha1: TextView
    private lateinit var tvReadMore: TextView

    private lateinit var ivCover: ImageView
    private lateinit var tvCoverLetter: TextView

    private lateinit var btnReadToolbar: TextView
    private lateinit var ivFavorite: ImageView
    private lateinit var ivWantToRead: ImageView
    private lateinit var ivShare: ImageView
    private lateinit var ivDelete: ImageView

    private lateinit var llFilesContainer: LinearLayout
    private lateinit var llAuthorContainer: LinearLayout
    private lateinit var llSeriesContainer: LinearLayout

    private var bookSha1: String? = null
    private var currentBook: BookEntity? = null
    private var isAnnotationExpanded = false
    private var currentVibrantColor: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_detail)

        // Handle WindowInsets for Edge-to-Edge immersion
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        val appBarLayout = toolbar.parent as? View
        val nestedScrollView = findViewById<View>(R.id.nestedScrollView)
        
        val rootView = findViewById<View>(android.R.id.content)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            // Bottom padding for content so it is not clipped by the navigation bar
            view.setPadding(0, 0, 0, insets.bottom)
            // Top padding for AppBarLayout/Toolbar to align buttons directly under status bar
            appBarLayout?.setPadding(0, insets.top, 0, 0)
            
            // Get action bar height dynamically to ensure correct spacing
            val tv = TypedValue()
            var actionBarHeight = 0
            if (theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
            }
            
            // Top padding for NestedScrollView so cover starts below the status bar and toolbar
            nestedScrollView?.setPadding(0, insets.top + actionBarHeight, 0, 0)
            windowInsets
        }

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { supportFinishAfterTransition() }

        // Bind views
        tvTitle = findViewById(R.id.tvTitle)
        tvAuthor = findViewById(R.id.tvAuthor)
        tvSeries = findViewById(R.id.tvSeries)
        tvSeriesBanner = findViewById(R.id.tvSeriesBanner)
        tvAnnotation = findViewById(R.id.tvAnnotation)
        tvReadMore = findViewById(R.id.tvReadMore)
        
        tvLanguage = findViewById(R.id.tvLanguage)
        tvProgress = findViewById(R.id.tvProgress)
        tvFormatSize = findViewById(R.id.tvFormatSize)
        tvSha1 = findViewById(R.id.tvSha1)

        ivCover = findViewById(R.id.ivCover)
        tvCoverLetter = findViewById(R.id.tvCoverLetter)

        btnReadToolbar = findViewById(R.id.btnReadToolbar)
        ivFavorite = findViewById(R.id.ivFavorite)
        ivWantToRead = findViewById(R.id.ivWantToRead)
        ivShare = findViewById(R.id.ivShare)
        ivDelete = findViewById(R.id.ivDelete)

        llFilesContainer = findViewById(R.id.llFilesContainer)
        llAuthorContainer = findViewById(R.id.llAuthorContainer)
        llSeriesContainer = findViewById(R.id.llSeriesContainer)

        bookSha1 = intent.getStringExtra("BOOK_SHA1")
        Log.d("BookDetailActivity", "onCreate: Opened BookDetailActivity with BOOK_SHA1 = $bookSha1")

        if (bookSha1.isNullOrEmpty()) {
            Log.e("BookDetailActivity", "onCreate: BOOK_SHA1 is null or empty! Finishing activity.")
            finish()
            return
        }
        
        ivCover.transitionName = "cover_$bookSha1"
        supportPostponeEnterTransition()

        tvReadMore.setOnClickListener {
            toggleAnnotation()
        }

        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        loadBookData()
    }

    private fun setupClickListeners() {
        btnReadToolbar.setOnClickListener {
            val intent = Intent(this, BookReaderActivity::class.java).apply {
                putExtra("BOOK_SHA1", bookSha1)
            }
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in_custom, R.anim.fade_out_custom)
        }

        ivFavorite.setOnClickListener {
            toggleFavorite()
        }
        
        ivWantToRead.setOnClickListener {
            toggleWantToRead()
        }

        ivShare.setOnClickListener {
            shareBookFile()
        }

        ivDelete.setOnClickListener {
            showDeleteConfirmation()
        }

        findViewById<View>(R.id.btnAiAnnotation)?.setOnClickListener {
            generateAiAnnotation()
        }

        findViewById<View>(R.id.btnBookSummary)?.setOnClickListener {
            showOrGenerateSummary()
        }

        findViewById<View>(R.id.btnAnalyzeCharacters)?.setOnClickListener {
            showOrGenerateCharacters()
        }
    }

    private fun toggleFavorite() {
        val book = currentBook ?: return
        val updated = book.copy(isFavorite = !book.isFavorite)
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@BookDetailActivity)
            withContext(Dispatchers.IO) {
                db.bookDao().updateBook(updated)
            }
            currentBook = updated
            updateFavoriteIcon(updated.isFavorite)
        }
    }

    private fun toggleWantToRead() {
        val book = currentBook ?: return
        val updated = book.copy(isWantToRead = !book.isWantToRead)
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@BookDetailActivity)
            withContext(Dispatchers.IO) {
                db.bookDao().updateBook(updated)
            }
            currentBook = updated
            updateWantToReadIcon(updated.isWantToRead)
        }
    }

    private fun updateFavoriteIcon(isFav: Boolean) {
        if (isFav) {
            ivFavorite.setImageResource(android.R.drawable.btn_star_big_on)
            ivFavorite.setColorFilter(Color.parseColor("#FFD700"))
        } else {
            ivFavorite.setImageResource(android.R.drawable.btn_star_big_off)
            ivFavorite.setColorFilter(Color.parseColor("#8E8E93"))
        }
    }

    private fun updateWantToReadIcon(isWantToRead: Boolean) {
        if (isWantToRead) {
            ivWantToRead.setImageResource(R.drawable.ic_pin_filled)
            ivWantToRead.setColorFilter(Color.parseColor("#FF5C5C"))
        } else {
            ivWantToRead.setImageResource(R.drawable.ic_pin)
            ivWantToRead.setColorFilter(Color.parseColor("#8E8E93"))
        }
    }

    private fun shareBookFile() {
        val book = currentBook ?: return
        val path = book.filePath
        if (path.isNullOrEmpty()) return
        val file = File(path)
        if (!file.exists()) return

        try {
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Поделиться книгой"))
        } catch (e: Exception) {
            Log.e("BookDetail", "Sharing failed", e)
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Удалить книгу?")
            .setMessage("Вы уверены, что хотите удалить эту книгу из библиотеки?")
            .setPositiveButton("Удалить") { _, _ ->
                deleteBook()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteBook() {
        val sha1 = bookSha1 ?: return
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@BookDetailActivity)
            withContext(Dispatchers.IO) {
                db.bookDao().deleteBookBySha1(sha1)
            }
            finish()
        }
    }

    private fun loadBookData() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@BookDetailActivity)
            
            // Query current book details
            val book = withContext(Dispatchers.IO) {
                db.bookDao().getBookBySha1(bookSha1!!)
            }

            // Query all duplicates with same SHA-1
            val copies = withContext(Dispatchers.IO) {
                db.bookDao().getBooksBySha1(bookSha1!!)
            }

            if (book != null) {
                currentBook = book
                tvTitle.text = book.title
                updateAiVisibility()
                
                // Author setup
                val authorName = book.author ?: "Неизвестен"
                tvAuthor.text = authorName
                llAuthorContainer.setOnClickListener {
                    val intent = Intent(this@BookDetailActivity, AuthorBooksActivity::class.java).apply {
                        putExtra("AUTHOR_NAME", authorName)
                    }
                    startActivity(intent)
                    overridePendingTransition(R.anim.fade_in_custom, R.anim.fade_out_custom)
                }

                // Series setup
                if (!book.series.isNullOrEmpty()) {
                    llSeriesContainer.visibility = View.VISIBLE
                    val indexText = if (book.seriesIndex != null && book.seriesIndex > 0) " №${book.seriesIndex}" else ""
                    val displayText = "${book.series}$indexText"
                    tvSeries.text = displayText
                    llSeriesContainer.setOnClickListener {
                        val intent = Intent(this@BookDetailActivity, SeriesBooksActivity::class.java).apply {
                            putExtra("SERIES_NAME", book.series)
                        }
                        startActivity(intent)
                        overridePendingTransition(R.anim.fade_in_custom, R.anim.fade_out_custom)
                    }

                    // Series Banner Setup
                    tvSeriesBanner.visibility = View.VISIBLE
                    tvSeriesBanner.text = "Серия: $displayText"
                    tvSeriesBanner.setOnClickListener {
                        val intent = Intent(this@BookDetailActivity, SeriesBooksActivity::class.java).apply {
                            putExtra("SERIES_NAME", book.series)
                        }
                        startActivity(intent)
                        overridePendingTransition(R.anim.fade_in_custom, R.anim.fade_out_custom)
                    }
                } else {
                    llSeriesContainer.visibility = View.GONE
                    tvSeriesBanner.visibility = View.GONE
                }

                // Annotation setup
                setupAnnotation(book, db)
                tvAnnotation.setOnClickListener { toggleAnnotation() }

                // Language setup
                val langCode = book.language ?: "ru"
                val displayLanguage = when (langCode.lowercase(Locale.ROOT)) {
                    "ru", "rus", "russian" -> "Русский"
                    "en", "eng", "english" -> "Английский"
                    else -> langCode
                }
                tvLanguage.text = displayLanguage

                // Progress & Date setup
                val percent = if (book.totalCharacters > 0) {
                    val calculated = ((book.currentProgressChar.toFloat() / book.totalCharacters) * 100).toInt().coerceIn(0, 100)
                    if (calculated >= 98) 100 else calculated
                } else {
                    0
                }
                val sdf = SimpleDateFormat("d MMMM yyyy г. HH:mm", Locale("ru"))
                val formattedDate = sdf.format(Date(book.lastReadTime))
                if (book.currentPageIndex > 0) {
                    tvProgress.text = "Стр. ${book.currentPageIndex + 1} ($percent%), $formattedDate"
                } else {
                    tvProgress.text = "$percent%, $formattedDate"
                }

                // Format & Size setup
                val filePath = book.filePath
                var formatName = "FB2"
                var sizeBytes = book.fileSize
                if (!filePath.isNullOrEmpty()) {
                    val file = File(filePath)
                    formatName = file.extension.uppercase(Locale.ROOT).ifEmpty { "FB2" }
                    if (sizeBytes == 0L && file.exists()) {
                        sizeBytes = file.length()
                    }
                }
                val sizeKb = sizeBytes / 1024
                val sizeText = if (sizeKb > 1024) {
                    String.format(Locale.US, "%.1f МБ", sizeKb / 1024.0)
                } else {
                    "$sizeKb КБ"
                }
                tvFormatSize.text = "$formatName, $sizeText"

                // SHA-1 setup
                tvSha1.text = "SHA-1: ${book.sha1}"

                // Favorite icon setup
                updateFavoriteIcon(book.isFavorite)

                // Want to read icon setup
                updateWantToReadIcon(book.isWantToRead)

                // Cover setup
                if (!book.coverPath.isNullOrEmpty() && File(book.coverPath).exists()) {
                    ivCover.load(File(book.coverPath)) {
                        listener(
                            onSuccess = { _, result ->
                                val bitmapDrawable = result.drawable as? android.graphics.drawable.BitmapDrawable
                                val bitmap = bitmapDrawable?.bitmap
                                if (bitmap != null) {
                                    androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                                        if (palette != null) {
                                            applyPaletteColors(palette)
                                        }
                                    }
                                }
                                supportStartPostponedEnterTransition()
                            },
                            onError = { _, _ ->
                                supportStartPostponedEnterTransition()
                            }
                        )
                    }
                    tvCoverLetter.visibility = View.GONE
                } else {
                    ivCover.setImageDrawable(null)
                    tvCoverLetter.visibility = View.VISIBLE
                    tvCoverLetter.text = if (book.title.isNotEmpty()) book.title.take(1).uppercase(Locale.ROOT) else "?"
                    supportStartPostponedEnterTransition()
                }

                // Render dynamic list of files copies
                renderFilesList(copies)
            } else {
                finish()
            }
        }
    }

    private fun setupAnnotation(book: BookEntity, db: AppDatabase) {
        val dbAnnotation = book.annotation
        val btnAiAnno = findViewById<View>(R.id.btnAiAnnotation)
        
        // If there's an annotation stored in the entity, display it
        if (!dbAnnotation.isNullOrEmpty() && dbAnnotation != "Аннотация отсутствует") {
            tvAnnotation.text = dbAnnotation
            btnAiAnno?.visibility = View.GONE
            checkAnnotationLength()
        } else if (!book.filePath.isNullOrEmpty() && File(book.filePath).exists()) {
            tvAnnotation.text = "Загрузка аннотации..."
            lifecycleScope.launch(Dispatchers.Default) {
                val fileAnnotation = readAnnotationFromFile(book.filePath)
                withContext(Dispatchers.Main) {
                    if (!fileAnnotation.isNullOrEmpty()) {
                        tvAnnotation.text = fileAnnotation
                        btnAiAnno?.visibility = View.GONE
                        checkAnnotationLength()
                        // Persist it back to database
                        launch(Dispatchers.IO) {
                            db.bookDao().updateBook(book.copy(annotation = fileAnnotation))
                        }
                    } else {
                        tvAnnotation.text = "Аннотация отсутствует"
                        tvReadMore.visibility = View.GONE
                        btnAiAnno?.visibility = View.GONE
                    }
                }
            }
        } else {
            tvAnnotation.text = "Аннотация отсутствует"
            tvReadMore.visibility = View.GONE
            btnAiAnno?.visibility = View.GONE
        }
    }

    private fun updateAiVisibility() {
        val cardAiFeatures = findViewById<View>(R.id.cardAiFeatures)
        val btnAiAnnotation = findViewById<View>(R.id.btnAiAnnotation)

        // Always show the AI features card as a premium highlight!
        cardAiFeatures?.visibility = View.VISIBLE
        
        val dbAnnotation = currentBook?.annotation
        val isMissingAnnotation = dbAnnotation.isNullOrEmpty() || dbAnnotation == "Аннотация отсутствует"
        
        // Show the annotation generation button if the book currently lacks an annotation
        btnAiAnnotation?.visibility = if (isMissingAnnotation) View.VISIBLE else View.GONE
    }

    private fun checkAnnotationLength() {
        tvAnnotation.post {
            if (tvAnnotation.layout != null && tvAnnotation.layout.lineCount > 5) {
                tvReadMore.visibility = View.VISIBLE
                tvAnnotation.maxLines = 5
                isAnnotationExpanded = false
            } else {
                tvReadMore.visibility = View.GONE
            }
        }
    }

    private fun toggleAnnotation() {
        val parentLayout = findViewById<android.view.ViewGroup>(R.id.llAnnotationContainer) ?: tvAnnotation.parent as? android.view.ViewGroup
        if (parentLayout != null) {
            android.transition.TransitionManager.beginDelayedTransition(parentLayout)
        }
        if (isAnnotationExpanded) {
            tvAnnotation.maxLines = 5
            tvReadMore.text = "...читать далее"
        } else {
            tvAnnotation.maxLines = Integer.MAX_VALUE
            tvReadMore.text = "Свернуть"
        }
        isAnnotationExpanded = !isAnnotationExpanded
    }

    private fun renderFilesList(copies: List<BookEntity>) {
        llFilesContainer.removeAllViews()
        val textPrimaryColor = Color.WHITE
        val textSecondaryColor = Color.parseColor("#B8A0C8")

        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)

        copies.forEachIndexed { index, copy ->
            val copyLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dpToPx(8), 0, dpToPx(8))
                setBackgroundResource(outValue.resourceId)
                isClickable = true
                isFocusable = true
            }

            val tvFileIndex = TextView(this).apply {
                text = "Файл №${index + 1}"
                textSize = 14f
                setTextColor(textPrimaryColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val tvFilePath = TextView(this).apply {
                text = copy.filePath ?: "Встроенный файл"
                textSize = 12f
                setTextColor(textSecondaryColor)
                setPadding(0, dpToPx(2), 0, 0)
            }

            copyLayout.addView(tvFileIndex)
            copyLayout.addView(tvFilePath)
            llFilesContainer.addView(copyLayout)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    // --- ASYNC ANNOTATION FB2 PARSING ENGINE ---

    private suspend fun readAnnotationFromFile(filePath: String): String? = withContext(Dispatchers.IO) {
        val pathLower = filePath.lowercase(Locale.ROOT)
        val bytes = readFirstBytesOfFb2(filePath)
        if (bytes == null || bytes.isEmpty()) return@withContext null

        val detectedEncoding = parseEncodingFromProlog(bytes)
        val charsetsToTry = mutableListOf<String>()

        if (detectedEncoding != null) {
            charsetsToTry.add(detectedEncoding)
        }

        // Standard fallbacks in order of likelihood for cyrillic and western texts
        val fallbacks = listOf("UTF-8", "windows-1251", "KOI8-R", "ISO-8859-1")
        for (fallback in fallbacks) {
            if (!charsetsToTry.contains(fallback)) {
                charsetsToTry.add(fallback)
            }
        }

        for (charset in charsetsToTry) {
            try {
                if (!java.nio.charset.Charset.isSupported(charset)) continue
                val bais = java.io.ByteArrayInputStream(bytes)
                val annotation = extractAnnotationFromStream(bais, charset)
                if (annotation != null && annotation.isNotEmpty()) {
                    return@withContext annotation
                }
            } catch (e: Exception) {
                Log.e("BookDetail", "Error parsing annotation with charset $charset", e)
            }
        }
        null
    }

    private fun readFirstBytesOfFb2(filePath: String, limit: Int = 256 * 1024): ByteArray? {
        val file = File(filePath)
        if (!file.exists()) return null

        val ext = file.extension.lowercase(Locale.ROOT)
        if (ext == "zip") {
            try {
                java.io.FileInputStream(file).use { fis ->
                    java.util.zip.ZipInputStream(fis).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val entryName = entry.name.lowercase(Locale.ROOT)
                            if (!entry.isDirectory && (entryName.endsWith(".fb2") || entryName.endsWith(".fb2.xml"))) {
                                val bos = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(4096)
                                var totalRead = 0
                                var read = 0
                                while (totalRead < limit && zis.read(buffer).also { read = it } != -1) {
                                    val toWrite = minOf(read, limit - totalRead)
                                    bos.write(buffer, 0, toWrite)
                                    totalRead += toWrite
                                }
                                return bos.toByteArray()
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BookDetail", "Error reading ZIP file: $filePath", e)
            }
        } else {
            try {
                java.io.FileInputStream(file).use { fis ->
                    val bos = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    var totalRead = 0
                    var read = 0
                    while (totalRead < limit && fis.read(buffer).also { read = it } != -1) {
                        val toWrite = minOf(read, limit - totalRead)
                        bos.write(buffer, 0, toWrite)
                        totalRead += toWrite
                    }
                    return bos.toByteArray()
                }
            } catch (e: Exception) {
                Log.e("BookDetail", "Error reading FB2 file: $filePath", e)
            }
        }
        return null
    }

    private fun parseEncodingFromProlog(bytes: ByteArray): String? {
        try {
            val size = minOf(bytes.size, 1024)
            val header = String(bytes, 0, size, java.nio.charset.StandardCharsets.ISO_8859_1)
            val match = """<\?xml[^>]*encoding=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE).find(header)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        } catch (e: Exception) {
            Log.e("BookDetail", "Error parsing XML prolog encoding", e)
        }
        return null
    }

    private fun extractAnnotationFromStream(inputStream: java.io.InputStream, charsetName: String): String? {
        try {
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            val reader = java.io.InputStreamReader(inputStream, charsetName)
            parser.setInput(reader)

            var eventType = parser.eventType
            var inAnnotation = false
            val annotationText = java.lang.StringBuilder()

            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name?.lowercase(Locale.ROOT)
                when (eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        if (tagName == "annotation" || tagName == "description") {
                            inAnnotation = true
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> {
                        if (inAnnotation) {
                            val txt = parser.text
                            if (txt != null) {
                                annotationText.append(txt)
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        if (tagName == "p" && inAnnotation) {
                            annotationText.append("\n")
                        } else if (tagName == "annotation" || tagName == "description") {
                            inAnnotation = false
                            break
                        }
                    }
                }
                eventType = parser.next()
            }

            val result = annotationText.toString().trim()
            return if (result.isNotEmpty()) result else null
        } catch (e: Exception) {
            Log.e("BookDetail", "Failed to parse XML using charset $charsetName", e)
        }
        return null
    }

    private fun applyPaletteColors(palette: androidx.palette.graphics.Palette) {
        // 1. Extract premium colors
        val defaultAccent = androidx.core.content.ContextCompat.getColor(this, R.color.accent)
        
        // Choose vibrant/lightVibrant color as the accent, fallback to default accent
        val vibrantColor = palette.getVibrantColor(palette.getLightVibrantColor(palette.getDominantColor(defaultAccent)))
        currentVibrantColor = vibrantColor
        val darkMutedColor = palette.getDarkMutedColor(palette.getDarkVibrantColor(0xFF140C26.toInt()))
        
        // 2. Beautiful Soft background gradient bleed
        val coordinatorLayout = findViewById<androidx.coordinatorlayout.widget.CoordinatorLayout>(R.id.coordinatorLayout)
        if (coordinatorLayout != null) {
            // Create a gorgeous gradient that blends with our starry sky background
            val bgGradient = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    adjustAlpha(vibrantColor, 0.28f), // Soft, warm color bleed at the top
                    adjustAlpha(darkMutedColor, 0.12f), // Extremely subtle color transition in middle
                    0x00000000 // Fades completely to transparent at the bottom
                )
            )
            coordinatorLayout.background = bgGradient
        }
        
        // 3. Cover background glow
        val flCoverContainer = findViewById<android.view.View>(R.id.flCoverContainer)
        if (flCoverContainer != null) {
            val coverGlow = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                val radiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics)
                cornerRadius = radiusPx
                setStroke(0, 0)
                colors = intArrayOf(
                    adjustAlpha(vibrantColor, 0.40f), // central glow
                    0x00000000 // fades out
                )
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 180f, resources.displayMetrics)
            }
            flCoverContainer.background = coverGlow
        }

        // 4. Colorize text elements
        tvAuthor.setTextColor(vibrantColor)
        tvSeries.setTextColor(vibrantColor)
        tvReadMore.setTextColor(vibrantColor)

        // 5. Read Button background tint
        btnReadToolbar.backgroundTintList = ColorStateList.valueOf(vibrantColor)
        
        // 6. Tint AI and Tonal Buttons
        val btnAiAnnotation = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAiAnnotation)
        val btnBookSummary = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBookSummary)
        val btnAnalyzeCharacters = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAnalyzeCharacters)
        
        val secondaryTint = adjustAlpha(vibrantColor, 0.15f)
        val textStateList = ColorStateList.valueOf(vibrantColor)
        
        btnAiAnnotation?.backgroundTintList = ColorStateList.valueOf(secondaryTint)
        btnAiAnnotation?.setTextColor(textStateList)
        btnAiAnnotation?.iconTint = textStateList
        
        btnBookSummary?.backgroundTintList = ColorStateList.valueOf(secondaryTint)
        btnBookSummary?.setTextColor(textStateList)
        btnBookSummary?.iconTint = textStateList
        
        btnAnalyzeCharacters?.backgroundTintList = ColorStateList.valueOf(secondaryTint)
        btnAnalyzeCharacters?.setTextColor(textStateList)
        btnAnalyzeCharacters?.iconTint = textStateList
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Math.round(Color.alpha(color) * factor)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }

    private var progressDialog: AlertDialog? = null

    private fun showProgressDialog(message: String) {
        val padding = dpToPx(24)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dpToPx(16)
            }
        }
        val textView = TextView(this).apply {
            text = message
            textSize = 16f
            setTextColor(Color.WHITE)
        }
        container.addView(progressBar)
        container.addView(textView)

        progressDialog = AlertDialog.Builder(this, R.style.Theme_NightRead_Dialog)
            .setView(container)
            .setCancelable(false)
            .create()
        progressDialog?.show()
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun showMissingApiKeyDialog() {
        val message = "Для работы ИИ-функций требуется указать API-ключ Gemini.\n\n" +
                "1. Получите бесплатный ключ в Google AI Studio.\n" +
                "2. Укажите его в панели Secrets в AI Studio под именем GEMINI_API_KEY или добавьте в ваш .env файл.\n\n" +
                "После этого ИИ-функции (аннотации, краткие содержания, анализ персонажей) станут доступны!"
                
        AlertDialog.Builder(this, R.style.Theme_NightRead_Dialog)
            .setTitle("Настройка Gemini AI")
            .setMessage(message)
            .setPositiveButton("ОК", null)
            .setNegativeButton("Открыть AI Studio") { _, _ ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/"))
                startActivity(intent)
            }
            .show()
    }

    private fun showActionMenu(anchorView: View, onShow: () -> Unit, onRegenerate: () -> Unit) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchorView, android.view.Gravity.END, 0, R.style.Theme_NightRead_PopupMenu)
        popup.menu.add(0, 1, 0, "Показать сохраненное")
        popup.menu.add(0, 2, 1, "Перегенерировать через ИИ")
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> onShow()
                2 -> onRegenerate()
            }
            true
        }
        popup.show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        CustomToast.show(this, "Текст скопирован в буфер обмена")
    }

    private fun shareText(title: String, text: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, title)
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        startActivity(android.content.Intent.createChooser(intent, "Поделиться через"))
    }

    private fun showAiResultDialog(title: String, content: String) {
        val scrollView = android.widget.ScrollView(this).apply {
            val padding = dpToPx(20)
            setPadding(padding, padding, padding, padding)
        }
        val textView = TextView(this).apply {
            val accentColor = currentVibrantColor ?: Color.parseColor("#FFE3A8")
            text = MarkdownRenderer.render(this@BookDetailActivity, content, accentColor)
            textSize = 15f
            setTextColor(Color.WHITE)
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.2f)
        }
        scrollView.addView(textView)

        val dialog = AlertDialog.Builder(this, R.style.Theme_NightRead_Dialog)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton("ОК", null)
            .setNegativeButton("Копировать") { _, _ ->
                copyToClipboard(title, content)
            }
            .setNeutralButton("Поделиться") { _, _ ->
                shareText(title, content)
            }
            .create()

        dialog.setOnShowListener {
            val accentStr = "#FFE3A8"
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor(accentStr))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor(accentStr))
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.parseColor(accentStr))
        }
        dialog.show()
    }

    private fun generateAiAnnotation() {
        val book = currentBook ?: return

        showProgressDialog("Локальный ИИ генерирует аннотацию...")

        lifecycleScope.launch {
            try {
                kotlinx.coroutines.delay(800)
                val textResponse = withContext(Dispatchers.IO) {
                    com.nightread.app.data.LocalAiEngine.generateAnnotation(this@BookDetailActivity, book)
                }

                if (textResponse.isNotEmpty()) {
                    val updated = book.copy(annotation = textResponse)
                    val db = AppDatabase.getDatabase(this@BookDetailActivity)
                    withContext(Dispatchers.IO) {
                        db.bookDao().updateBook(updated)
                    }
                    currentBook = updated
                    
                    dismissProgressDialog()
                    tvAnnotation.text = textResponse
                    findViewById<View>(R.id.btnAiAnnotation)?.visibility = View.GONE
                    checkAnnotationLength()
                    CustomToast.show(this@BookDetailActivity, "Аннотация успешно сгенерирована локальным ИИ!")
                } else {
                    dismissProgressDialog()
                    CustomToast.show(this@BookDetailActivity, "Не удалось выполнить локальный анализ")
                }
            } catch (e: Exception) {
                dismissProgressDialog()
                Log.e("BookDetailActivity", "Local AI Annotation generation failed", e)
                CustomToast.show(this@BookDetailActivity, "Ошибка ИИ: ${e.localizedMessage}")
            }
        }
    }

    private fun showOrGenerateSummary(force: Boolean = false) {
        val book = currentBook ?: return
        if (!force && !book.summary.isNullOrBlank()) {
            showActionMenu(
                anchorView = findViewById<View>(R.id.btnBookSummary) ?: return,
                onShow = { showAiResultDialog("Краткое содержание книги", book.summary) },
                onRegenerate = { showOrGenerateSummary(force = true) }
            )
            return
        }

        showProgressDialog("Локальный ИИ анализирует книгу...")

        lifecycleScope.launch {
            try {
                kotlinx.coroutines.delay(1200)
                val textResponse = withContext(Dispatchers.IO) {
                    com.nightread.app.data.LocalAiEngine.generateSummary(this@BookDetailActivity, book)
                }

                if (textResponse.isNotEmpty()) {
                    val updated = book.copy(summary = textResponse)
                    val db = AppDatabase.getDatabase(this@BookDetailActivity)
                    withContext(Dispatchers.IO) {
                        db.bookDao().updateBook(updated)
                    }
                    currentBook = updated
                    
                    dismissProgressDialog()
                    showAiResultDialog("Краткое содержание книги", textResponse)
                } else {
                    dismissProgressDialog()
                    CustomToast.show(this@BookDetailActivity, "Не удалось выполнить локальный анализ")
                }
            } catch (e: Exception) {
                dismissProgressDialog()
                Log.e("BookDetailActivity", "Local AI Summary generation failed", e)
                CustomToast.show(this@BookDetailActivity, "Ошибка ИИ: ${e.localizedMessage}")
            }
        }
    }

    private fun showOrGenerateCharacters(force: Boolean = false) {
        val book = currentBook ?: return
        if (!force && !book.characters.isNullOrBlank()) {
            showActionMenu(
                anchorView = findViewById<View>(R.id.btnAnalyzeCharacters) ?: return,
                onShow = { showAiResultDialog("Главные персонажи", book.characters) },
                onRegenerate = { showOrGenerateCharacters(force = true) }
            )
            return
        }

        showProgressDialog("Локальный ИИ анализирует персонажей...")

        lifecycleScope.launch {
            try {
                kotlinx.coroutines.delay(1000)
                val textResponse = withContext(Dispatchers.IO) {
                    com.nightread.app.data.LocalAiEngine.generateCharacters(this@BookDetailActivity, book)
                }

                if (textResponse.isNotEmpty()) {
                    val updated = book.copy(characters = textResponse)
                    val db = AppDatabase.getDatabase(this@BookDetailActivity)
                    withContext(Dispatchers.IO) {
                        db.bookDao().updateBook(updated)
                    }
                    currentBook = updated
                    
                    dismissProgressDialog()
                    showAiResultDialog("Главные персонажи", textResponse)
                } else {
                    dismissProgressDialog()
                    CustomToast.show(this@BookDetailActivity, "Не удалось составить список персонажей")
                }
            } catch (e: Exception) {
                dismissProgressDialog()
                Log.e("BookDetailActivity", "Local AI Character analysis failed", e)
                CustomToast.show(this@BookDetailActivity, "Ошибка ИИ: ${e.localizedMessage}")
            }
        }
    }
}

// --- Custom Markdown Parser for Beautiful Rich Text Styling ---
object MarkdownRenderer {
    fun render(context: android.content.Context, source: String, accentColor: Int): android.text.Spanned {
        val ssb = android.text.SpannableStringBuilder()
        val lines = source.split("\n")
        
        for ((index, rawLine) in lines.withIndex()) {
            var line = rawLine
            val isHeader3 = line.startsWith("### ")
            val isHeader2 = line.startsWith("## ")
            val isHeader1 = line.startsWith("# ")
            val isBullet = line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ")
            
            var headerLevel = 0
            if (isHeader3) {
                line = line.substring(4)
                headerLevel = 3
            } else if (isHeader2) {
                line = line.substring(3)
                headerLevel = 2
            } else if (isHeader1) {
                line = line.substring(2)
                headerLevel = 1
            } else if (isBullet) {
                line = line.substring(2)
            }
            
            val startOfLine = ssb.length
            
            if (isBullet) {
                ssb.append("•  ")
            }
            
            var currentPos = 0
            while (currentPos < line.length) {
                val boldIdx = line.indexOf("**", currentPos)
                val italicIdx = line.indexOf("*", currentPos)
                
                if (boldIdx != -1 && (italicIdx == -1 || boldIdx <= italicIdx)) {
                    if (boldIdx > currentPos) {
                        ssb.append(line.substring(currentPos, boldIdx))
                    }
                    val boldEnd = line.indexOf("**", boldIdx + 2)
                    if (boldEnd != -1) {
                        val startSpan = ssb.length
                        ssb.append(line.substring(boldIdx + 2, boldEnd))
                        val endSpan = ssb.length
                        ssb.setSpan(
                            android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                            startSpan,
                            endSpan,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        currentPos = boldEnd + 2
                    } else {
                        ssb.append("**")
                        currentPos = boldIdx + 2
                    }
                } else if (italicIdx != -1) {
                    if (italicIdx > currentPos) {
                        ssb.append(line.substring(currentPos, italicIdx))
                    }
                    val italicEnd = line.indexOf("*", italicIdx + 1)
                    if (italicEnd != -1) {
                        val startSpan = ssb.length
                        ssb.append(line.substring(italicIdx + 1, italicEnd))
                        val endSpan = ssb.length
                        ssb.setSpan(
                            android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                            startSpan,
                            endSpan,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        currentPos = italicEnd + 1
                    } else {
                        ssb.append("*")
                        currentPos = italicIdx + 1
                    }
                } else {
                    ssb.append(line.substring(currentPos))
                    break
                }
            }
            
            val endOfLine = ssb.length
            
            if (headerLevel > 0) {
                val sizeMultiplier = when (headerLevel) {
                    1 -> 1.35f
                    2 -> 1.25f
                    else -> 1.15f
                }
                ssb.setSpan(
                    android.text.style.RelativeSizeSpan(sizeMultiplier),
                    startOfLine,
                    endOfLine,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                ssb.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    startOfLine,
                    endOfLine,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                ssb.setSpan(
                    android.text.style.ForegroundColorSpan(accentColor),
                    startOfLine,
                    endOfLine,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            
            if (index < lines.size - 1) {
                ssb.append("\n")
            }
        }
        return ssb
    }
}
