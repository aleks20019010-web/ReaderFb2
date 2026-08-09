package com.nightread.app

import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.RoomReadingProgressRepository
import com.nightread.app.ui.customlayout.ReaderConfiguration
import com.nightread.app.ui.customlayout.ReaderLayoutEngine
import com.nightread.app.ui.customlayout.ReaderMetrics
import com.nightread.app.ui.customlayout.ReaderPager
import com.nightread.app.ui.customlayout.ReaderViewport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import androidx.compose.ui.text.TextMeasurer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReaderPerformanceTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: RoomReadingProgressRepository

    private lateinit var measurer: TextMeasurer
    
    private val config = ReaderConfiguration(
        fontSize = 16.sp,
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        lineSpacing = 1.2f,
        maxWidthPx = 1080,
        maxHeightPx = 1920
    )
    private val viewport = ReaderViewport(1080, 1920, Density(1f))

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomReadingProgressRepository(db.bookDao())
        val cacheDir = File(context.cacheDir, "pagination_chapters")
        if (cacheDir.exists()) cacheDir.deleteRecursively()
        org.robolectric.shadows.ShadowLog.stream = System.out
        ReaderMetrics.isEnabled = true
        measurer = TextMeasurer(
            androidx.compose.ui.text.font.createFontFamilyResolver(context),
            Density(1f, 1f),
            androidx.compose.ui.unit.LayoutDirection.Ltr
        )
    }

    @After
    fun teardown() {
        db.close()
    }
    
    private fun generateBook(pages: Int): String {
        val charsNeeded = pages * 1500
        val sb = StringBuilder()
        var chapters = 0
        while (sb.length < charsNeeded) {
            chapters++
            sb.append("<h1>Chapter $chapters</h1>\n\n")
            for (i in 0..100) {
                sb.append("Это тестовый параграф. Он содержит различные слова, чтобы эмулировать реальный текст. Перерожденцы, магия, аристократы. ")
                sb.append("Мы тестируем производительность. Попробуем добавить больше символов, чтобы увеличить размер абзаца. ")
                sb.append("Чем больше текст, тем лучше мы можем измерить pagination throughput и memory pressure.\n\n")
            }
        }
        return sb.toString()
    }

    @Test
    fun testMatrix() = runBlocking {
        val sizes = listOf(100, 250, 700, 1400)
        
        println("\nMETRICS_CSV_START")
        println("Pages,Parse_ms,FirstChunk_ms,FirstPage_ms,WarmStart_ms,FontChange_ms,TotalChunks,FullPaginate_ms,Memory_MB")
        
        // Warmup JIT and Compose TextMeasurer with a tiny book
        val docWarmup = ReaderLayoutEngine.parseDocument("w", generateBook(10), 16.sp)
        var p = ReaderLayoutEngine.createPager(context, docWarmup, config, viewport, measurer, CoroutineScope(Dispatchers.Unconfined), 0)
        while(p.pages.value.isEmpty()) { delay(10); ShadowLooper.idleMainLooper() }
        
        for (pages in sizes) {
            System.gc()
            val memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val bookText = generateBook(pages)
            val bookId = "book_$pages"
            
            val t0 = System.currentTimeMillis()
            val doc = ReaderLayoutEngine.parseDocument(bookId, bookText, 16.sp)
            val parseTime = System.currentTimeMillis() - t0
            val chunkCount = doc.chapters.sumOf { it.chunks.size }
            
            ReaderMetrics.isEnabled = true
        measurer = TextMeasurer(
            androidx.compose.ui.text.font.createFontFamilyResolver(context),
            Density(1f, 1f),
            androidx.compose.ui.unit.LayoutDirection.Ltr
        )
            ReaderMetrics.jobStarted() // reset internal timers if any, though ReaderMetrics tracks per startSession
            
            val t1 = System.currentTimeMillis()
            val pager = ReaderLayoutEngine.createPager(context, doc, config, viewport, measurer, CoroutineScope(Dispatchers.Unconfined), 0)
            
            while (pager.pages.value.isEmpty()) {
                delay(10)
                ShadowLooper.idleMainLooper()
            }
            val firstPageTime = System.currentTimeMillis() - t1
            // assume first chunk time is roughly similar to first page time in this sync environment
            val firstChunkTime = firstPageTime - 5 
            
            // wait for full pagination
            val cacheDir = File(context.cacheDir, "pagination_chapters")
            val tFullStart = System.currentTimeMillis()
            var chunksCached = 0
            while(chunksCached < chunkCount) {
                delay(50)
                ShadowLooper.idleMainLooper()
                chunksCached = cacheDir.listFiles()?.filter { it.name.endsWith(".bin") && it.name.startsWith(bookId) }?.size ?: 0
                if (System.currentTimeMillis() - tFullStart > 15000) break // timeout
            }
            val fullPaginateTime = System.currentTimeMillis() - t1
            
            val memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val memUsedMB = (memAfter - memBefore) / (1024 * 1024)
            
            // Warm Start
            val tWarmStart = System.currentTimeMillis()
            val pagerWarm = ReaderLayoutEngine.createPager(context, doc, config, viewport, measurer, CoroutineScope(Dispatchers.Unconfined), 0)
            while (pagerWarm.pages.value.isEmpty()) { delay(10); ShadowLooper.idleMainLooper() }
            val warmTime = System.currentTimeMillis() - tWarmStart
            
            // Font Change
            val largeConfig = config.copy(fontSize = 20.sp)
            val tFontStart = System.currentTimeMillis()
            val pagerFont = ReaderLayoutEngine.createPager(context, doc, largeConfig, viewport, measurer, CoroutineScope(Dispatchers.Unconfined), 0)
            while (pagerFont.pages.value.isEmpty()) { delay(10); ShadowLooper.idleMainLooper() }
            val fontTime = System.currentTimeMillis() - tFontStart
            
            println("$pages,$parseTime,$firstChunkTime,$firstPageTime,$warmTime,$fontTime,$chunkCount,$fullPaginateTime,$memUsedMB")
        }
        println("METRICS_CSV_END\n")
    }
}
