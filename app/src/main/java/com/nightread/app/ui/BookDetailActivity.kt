package com.nightread.app.ui

import android.content.Intent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookDetailActivity : AppCompatActivity() {

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
    private lateinit var ivShare: ImageView
    private lateinit var ivDelete: ImageView

    private lateinit var llFilesContainer: LinearLayout
    private lateinit var llAuthorContainer: LinearLayout
    private lateinit var llSeriesContainer: LinearLayout

    private var bookSha1: String? = null
    private var currentBook: BookEntity? = null
    private var isAnnotationExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_detail)

        // Handle WindowInsets for Edge-to-Edge immersion
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val rootView = findViewById<View>(android.R.id.content)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, insets.top, 0, insets.bottom)
            windowInsets
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
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
        ivShare = findViewById(R.id.ivShare)
        ivDelete = findViewById(R.id.ivDelete)

        llFilesContainer = findViewById(R.id.llFilesContainer)
        llAuthorContainer = findViewById(R.id.llAuthorContainer)
        llSeriesContainer = findViewById(R.id.llSeriesContainer)

        bookSha1 = intent.getStringExtra("BOOK_SHA1")

        if (bookSha1.isNullOrEmpty()) {
            finish()
            return
        }
        
        ivCover.transitionName = "cover_$bookSha1"

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
            val intent = Intent(this, ReadingActivity::class.java).apply {
                putExtra("BOOK_SHA1", bookSha1)
            }
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        ivFavorite.setOnClickListener {
            toggleFavorite()
        }

        ivShare.setOnClickListener {
            shareBookFile()
        }

        ivDelete.setOnClickListener {
            showDeleteConfirmation()
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

    private fun updateFavoriteIcon(isFav: Boolean) {
        if (isFav) {
            ivFavorite.setImageResource(android.R.drawable.btn_star_big_on)
        } else {
            ivFavorite.setImageResource(android.R.drawable.btn_star_big_off)
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

                // Author setup
                val authorName = book.author ?: "Неизвестен"
                tvAuthor.text = authorName
                llAuthorContainer.setOnClickListener {
                    val intent = Intent(this@BookDetailActivity, AuthorBooksActivity::class.java).apply {
                        putExtra("AUTHOR_NAME", authorName)
                    }
                    startActivity(intent)
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
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
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }

                    // Series Banner Setup
                    tvSeriesBanner.visibility = View.VISIBLE
                    tvSeriesBanner.text = "Серия: $displayText"
                    tvSeriesBanner.setOnClickListener {
                        val intent = Intent(this@BookDetailActivity, SeriesBooksActivity::class.java).apply {
                            putExtra("SERIES_NAME", book.series)
                        }
                        startActivity(intent)
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
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
                    ((book.currentProgressChar.toFloat() / book.totalCharacters) * 100).toInt().coerceIn(0, 100)
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

                // Cover setup
                if (!book.coverPath.isNullOrEmpty() && File(book.coverPath).exists()) {
                    ivCover.load(File(book.coverPath))
                    tvCoverLetter.visibility = View.GONE
                } else {
                    ivCover.setImageDrawable(null)
                    tvCoverLetter.visibility = View.VISIBLE
                    tvCoverLetter.text = if (book.title.isNotEmpty()) book.title.take(1).uppercase(Locale.ROOT) else "?"
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
        // If there's an annotation stored in the entity, display it
        if (!dbAnnotation.isNullOrEmpty() && dbAnnotation != "Аннотация отсутствует") {
            tvAnnotation.text = dbAnnotation
            checkAnnotationLength()
        } else if (!book.filePath.isNullOrEmpty() && File(book.filePath).exists()) {
            tvAnnotation.text = "Загрузка аннотации..."
            lifecycleScope.launch(Dispatchers.Default) {
                val fileAnnotation = readAnnotationFromFile(book.filePath)
                withContext(Dispatchers.Main) {
                    if (!fileAnnotation.isNullOrEmpty()) {
                        tvAnnotation.text = fileAnnotation
                        checkAnnotationLength()
                        // Persist it back to database
                        launch(Dispatchers.IO) {
                            db.bookDao().updateBook(book.copy(annotation = fileAnnotation))
                        }
                    } else {
                        tvAnnotation.text = "Аннотация отсутствует"
                        tvReadMore.visibility = View.GONE
                    }
                }
            }
        } else {
            tvAnnotation.text = "Аннотация отсутствует"
            tvReadMore.visibility = View.GONE
        }
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
        val textPrimaryColor = resources.getColor(R.color.text_primary, theme)
        val textSecondaryColor = resources.getColor(R.color.text_secondary, theme)

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
}
