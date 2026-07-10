package com.nightread.app.ui

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import com.nightread.app.data.SettingsManager
import com.nightread.app.data.BookCache
import com.nightread.app.ui.PageSplitter.PageResult
import com.nightread.app.ui.ReaderPagerAdapter
import android.graphics.Color
import android.util.Log
import android.content.res.Resources

class ReadingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager
    private lateinit var topBar: View
    private lateinit var bottomBar: View
    private lateinit var progressBar: View
    private lateinit var tvLoadingProgress: TextView

    private var splitResult: PageResult? = null
    private var isSplittingFinished = false
    private var progressiveJob = lifecycleScope.launch { }

    // Высота статус-бара (вычисляется один раз при создании)
    private val statusBarHeight by lazy { getStatusBarHeight() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_reader)

        viewPager = findViewById(R.id.viewPager)
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        progressBar = findViewById(R.id.progressBar)
        tvLoadingProgress = findViewById(R.id.tvLoadingProgress)

        // Инициализация адаптера (пустой список до разбивки)
        viewPager.adapter = ReaderPagerAdapter(this, emptyList())

        // Отслеживаем изменение размеров viewPager для пересчёта страниц
        viewPager.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                viewPager.viewTreeObserver.removeOnGlobalLayoutListener(this)
                if (viewPager.width > 0 && viewPager.height > 0) {
                    recalculatePages()
                }
            }
        })
    }

    override fun onDestroy() {
        progressiveJob.cancel()
        super.onDestroy()
    }

    /**
     * Получает высоту статус-бара из системных ресурсов.
     * Это надёжнее, чем пытаться получить её через insets, когда системные бары скрыты.
     */
    private fun getStatusBarHeight(): Int {
        val res = resources
        val resourceId = res.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) res.getDimensionPixelSize(resourceId) else 0
    }

    /**
     * Основной метод пересчёта страниц.
     * @param targetCharOffset Смещение символа, к которому нужно перейти (например, после восстановления позиции).
     *                          Если -1, восстанавливаем позицию по текущей странице.
     */
    private suspend fun recalculatePages(targetCharOffset: Int = -1) {
        if (!lifecycleScope.coroutineContext.isActive) return

        var resolvedCharOffset = targetCharOffset
        if (resolvedCharOffset < 0 && splitResult != null) {
            val currentIdx = viewPager.currentItem
            if (currentIdx >= 0 && currentIdx < splitResult!!.offsets.size) {
                resolvedCharOffset = splitResult!!.offsets[currentIdx]
            }
        }

        val width = viewPager.width
        val height = viewPager.height
        if (width <= 0 || height <= 0) return

        // Показываем индикатор загрузки
        progressBar.visibility = View.VISIBLE
        tvLoadingProgress.visibility = View.VISIBLE
        tvLoadingProgress.text = "Разбивка на страницы..."

        // Настройка TextPaint на основе текущих настроек
        val paint = TextPaint().apply {
            textSize = SettingsManager.getFontSize(this@ReadingActivity) * resources.displayMetrics.density
            val family = SettingsManager.getFontFamily(this@ReadingActivity)
            val numericWeight = SettingsManager.getFontWeightAsInt(this@ReadingActivity)
            typeface = com.nightread.app.utils.FontUtils.createTypeface(family, numericWeight)
        }

        // --- РАСЧЁТ ДОСТУПНОЙ ВЫСОТЫ (ИСПРАВЛЕНИЕ ОШИБКИ ОТСТУПОВ) ---
        // 1. Высота статус-бара (системная)
        // 2. Высота верхней панели (topBar)
        // 3. Высота нижней панели (bottomBar)
        // 4. Внутренний вертикальный отступ страницы (например, 16dp)
        val topBarHeight = if (topBar.isLaidOut) topBar.measuredHeight else 0
        val bottomBarHeight = if (bottomBar.isLaidOut) bottomBar.measuredHeight else 0
        val pagePaddingVertical = (16 * resources.displayMetrics.density).toInt()
        val pagePaddingHorizontal = (32 * resources.displayMetrics.density).toInt() // 16dp слева + 16dp справа

        val paddingVertical = statusBarHeight + topBarHeight + bottomBarHeight + pagePaddingVertical
        val availableWidth = max(0, width - pagePaddingHorizontal)
        val availableHeight = max(0, height - paddingVertical)

        if (availableWidth == 0 || availableHeight == 0) {
            Log.w("ReadingActivity", "Available dimensions are zero! W=$availableWidth, H=$availableHeight")
            progressBar.visibility = View.GONE
            tvLoadingProgress.visibility = View.GONE
            return
        }

        Log.d("READING_DEBUG", "Layout dims: W=$availableWidth, H=$availableHeight. Pads: SB=$statusBarHeight, TB=$topBarHeight, BB=\$bottomBarHeight")
        // -----------------------------------------------------------------

        // Получаем флаг переносов из настроек
        val hyphenationEnabled = com.nightread.app.data.SettingsManager.isHyphenationEnabled(this@ReadingActivity)

        // Текст для разбивки (сырой, без ручной гипонизации)
        val textToSplit = com.nightread.app.data.BookCache.bookContent ?: ""

        if (textToSplit.isEmpty()) {
            progressBar.visibility = View.GONE
            tvLoadingProgress.visibility = View.GONE
            return
        }

        // Ключ для кэширования макета (зависит от всех параметров, влияющих на верстку)
        val currentKey = "${width}_${height}_\${paint.textSize}" +
                "_\${SettingsManager.getFontFamily(this@ReadingActivity)}" +
                "_\${SettingsManager.getFontWeightAsInt(this@ReadingActivity)}" +
                "_\${SettingsManager.getLineSpacing(this@ReadingActivity)}" +
                "_hyphen=\$hyphenationEnabled"

        // Проверка кэша
        if (com.nightread.app.data.BookCache.sha1 == com.nightread.app.data.BookCache.currentBookSha1 &&
            com.nightread.app.data.BookCache.layoutKey == currentKey &&
            com.nightread.app.data.BookCache.splitResult?.isFinished == true) {

            splitResult = com.nightread.app.data.BookCache.splitResult!!
            isSplittingFinished = true

            tvLoadingProgress.visibility = View.GONE
            progressBar.visibility = View.GONE

            if (viewPager.adapter == null) {
                viewPager.adapter = ReaderPagerAdapter(this@ReadingActivity, splitResult!!.pages)
            } else {
                (viewPager.adapter as ReaderPagerAdapter).pages = splitResult!!.pages
                viewPager.adapter?.notifyDataSetChanged()
            }

            var targetPage = 0
            if (resolvedCharOffset >= 0) {
                targetPage = splitResult!!.offsets.indexOfLast { it <= resolvedCharOffset }.coerceAtLeast(0)
            } else {
                val currentIdx = viewPager.currentItem
                if (currentIdx < splitResult!!.offsets.size) {
                    val offset = splitResult!!.offsets[currentIdx]
                    targetPage = splitResult!!.offsets.indexOfLast { it <= offset }.coerceAtLeast(0)
                }
            }

            if (targetPage < splitResult!!.pages.size) {
                viewPager.setCurrentItem(targetPage, false)
            }
            updateBottomBar(targetPage)
            showBarsWithAnimation(animateFab = true)
            return
        }

        // Отмена предыдущей задачи разбивки
        progressiveJob.cancel()
        viewPager.adapter = ReaderPagerAdapter(this@ReadingActivity, splitResult?.pages ?: emptyList())
        isSplittingFinished = false
        var isFirstRender = true

        progressiveJob = lifecycleScope.launch {
            PageSplitter.splitTextProgressive(
                text = textToSplit,
                availableWidth = availableWidth,
                availableHeight = availableHeight,
                paint = paint,
                lineSpacing = SettingsManager.getLineSpacing(this@ReadingActivity),
                alignment = "justify",
                hyphenationEnabled = hyphenationEnabled, // Передаем флаг явно
                onProgress = { result ->
                    val oldCount = splitResult?.pages?.size ?: 0
                    splitResult = result
                    isSplittingFinished = result.isFinished

                    (viewPager.adapter as ReaderPagerAdapter).pages = result.pages
                    viewPager.adapter?.notifyDataSetChanged()

                    if (isFirstRender && result.pages.isNotEmpty()) {
                        isFirstRender = false
                        progressBar.visibility = View.GONE
                        tvLoadingProgress.visibility = View.GONE

                        var targetPage = 0
                        if (resolvedCharOffset >= 0) {
                            targetPage = result.offsets.indexOfLast { it <= resolvedCharOffset }.coerceAtLeast(0)
                        } else {
                            targetPage = 0
                        }

                        if (targetPage < result.pages.size) {
                            viewPager.setCurrentItem(targetPage, false)
                        }
                        updateBottomBar(targetPage)
                        showBarsWithAnimation(animateFab = true)
                    } else {
                        // При прогрессивной отрисовке пытаемся держать целевую страницу видимой
                        if (resolvedCharOffset >= 0 && result.pages.isNotEmpty()) {
                            val targetPage = result.offsets.indexOfLast { it <= resolvedCharOffset }.coerceAtLeast(0)
                            if (targetPage < result.pages.size && viewPager.currentItem != targetPage) {
                                viewPager.setCurrentItem(targetPage, false)
                            }
                        }
                        updateBottomBar(viewPager.currentItem)
                    }

                    if (result.isFinished) {
                        com.nightread.app.data.BookCache.layoutKey = currentKey
                        com.nightread.app.data.BookCache.splitResult = result
                    }
                }
            )
        }
    }

    /**
     * Обновляет UI нижней панели (номер страницы, прогресс и т.д.)
     */
    private fun updateBottomBar(pageIndex: Int) {
        // Здесь логика обновления UI нижней панели
        // Например: tvPageNumber.text = "${pageIndex + 1} / ${splitResult?.pages?.size}"
        Log.d("READING_DEBUG", "Current page: \${pageIndex + 1}")
    }

    /**
     * Показывает панели управления с анимацией (если требуется)
     */
    private fun showBarsWithAnimation(animateFab: Boolean) {
        // Логика анимации появления панелей
        topBar.visibility = View.VISIBLE
        bottomBar.visibility = View.VISIBLE
    }
}
