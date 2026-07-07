package com.nightread.app.ui

import android.os.Bundle
import android.text.TextPaint
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.SettingsManager
import com.nightread.app.service.Fb2Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream

class ReadingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        viewPager = findViewById(R.id.viewPager)
        progressBar = findViewById(R.id.progressBar)

        val sha1 = intent.getStringExtra("BOOK_SHA1")
        if (sha1.isNullOrEmpty()) {
            Toast.makeText(this, "Книга не найдена", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadBook(sha1)
    }

    private fun loadBook(sha1: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ReadingActivity)
            val book = withContext(Dispatchers.IO) {
                db.bookDao().getBookBySha1(sha1)
            }

            if (book == null) {
                Toast.makeText(this@ReadingActivity, "Книга не найдена в БД", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            val filePath = book.filePath
            if (filePath.isNullOrEmpty()) {
                Toast.makeText(this@ReadingActivity, "Путь к файлу пуст", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(this@ReadingActivity, "Файл не найден на диске", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            try {
                Log.d("ReadingActivity", "Loading file: $filePath, size: ${file.length()}")
                
                val text = withContext(Dispatchers.IO) {
                    extractTextFromFile(file)
                }

                if (text.isEmpty()) {
                    Toast.makeText(this@ReadingActivity, "Не удалось прочитать текст", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                // Layout measurements
                val paint = TextPaint().apply {
                    textSize = SettingsManager.getFontSize(this@ReadingActivity) * resources.displayMetrics.density
                }
                
                // Wait for layout to get viewPager dimensions
                viewPager.post {
                    lifecycleScope.launch {
                        val width = viewPager.width - (32 * resources.displayMetrics.density).toInt() // padding 16dp on each side
                        val height = viewPager.height - (32 * resources.displayMetrics.density).toInt() 
                        
                        Log.d("ReadingActivity", "Splitting pages for width: $width, height: $height")
                        
                        val splitResult = PageSplitter.splitText(
                            text = text,
                            availableWidth = width,
                            availableHeight = height,
                            paint = paint,
                            lineSpacing = SettingsManager.getLineSpacing(this@ReadingActivity),
                            alignment = "justify"
                        )
                        
                        Log.d("ReadingActivity", "Pages split: ${splitResult.pages.size}")

                        progressBar.visibility = View.GONE
                        viewPager.adapter = ReaderPagerAdapter(this@ReadingActivity, splitResult.pages)
                        
                        // Set current page
                        val savedPage = book.currentPageIndex ?: 0
                        if (savedPage < splitResult.pages.size) {
                            viewPager.setCurrentItem(savedPage, false)
                        }

                        // Listen for page changes to save progress
                        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                            override fun onPageSelected(position: Int) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val charOffset = if (position < splitResult.offsets.size) splitResult.offsets[position] else 0
                                    db.bookDao().updateProgressAndPage(sha1, charOffset, position, System.currentTimeMillis())
                                }
                            }
                        })
                    }
                }
            } catch (e: Exception) {
                Log.e("ReadingActivity", "Error loading book", e)
                Toast.makeText(this@ReadingActivity, "Ошибка чтения файла: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun extractTextFromFile(file: File): String {
        val ext = file.extension.lowercase(Locale.ROOT)
        return when (ext) {
            "fb2", "xml" -> {
                val xmlContent = file.readText(StandardCharsets.UTF_8)
                Fb2Parser.extractText(xmlContent)
            }
            "zip" -> {
                var content = ""
                FileInputStream(file).use { fis ->
                    ZipInputStream(fis).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && (entry.name.endsWith(".fb2") || entry.name.endsWith(".xml"))) {
                                val xmlContent = zis.bufferedReader().use { it.readText() }
                                content = Fb2Parser.extractText(xmlContent)
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
                content
            }
            "txt" -> file.readText(StandardCharsets.UTF_8)
            else -> ""
        }
    }
}
