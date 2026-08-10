package com.nightread.app.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoAwesome

import com.nightread.app.data.BookmarkDatabase
import com.nightread.app.data.BookmarkEntity
import com.nightread.app.data.BookmarkRepository
import com.nightread.app.ui.customlayout.ReaderSearchEngine
import com.nightread.app.ui.customlayout.ReaderSearchResult
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import com.nightread.app.utils.TypographyUtils
import com.nightread.app.ui.BrightnessHelper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.Intent
import android.os.Build
import com.nightread.app.service.TtsForegroundService
import com.nightread.app.data.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.debounce
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlin.math.abs

enum class ThemeType(val displayName: String) {
    DAY("День"),
    NIGHT("Ночь"),
    SEPIA("Сепия"),
    HIGH_CONTRAST("Высокая контрастность")
}

data class ReaderSettings(
    val themeType: ThemeType = ThemeType.SEPIA,
    val fontFamily: String = "Serif",
    val fontSize: Float = 20f,
    val fontWeight: Float = 0.5f,
    val lineHeight: Float = 1.4f,
    val isHideBars: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderComposeScreen(
    sha1: String = "",
    bookTitle: String = "Орден Архитекторов",
    authorAndChapter: String = "Олег Сапфир, Юрий Винокуров | Глава 11",
    mainText: String = sampleText,
    isLoading: Boolean = false,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = (context as? Activity)?.window

    var settingsVersion by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        SettingsManager.settingsChanged.collect {
            settingsVersion++
        }
    }

    val fontSize = remember(settingsVersion, context) { SettingsManager.getFontSize(context) }
    val lineSpacing = remember(settingsVersion, context) { SettingsManager.getLineSpacing(context) }
    val fontFamilyStr = remember(settingsVersion, context) { SettingsManager.getFontFamily(context) }
    val fontWeightInt = remember(settingsVersion, context) { SettingsManager.getFontWeightAsInt(context) }
    val readingThemeStr = remember(settingsVersion, context) { SettingsManager.getReadingTheme(context) }
    val isAutoThemeEnabled = remember(settingsVersion, context) { SettingsManager.isReaderAutoThemeEnabled(context) }
    val isAmberEnabled = remember(settingsVersion, context) { SettingsManager.isAmberFilterEnabled(context) }
    val isExtraDimEnabled = remember(settingsVersion, context) { SettingsManager.isExtraDimEnabled(context) }
    val extraDimIntensity = remember(settingsVersion, context) { SettingsManager.getExtraDimIntensity(context) }
    val isHapticEnabled = remember(settingsVersion, context) { SettingsManager.isHapticFeedbackEnabled(context) }
    val isSilentModeEnabled = remember(settingsVersion, context) { SettingsManager.isSilentModeEnabled(context) }
    val isHyphenationEnabled = remember(settingsVersion, context) { SettingsManager.isHyphenationEnabled(context) }
    val isSleepTimerEnabled = remember(settingsVersion, context) { SettingsManager.isSleepTimerEnabled(context) }
    val sleepTimerDuration = remember(settingsVersion, context) { SettingsManager.getSleepTimerDuration(context) }
    val pageAnimation = remember(settingsVersion, context) { SettingsManager.getPageAnimation(context) }
    var sleepTimerRemaining by remember { mutableLongStateOf(0L) }

    var isHideBars by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var isSettingsOpen by remember { mutableStateOf(false) }
    
    // --- Bookmarks & Search State ---
    val bookmarkDb = remember(context) { BookmarkDatabase.getDatabase(context) }
    val bookmarkRepo = remember(bookmarkDb) { BookmarkRepository(bookmarkDb.bookmarkDao()) }
    val bookmarks by bookmarkRepo.getBookmarksForBook(sha1).collectAsState(initial = emptyList())

    
    val searchEngine = remember(mainText) { ReaderSearchEngine(mainText) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ReaderSearchResult>>(emptyList()) }
    var currentSearchIndex by remember { mutableIntStateOf(-1) }
    var isSearchMode by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    

    var pendingTargetOffset by remember { mutableStateOf<Int?>(null) }
    var showTocSheet by remember { mutableStateOf(false) }
    
    // --------------------------------
    
    val fragmentActivity = context as? androidx.fragment.app.FragmentActivity
    LaunchedEffect(fragmentActivity) {
        if (fragmentActivity is BookReaderActivity) {
            fragmentActivity.navigationEvents.collect { offset ->
                pendingTargetOffset = offset
            }
        }
    }


    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // Brightness and Warmth (amber filter) states
    val initialBrightness = remember(context) {
        val b = SettingsManager.getBrightness(context)
        if (b < 0) {
            (context as? Activity)?.let { BrightnessHelper.getBrightness(it) } ?: 0.5f
        } else b
    }
    var currentBrightness by remember { mutableFloatStateOf(initialBrightness) }

    val initialWarmth = remember(context) {
        SettingsManager.getAmberFilterIntensity(context)
    }
    var currentWarmth by remember { mutableIntStateOf(initialWarmth) }

    var gestureIndicatorText by remember { mutableStateOf<String?>(null) }
    var gestureIndicatorIcon by remember { mutableStateOf<ImageVector?>(null) }
    var showGestureIndicatorTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(showGestureIndicatorTime) {
        if (showGestureIndicatorTime > 0L) {
            delay(1500L)
            gestureIndicatorText = null
        }
    }

    // Auto-hide top and bottom bars after 3 seconds of inactivity when visible
    LaunchedEffect(isHideBars, lastInteractionTime) {
        if (!isHideBars) {
            delay(3000L)
            isHideBars = true
        }
    }

    // Apply brightness on launch
    LaunchedEffect(currentBrightness) {
        (context as? Activity)?.let {
            BrightnessHelper.setBrightness(it, currentBrightness)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // Set edge-to-edge layout ONCE so the window size never changes
    LaunchedEffect(Unit) {
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }

    // Status bar and nav bar hiding
    LaunchedEffect(isHideBars) {
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            if (isHideBars) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val themeType = if (isAutoThemeEnabled) {
        if (com.nightread.app.data.ThemeHelper.isNightTime()) ThemeType.NIGHT else ThemeType.DAY
    } else {
        when (readingThemeStr.lowercase()) {
            "day", "light" -> ThemeType.DAY
            "night", "dark", "amoled" -> ThemeType.NIGHT
            "sepia" -> ThemeType.SEPIA
            "contrast", "sepia_contrast" -> ThemeType.HIGH_CONTRAST
            else -> ThemeType.SEPIA
        }
    }

    val (bgColor, textColor) = when (themeType) {
        ThemeType.DAY -> Color(0xFFFBF9F1) to Color(0xFF1B1B1B)
        ThemeType.NIGHT -> Color(0xFF0F140D) to Color(0xFFC4C9BC)
        ThemeType.SEPIA -> Color(0xFFF4ECD8) to Color(0xFF3B2F1F)
        ThemeType.HIGH_CONTRAST -> Color(0xFFFFFFFF) to Color(0xFF000000)
    }

    val font = when (fontFamilyStr) {
        "Default" -> FontFamily.Default
        "Merriweather", "Serif", "Georgia", "Times New Roman", "Lora", "EB Garamond", "Literata" -> FontFamily.Serif
        "Roboto", "SansSerif", "OpenDyslexic" -> FontFamily.SansSerif
        "Monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
    
    val mappedFontWeight = FontWeight(fontWeightInt.coerceIn(100, 900))

    var readerDocument by remember { mutableStateOf<com.nightread.app.ui.customlayout.ReaderDocument?>(null) }
    var readerPages by remember { mutableStateOf<List<com.nightread.app.ui.customlayout.ReaderPage>>(emptyList()) }
    val pageStartOffsets = remember(readerPages) { readerPages.map { it.startOffset } }
    var isRestoringProgress by remember { mutableStateOf(true) }
    var savedTextOffset by remember { mutableIntStateOf(0) }
    var isPreparingText by remember { mutableStateOf(true) }
    val textMeasurer = rememberTextMeasurer()

    val pagerState = rememberPagerState(pageCount = { readerPages.size.coerceAtLeast(1) })
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }

    // Haptic feedback on page turn
    LaunchedEffect(pagerState.currentPage) {
        if (isHapticEnabled && !isSilentModeEnabled && !isRestoringProgress) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    // Sleep Timer logic
    LaunchedEffect(isSleepTimerEnabled, sleepTimerDuration) {
        if (isSleepTimerEnabled) {
            sleepTimerRemaining = sleepTimerDuration * 60 * 1000L
            while (sleepTimerRemaining > 0) {
                delay(10000L) // Check every 10 seconds to be efficient
                sleepTimerRemaining -= 10000L
            }
            if (isSleepTimerEnabled) { // Double check if still enabled
                onBackClick()
            }
        }
    }
    
    val currentChapter = remember(pagerState.currentPage, readerPages, readerDocument) {
        val currentOffset = readerPages.getOrNull(pagerState.currentPage)?.startOffset ?: 0
        readerDocument?.chapters?.find { it.startOffset <= currentOffset && it.endOffset > currentOffset }
    }
    
    val database = remember { com.nightread.app.data.DatabaseProvider.getInstance(context) }
    val progressRepository = remember { com.nightread.app.data.RoomReadingProgressRepository(database.bookDao()) }

    LaunchedEffect(sha1) {
        if (sha1.isNotEmpty()) {
            val progress = progressRepository.getProgress(sha1)
            savedTextOffset = progress?.sourceOffset ?: 0
            isRestoringProgress = true
            android.util.Log.d("ReadingProgress", "RESTORE bookId=$sha1 offset=$savedTextOffset")
        }
    }

    LaunchedEffect(pagerState.currentPage, readerPages) {
        if (!isRestoringProgress && readerPages.isNotEmpty() && sha1.isNotEmpty()) {
            val currentOffset = readerPages.getOrNull(pagerState.currentPage)?.startOffset ?: 0
            delay(500) // Debounce 500ms
            
            android.util.Log.d("ReadingProgress", "SAVE (debounce) bookId=$sha1 offset=$currentOffset")
            progressRepository.saveProgress(
                com.nightread.app.data.ReadingProgress(
                    bookId = sha1,
                    sourceOffset = currentOffset,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, readerPages, pagerState.currentPage) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                if (sha1.isNotEmpty() && readerPages.isNotEmpty()) {
                    val currentOffset = readerPages.getOrNull(pagerState.currentPage)?.startOffset ?: 0
                    android.util.Log.d("ReadingProgress", "SAVE (lifecycle) bookId=$sha1 offset=$currentOffset")
                    
                    kotlinx.coroutines.runBlocking {
                        progressRepository.saveProgress(
                            com.nightread.app.data.ReadingProgress(
                                bookId = sha1,
                                sourceOffset = currentOffset,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (sha1.isNotEmpty() && readerPages.isNotEmpty()) {
                val currentOffset = readerPages.getOrNull(pagerState.currentPage)?.startOffset ?: 0
                android.util.Log.d("ReadingProgress", "SAVE (dispose) bookId=$sha1 offset=$currentOffset")
                kotlinx.coroutines.runBlocking {
                    progressRepository.saveProgress(
                        com.nightread.app.data.ReadingProgress(
                            bookId = sha1,
                            sourceOffset = currentOffset,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    val activity = context as? BookReaderActivity
    DisposableEffect(webViewRef) {
        activity?.onNextPage = {
            webViewRef?.evaluateJavascript("window.nextPage();", null)
        }
        activity?.onPrevPage = {
            webViewRef?.evaluateJavascript("window.prevPage();", null)
        }
        onDispose {
            activity?.onNextPage = null
            activity?.onPrevPage = null
        }
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        containerColor = bgColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            webViewRef?.evaluateJavascript("window.nextPage();", null)
                            true
                        }
                        android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                            webViewRef?.evaluateJavascript("window.prevPage();", null)
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = textColor,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Загрузка книги...",
                            color = textColor.copy(alpha = alpha),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = font
                        )
                    }
                }
            } else {
                val cutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
                val cutoutBottom = WindowInsets.displayCutout.asPaddingValues().calculateBottomPadding()

                // Container for reader content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    // Main Text Content as HorizontalPager with dynamic text measuring
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        val density = LocalDensity.current
                        val maxWidthPx = with(density) { constraints.maxWidth }
                        val maxHeightPx = with(density) { constraints.maxHeight }
                        
                        val currentReadingOffset = remember(pagerState.currentPage, readerPages, savedTextOffset) {
                            if (readerPages.isNotEmpty() && pagerState.currentPage < readerPages.size) {
                                readerPages[pagerState.currentPage].startOffset
                            } else {
                                savedTextOffset
                            }
                        }
                        
                        val latestOffset = rememberUpdatedState(currentReadingOffset)

                        val debouncedConfig by produceState<com.nightread.app.ui.customlayout.ReaderConfiguration?>(
                            initialValue = null, 
                            fontSize, lineSpacing, font, mappedFontWeight, maxWidthPx, maxHeightPx
                        ) {
                            snapshotFlow {
                                if (maxWidthPx <= 0 || maxHeightPx <= 0) null
                                else com.nightread.app.ui.customlayout.ReaderConfiguration(
                                    fontSize = fontSize.sp,
                                    fontFamily = font,
                                    fontWeight = mappedFontWeight,
                                    lineSpacing = lineSpacing,
                                    maxWidthPx = maxWidthPx,
                                    maxHeightPx = maxHeightPx
                                )
                            }.debounce(if (value == null) 0L else 400L)
                             .collect { value = it }
                        }

                        var oldWidthPx by remember { mutableIntStateOf(0) }
                        var oldHeightPx by remember { mutableIntStateOf(0) }
                        
                        LaunchedEffect(maxWidthPx, maxHeightPx, isHideBars) {
                            if (oldWidthPx != 0 && oldHeightPx != 0 && (oldWidthPx != maxWidthPx || oldHeightPx != maxHeightPx)) {
                                android.util.Log.e("ReaderViewport", "ERROR: Viewport changed! This will cause repagination.\nold viewport:\nwidth=$oldWidthPx\nheight=$oldHeightPx\n\nnew viewport:\nwidth=$maxWidthPx\nheight=$maxHeightPx\nisHideBars=$isHideBars")
                            } else {
                                android.util.Log.d("ReaderViewport", "viewport stable\nwidth=$maxWidthPx\nheight=$maxHeightPx\nisHideBars=$isHideBars")
                            }
                            oldWidthPx = maxWidthPx
                            oldHeightPx = maxHeightPx
                        }

                        val readerTextStyle = remember(fontSize, font, mappedFontWeight, lineSpacing, isHyphenationEnabled) {
                            TextStyle(
                                fontSize = fontSize.sp,
                                fontFamily = font,
                                fontWeight = mappedFontWeight,
                                textAlign = TextAlign.Justify,
                                lineHeight = (fontSize * lineSpacing).sp,
                                letterSpacing = 0.1.sp,
                                lineBreak = LineBreak.Paragraph,
                                hyphens = if (isHyphenationEnabled) Hyphens.Auto else Hyphens.None,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        }

                        LaunchedEffect(mainText, sha1) {
                            if (mainText.isNotEmpty()) {
                                readerDocument = com.nightread.app.ui.customlayout.ReaderLayoutEngine.parseDocument(
                                    bookId = sha1.ifEmpty { "default" },
                                    mainText = mainText,
                                    baseFontSize = fontSize.sp
                                )
                            }
                        }

                        LaunchedEffect(readerDocument, debouncedConfig) {
                            val doc = readerDocument
                            val config = debouncedConfig
                            if (doc != null && config != null) {
                                isPreparingText = true
                                val targetOffset = latestOffset.value
                                
                                val viewport = com.nightread.app.ui.customlayout.ReaderViewport(
                                    widthPx = config.maxWidthPx,
                                    heightPx = config.maxHeightPx,
                                    density = density
                                )

                                val pager = com.nightread.app.ui.customlayout.ReaderLayoutEngine.createPager(
                                    context = context,
                                    document = doc,
                                    config = config,
                                    viewport = viewport,
                                    textMeasurer = textMeasurer,
                                    scope = this,
                                    initialTargetOffset = targetOffset
                                )
                                
                                launch {
                                    snapshotFlow { pendingTargetOffset }.collectLatest { target ->
                                        if (target != null) {
                                            pager.goToOffset(target)
                                        }
                                    }
                                }

                                launch {
                                    snapshotFlow { pagerState.currentPage }.collectLatest { currentPage ->
                                        if (readerPages.isNotEmpty() && currentPage < readerPages.size) {
                                            pager.notifyPageChanged(readerPages[currentPage].startOffset)
                                        }
                                    }
                                }

                                pager.pages.collect { updatedPages ->
                                    if (updatedPages.isNotEmpty()) {
                                        val oldPages = readerPages
                                        readerPages = updatedPages
                                        
                                        if (oldPages.isEmpty() || isRestoringProgress) {
                                            val targetPage = findPageForOffset(updatedPages.map { it.startOffset }, targetOffset)
                                            pagerState.scrollToPage(targetPage)
                                            isRestoringProgress = false
                                        }
                                        
                                        if (pendingTargetOffset != null && updatedPages.isNotEmpty()) {
                                            val target = pendingTargetOffset!!
                                            val targetPage = findPageForOffset(updatedPages.map { it.startOffset }, target)
                                            if (targetPage != -1) {
                                                pagerState.scrollToPage(targetPage)
                                                pendingTargetOffset = null
                                            }
                                        }
                                        
                                        isPreparingText = false
                                    }
                                }
                            }
                        }

                        if (isPreparingText && readerPages.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = textColor,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text(
                                        text = "Подготовка текста...",
                                        color = textColor.copy(alpha = alpha),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = font
                                    )
                                }
                            }
                        } else {
                            val textColorHex = String.format("#%06X", (0xFFFFFF and textColor.toArgb()))
                            val bgColorHex = String.format("#%06X", (0xFFFFFF and bgColor.toArgb()))
                            val densityVal = density.density
                            val widthDp = (maxWidthPx / densityVal).toInt()
                            val heightDp = (maxHeightPx / densityVal).toInt()
                            val statusBarsTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                            val displayCutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
                            val topPaddingDp = maxOf(statusBarsTop, displayCutoutTop) + 3.dp
                            val topPaddingPx = with(density) { topPaddingDp.toPx().toInt() }
                            val leftPaddingPx = with(density) { 20.dp.toPx().toInt() }
                            val rightPaddingPx = with(density) { 20.dp.toPx().toInt() }
                            val bottomPaddingPx = with(density) { 20.dp.toPx().toInt() }

                            val htmlContent = remember(mainText, font, fontSize, mappedFontWeight, lineSpacing, widthDp, heightDp, textColorHex, bgColorHex, pageAnimation, topPaddingPx) {
                                com.nightread.app.ui.customlayout.ReaderWebViewEngine.prepareHtmlForBook(
                                    context = context,
                                    bookId = sha1.ifEmpty { "default" },
                                    mainText = mainText,
                                    fontFamily = "Serif",
                                    fontSize = fontSize,
                                    fontWeight = mappedFontWeight.weight.toFloat(),
                                    lineHeight = lineSpacing,
                                    textColorHex = textColorHex,
                                    bgColorHex = bgColorHex,
                                    viewportWidth = widthDp,
                                    viewportHeight = heightDp,
                                    pageAnimation = pageAnimation,
                                    topPadding = topPaddingPx,
                                    bottomPadding = bottomPaddingPx,
                                    leftPadding = leftPaddingPx,
                                    rightPadding = rightPaddingPx
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clipToBounds(),
                                contentAlignment = Alignment.TopStart
                            ) {
                                com.nightread.app.ui.customlayout.ReaderWebViewComponent(
                                    modifier = Modifier.fillMaxSize().testTag("reader_webview"),
                                    htmlContent = htmlContent,
                                    fontFamily = "Serif",
                                    fontSize = fontSize,
                                    fontWeight = mappedFontWeight.weight.toFloat(),
                                    lineHeight = lineSpacing,
                                    themeColor = textColor,
                                    bgColor = Color.Transparent,
                                    currentPage = pagerState.currentPage,
                                    targetOffset = pendingTargetOffset ?: if (isRestoringProgress) savedTextOffset else 0,
                                    onTargetOffsetHandled = {
                                        pendingTargetOffset = null
                                        isRestoringProgress = false
                                    },
                                    onPositionChanged = { offset, page, total ->
                                        savedTextOffset = offset
                                    },
                                    onWordSelected = { word -> },
                                    onNoteClicked = { noteId -> },
                                    onNextPage = {},
                                    onPreviousPage = {},
                                    onWebViewCreated = { webView ->
                                        webViewRef = webView
                                    },
                                    onToggleBars = {
                                        isHideBars = !isHideBars
                                        lastInteractionTime = System.currentTimeMillis()
                                    },
                                    onVerticalScroll = { startX, dragAmount ->
                                        if (abs(dragAmount) > 1.5f) {
                                            lastInteractionTime = System.currentTimeMillis()
                                            val screenWidth = maxWidthPx
                                            val activity = context as? Activity
                                            if (startX < screenWidth / 2f) {
                                                currentBrightness = (currentBrightness - dragAmount / 600f).coerceIn(0.02f, 1f)
                                                if (activity != null) {
                                                    BrightnessHelper.setBrightness(activity, currentBrightness)
                                                }
                                                SettingsManager.setBrightness(context, currentBrightness)
                                                gestureIndicatorText = "Яркость: ${(currentBrightness * 100).toInt()}%"
                                                gestureIndicatorIcon = Icons.Filled.WbSunny
                                                showGestureIndicatorTime = System.currentTimeMillis()
                                            } else {
                                                currentWarmth = (currentWarmth - dragAmount / 5f).toInt().coerceIn(0, 100)
                                                SettingsManager.setAmberFilterIntensity(context, currentWarmth)
                                                SettingsManager.setAmberFilterEnabled(context, currentWarmth > 0)
                                                gestureIndicatorText = "Теплота: $currentWarmth%"
                                                gestureIndicatorIcon = Icons.Filled.Thermostat
                                                showGestureIndicatorTime = System.currentTimeMillis()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Warmth (Amber filter) overlay
                    if (isAmberEnabled && currentWarmth > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFFF8000).copy(alpha = (currentWarmth / 100f) * 0.35f))
                        )
                    }

                    // Extra Dim overlay
                    if (isExtraDimEnabled && extraDimIntensity > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = (extraDimIntensity / 100f) * 0.6f))
                        )
                    }

                    // Gesture Indicator Toast Overlay
                    AnimatedVisibility(
                        visible = gestureIndicatorText != null,
                        enter = fadeIn(animationSpec = tween(150)),
                        exit = fadeOut(animationSpec = tween(150)),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.78f),
                            contentColor = Color.White,
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                gestureIndicatorIcon?.let { icon ->
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = Color(0xFFFFC107),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text(
                                    text = gestureIndicatorText ?: "",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            val glassBgColor = bgColor.copy(alpha = if (themeType == ThemeType.NIGHT || themeType == ThemeType.HIGH_CONTRAST) 0.85f else 0.90f)
            val glassBorder = BorderStroke(1.dp, textColor.copy(alpha = 0.18f))

            // Top Panel (Glassmorphism)
            AnimatedVisibility(
                visible = !isHideBars && !isSearchMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = glassBgColor,
                    border = glassBorder,
                    shadowElevation = 8.dp
                ) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        title = {
                            Column {
                                Text(
                                    text = bookTitle,
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = authorAndChapter,
                                    color = textColor.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = textColor)
                            }
                        },
                        actions = {
                            val fragmentActivity = context as? androidx.fragment.app.FragmentActivity
                            val currentOffset = if (readerPages.isNotEmpty() && pagerState.currentPage < readerPages.size) readerPages[pagerState.currentPage].startOffset else 0
                            val isBookmarked = bookmarks.any { it.charOffset == currentOffset }
                            
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    if (isBookmarked) {
                                        bookmarkRepo.deleteBookmarkAtOffset(sha1, currentOffset)
                                    } else {
                                        bookmarkRepo.insertBookmark(BookmarkEntity(bookSha1 = sha1, bookTitle = bookTitle, charOffset = currentOffset, pageIndex = pagerState.currentPage, snippet = "Закладка на позиции $currentOffset", timestamp = System.currentTimeMillis()))
                                    }
                                }
                            }) {
                                Icon(if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = "Закладка", tint = textColor)
                            }

                            IconButton(onClick = {
                                isSearchMode = true
                                isHideBars = true
                            }) {
                                Icon(Icons.Filled.Search, contentDescription = "Поиск", tint = textColor)
                            }

                            IconButton(onClick = { showTocSheet = true }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Оглавление", tint = textColor)
                            }
                            
                            IconButton(onClick = {
                                fragmentActivity?.supportFragmentManager?.let { fm ->
                                    BookmarksListBottomSheet.newInstance(sha1).show(fm, "BookmarksList")
                                }
                            }) {
                                Icon(Icons.Filled.List, contentDescription = "Список закладок", tint = textColor)
                            }
                            
                            IconButton(onClick = {
                                fragmentActivity?.supportFragmentManager?.let { fm ->
                                    SettingsBottomSheet().show(fm, "SettingsBottomSheet")
                                }
                            }) {
                                Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = textColor)
                            }
                        }
                    )
                }
            }

            // Bottom Panel (Glassmorphism)
            AnimatedVisibility(
                visible = !isHideBars && !isSearchMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = glassBgColor,
                    border = glassBorder,
                    shadowElevation = 8.dp
                ) {
                    var isDraggingSlider by remember { mutableStateOf(false) }
                    var sliderPageValue by remember { mutableStateOf(0f) }

                    val totalPages = pagerState.pageCount
                    val maxPage = (totalPages - 1).coerceAtLeast(0)
                    val currentOffset = if (readerPages.isNotEmpty() && pagerState.currentPage < readerPages.size) readerPages[pagerState.currentPage].startOffset else 0
                    val totalChars = mainText.length.coerceAtLeast(1)
                    val currentPercent = (currentOffset.toFloat() / totalChars) * 100f

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Прогресс",
                                color = textColor.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = String.format("%.1f%%", currentPercent),
                                color = textColor.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        val currentSliderVal = if (isDraggingSlider) sliderPageValue else currentPercent
                        androidx.compose.material3.Slider(
                            value = currentSliderVal.coerceIn(0f, 100f),
                            onValueChange = { newValue ->
                                isDraggingSlider = true
                                sliderPageValue = newValue
                            },
                            onValueChangeFinished = {
                                val targetOffset = ((sliderPageValue / 100f) * totalChars).toInt().coerceIn(0, totalChars)
                                pendingTargetOffset = targetOffset
                                isDraggingSlider = false
                            },
                            valueRange = 0f..100f,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = textColor,
                                activeTrackColor = textColor,
                                inactiveTrackColor = textColor.copy(alpha = 0.2f)
                            ),
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(textColor, CircleShape)
                                )
                            },
                            track = { sliderState ->
                                androidx.compose.material3.SliderDefaults.Track(
                                    sliderState = sliderState,
                                    colors = androidx.compose.material3.SliderDefaults.colors(
                                        activeTrackColor = textColor,
                                        inactiveTrackColor = textColor.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier.height(4.dp)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            // Search UI Overlay
            AnimatedVisibility(
                visible = isSearchMode,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = glassBgColor,
                    border = glassBorder,
                    shadowElevation = 8.dp
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { query ->
                                    searchQuery = query
                                    if (query.isNotEmpty()) {
                                        coroutineScope.launch {
                                            searchResults = searchEngine.search(query)
                                            if (searchResults.isNotEmpty()) {
                                                // Find the first result after current offset
                                                val currentOffset = readerPages.getOrNull(pagerState.currentPage)?.startOffset ?: 0
                                                var nearestIndex = 0
                                                for (i in searchResults.indices) {
                                                    if (searchResults[i].sourceStartOffset >= currentOffset) {
                                                        nearestIndex = i
                                                        break
                                                    }
                                                }
                                                currentSearchIndex = nearestIndex
                                                pendingTargetOffset = searchResults[currentSearchIndex].sourceStartOffset
                                            } else {
                                                currentSearchIndex = -1
                                            }
                                        }
                                    } else {
                                        searchResults = emptyList()
                                        currentSearchIndex = -1
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Поиск...", color = textColor.copy(alpha = 0.5f)) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = textColor,
                                    unfocusedTextColor = textColor,
                                    cursorColor = textColor,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true
                            )
                            
                            IconButton(onClick = {
                                isSearchMode = false
                                searchQuery = ""
                                searchResults = emptyList()
                                currentSearchIndex = -1
                                isHideBars = false
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = textColor)
                            }
                        }
                        
                        if (searchResults.isNotEmpty()) {
                            androidx.compose.material3.Divider(color = textColor.copy(alpha = 0.1f))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("${currentSearchIndex + 1} из ${searchResults.size}", color = textColor)
                                Row {
                                    IconButton(onClick = {
                                        if (currentSearchIndex > 0) {
                                            currentSearchIndex--
                                            pendingTargetOffset = searchResults[currentSearchIndex].sourceStartOffset
                                        }
                                    }) {
                                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Предыдущий", tint = textColor)
                                    }
                                    IconButton(onClick = {
                                        if (currentSearchIndex < searchResults.size - 1) {
                                            currentSearchIndex++
                                            pendingTargetOffset = searchResults[currentSearchIndex].sourceStartOffset
                                        }
                                    }) {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Следующий", tint = textColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Table of Contents Bottom Sheet ---
        if (showTocSheet && readerDocument != null) {
            ModalBottomSheet(
                onDismissRequest = { showTocSheet = false },
                sheetState = sheetState,
                containerColor = bgColor, // Using bgColor since it's available in broader scope
                contentColor = textColor,
                tonalElevation = 8.dp,
                dragHandle = { BottomSheetDefaults.DragHandle(color = textColor.copy(alpha = 0.3f)) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.8f)
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Оглавление",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp),
                        color = textColor
                    )
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(readerDocument!!.chapters) { chapter ->
                            val isCurrent = chapter == currentChapter
                            ListItem(
                                headlineContent = { 
                                    Text(
                                        text = chapter.title,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else textColor
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "Глава ${chapter.chapterIndex + 1}",
                                        fontSize = 12.sp,
                                        color = textColor.copy(alpha = 0.6f)
                                    )
                                },
                                modifier = Modifier.clickable {
                                    pendingTargetOffset = chapter.startOffset
                                    showTocSheet = false
                                    isHideBars = true
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            HorizontalDivider(color = textColor.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit
) {
    val sheetTextColor = Color(0xFF2A3A22)
    val secondaryTextColor = Color.Gray
    val accentColor = Color(0xFF7A9B6A)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "НАСТРОЙКИ ЧТЕНИЯ",
            color = Color(0xFF5C7A4E),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Dropdown: Тема
        val themes = ThemeType.values().map { it.displayName }
        SettingsDropdown(
            label = "ЦВЕТОВАЯ СХЕМА",
            selectedValue = settings.themeType.displayName,
            options = themes,
            onOptionSelected = { selected ->
                val type = ThemeType.values().first { it.displayName == selected }
                onSettingsChange(settings.copy(themeType = type))
            }
        )

        // Dropdown: Шрифт
        val fonts = listOf("Default", "Merriweather", "Roboto", "Serif")
        SettingsDropdown(
            label = "ШРИФТ",
            selectedValue = settings.fontFamily,
            options = fonts,
            onOptionSelected = { selected ->
                onSettingsChange(settings.copy(fontFamily = selected))
            }
        )

        // Stepper: Размер шрифта
        SettingsStepper(
            label = "РАЗМЕР ШРИФТА",
            value = Math.round(settings.fontSize).toString(),
            onDecrease = {
                if (settings.fontSize > 14f) onSettingsChange(settings.copy(fontSize = settings.fontSize - 1f))
            },
            onIncrease = {
                if (settings.fontSize < 40f) onSettingsChange(settings.copy(fontSize = settings.fontSize + 1f))
            }
        )

        // Slider: Жирность шрифта
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("ЖИРНОСТЬ ШРИФТА", color = secondaryTextColor, fontSize = 12.sp)
            Slider(
                value = settings.fontWeight,
                onValueChange = { onSettingsChange(settings.copy(fontWeight = it)) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = Color.DarkGray
                )
            )
        }

        // Stepper: Межстрочный интервал
        SettingsStepper(
            label = "МЕЖСТРОЧНЫЙ ИНТЕРВАЛ",
            value = String.format("%.1f", settings.lineHeight),
            onDecrease = {
                if (settings.lineHeight > 1.0f) onSettingsChange(settings.copy(lineHeight = settings.lineHeight - 0.1f))
            },
            onIncrease = {
                if (settings.lineHeight < 2.0f) onSettingsChange(settings.copy(lineHeight = settings.lineHeight + 0.1f))
            }
        )

        // Switch: Скрыть бары
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Скрыть статус-бар и навигацию", color = Color(0xFF2A3A22), fontSize = 14.sp)
            Switch(
                checked = settings.isHideBars,
                onCheckedChange = { onSettingsChange(settings.copy(isHideBars = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF7A9B6A),
                    checkedTrackColor = Color(0xFF7A9B6A).copy(alpha = 0.5f),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.DarkGray
                )
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedValue,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color(0xFF2A3A22),
                    unfocusedTextColor = Color(0xFF2A3A22),
                    focusedContainerColor = Color.DarkGray.copy(alpha = 0.3f),
                    unfocusedContainerColor = Color.DarkGray.copy(alpha = 0.3f)
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFFC8D4BA))
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = Color(0xFF2A3A22)) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsStepper(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFC8D4BA), shape = CircleShape)
                    .clickable { onDecrease() },
                contentAlignment = Alignment.Center
            ) {
                Text("–", color = Color(0xFF2A3A22), fontSize = 24.sp)
            }
            Text(value, color = Color(0xFF2A3A22), fontSize = 16.sp)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFC8D4BA), shape = CircleShape)
                    .clickable { onIncrease() },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Color(0xFF2A3A22), fontSize = 24.sp)
            }
        }
    }
}

private fun openTtsSettingsSheet(
    activity: androidx.fragment.app.FragmentActivity?,
    mainText: String,
    bookTitle: String
) {
    activity?.supportFragmentManager?.let { fm ->
        val sheet = TtsSettingsBottomSheet.newInstance(mainText, bookTitle)
        sheet.setTtsListener(object : TtsSettingsBottomSheet.TtsSettingsListener {
            override fun onTtsStartRequested(speed: Float, pitch: Float, voiceName: String?, continuous: Boolean) {
                try {
                    val intent = Intent(activity, TtsForegroundService::class.java).apply {
                        action = TtsForegroundService.ACTION_START
                        putExtra(TtsForegroundService.EXTRA_TEXT, mainText)
                        putExtra(TtsForegroundService.EXTRA_BOOK_TITLE, bookTitle)
                        putExtra(TtsForegroundService.EXTRA_SPEED, speed)
                        putExtra(TtsForegroundService.EXTRA_PITCH, pitch)
                        putExtra(TtsForegroundService.EXTRA_VOICE, voiceName)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        activity.startForegroundService(intent)
                    } else {
                        activity.startService(intent)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ReaderComposeScreen", "Error starting TTS service", e)
                }
            }

            override fun onTtsPauseRequested() {
                try {
                    val intent = Intent(activity, TtsForegroundService::class.java).apply {
                        action = TtsForegroundService.ACTION_PAUSE
                    }
                    activity.startService(intent)
                } catch (e: Exception) {
                    android.util.Log.e("ReaderComposeScreen", "Error pausing TTS service", e)
                }
            }

            override fun onTtsStopRequested() {
                try {
                    val intent = Intent(activity, TtsForegroundService::class.java).apply {
                        action = TtsForegroundService.ACTION_STOP
                    }
                    activity.startService(intent)
                } catch (e: Exception) {
                    android.util.Log.e("ReaderComposeScreen", "Error stopping TTS service", e)
                }
            }

            override fun onTtsSpeedChanged(speed: Float) {
                try {
                    val intent = Intent(activity, TtsForegroundService::class.java).apply {
                        action = TtsForegroundService.ACTION_SET_SPEED
                        putExtra(TtsForegroundService.EXTRA_SPEED, speed)
                    }
                    activity.startService(intent)
                } catch (e: Exception) {
                    android.util.Log.e("ReaderComposeScreen", "Error set speed TTS service", e)
                }
            }

            override fun onTtsPitchChanged(pitch: Float) {
                try {
                    val intent = Intent(activity, TtsForegroundService::class.java).apply {
                        action = TtsForegroundService.ACTION_SET_PITCH
                        putExtra(TtsForegroundService.EXTRA_PITCH, pitch)
                    }
                    activity.startService(intent)
                } catch (e: Exception) {
                    android.util.Log.e("ReaderComposeScreen", "Error set pitch TTS service", e)
                }
            }

            override fun onTtsVoiceChanged(voiceName: String) {
                try {
                    val intent = Intent(activity, TtsForegroundService::class.java).apply {
                        action = TtsForegroundService.ACTION_SET_VOICE
                        putExtra(TtsForegroundService.EXTRA_VOICE, voiceName)
                    }
                    activity.startService(intent)
                } catch (e: Exception) {
                    android.util.Log.e("ReaderComposeScreen", "Error set voice TTS service", e)
                }
            }
        })
        sheet.show(fm, "TtsSettings")
    }
}

val sampleText = """
для примера, я слышал истории, что были перерожденцы, которые имели Дар, что зависел от солнца. Вот только в том мире, где они переродились, оно появлялось всего раза два в год или три. Оттого человека записали в сумасшедшие, когда он всем рассказал, как он правил и насколько могущественным был, и давай толкать свою идеи. А потому первое правило перерожденца — это молчать об этом. Ты можешь быть бесконечно могущественным в прошлом мире, но в этом тебе просто могут не дать дойти до этого.

Сейчас я заметил, что часто думаю еще от лица прошлого себя, а нужно перестраиваться. Тяжело воспринимать проблемы с деньгами, когда в прошлом мире за один заказ тебе могли заплатить несколько тонн золотых, и это еще со скидкой. Как воспринимать всерьез мелкие банды, когда я сражался с Архимагами и видел их слезы и боль, которую приносил им именно я.

Целые тысячелетние Ордены падали от наших рук, а здесь меня пытаются прижать родители, которые являются аристократами не самого высокого пошиба.

Вот и выходит, что вроде я прожил хорошую жизнь в прошлом, а ошибок в этой допускаю много, и можно было все сделать по другому. Но разве у меня есть карта моей жизни в голове, чтобы следовать по ней? Каждый их допускает и, наверное, не стоит больше об этом думать, хоть и досадно местами.

— Приехали! — сообщил мне водитель.
— Благодарю, — протягиваю ему несколько купюр и выхожу из машины.

Такс... А теперь посмотрим, куда это меня занесло. Я и раньше обследовал все по карте, но как показывает опыт, то карты очень и очень...
""".trimIndent()

private data class OpenTagInfo(val tagName: String, val startIndex: Int)

fun parseFormattedTextToAnnotatedString(
    text: String,
    baseFontSize: TextUnit
): AnnotatedString {
    return buildAnnotatedString {
        val tagRegex = Regex("""</?(strong|b|emphasis|i|em|strikethrough|s|strike|del|sup|sub|code|CHAPTER|title|h1|h2)[^>]*>""", RegexOption.IGNORE_CASE)
        val openTags = mutableListOf<OpenTagInfo>()
        var currentIndex = 0

        val matches = tagRegex.findAll(text)
        for (match in matches) {
            if (match.range.first > currentIndex) {
                append(text.substring(currentIndex, match.range.first))
            }

            val fullTag = match.value
            val isClosing = fullTag.startsWith("</")
            val rawTagName = match.groupValues[1].lowercase()

            val tagName = when (rawTagName) {
                "b" -> "strong"
                "i", "em" -> "emphasis"
                "s", "strike", "del" -> "strikethrough"
                "title", "h1", "h2" -> "chapter"
                else -> rawTagName
            }

            if (!isClosing) {
                openTags.add(OpenTagInfo(tagName, length))
            } else {
                val openTagIndex = openTags.indexOfLast { it.tagName == tagName }
                if (openTagIndex != -1) {
                    val openTag = openTags.removeAt(openTagIndex)
                    val start = openTag.startIndex
                    val end = length
                    if (end > start) {
                        applyTagStyle(tagName, start, end, baseFontSize)
                    }
                }
            }
            currentIndex = match.range.last + 1
        }

        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }

        for (openTag in openTags) {
            val start = openTag.startIndex
            val end = length
            if (end > start) {
                applyTagStyle(openTag.tagName, start, end, baseFontSize)
            }
        }
    }
}

private fun AnnotatedString.Builder.applyTagStyle(
    tagName: String,
    start: Int,
    end: Int,
    baseFontSize: TextUnit
) {
    val style = when (tagName) {
        "strong" -> SpanStyle(fontWeight = FontWeight.Bold)
        "emphasis" -> SpanStyle(fontStyle = FontStyle.Italic)
        "strikethrough" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        "sup" -> SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = baseFontSize * 0.75f)
        "sub" -> SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = baseFontSize * 0.75f)
        "code" -> SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22888888))
        "chapter" -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.5f)
        else -> null
    }
    if (style != null) {
        addStyle(style, start, end)
    }
}

fun AnnotatedString.trimTrailingWhitespace(): AnnotatedString {
    var end = length
    while (end > 0 && text[end - 1].isWhitespace()) {
        end--
    }
    return if (end == length) this else subSequence(0, end)
}

fun findPageForOffset(offsets: List<Int>, targetOffset: Int): Int {
    if (offsets.isEmpty()) return 0
    var bestPage = 0
    for (i in offsets.indices) {
        if (offsets[i] <= targetOffset) {
            bestPage = i
        } else {
            break
        }
    }
    return bestPage.coerceIn(0, (offsets.size - 1).coerceAtLeast(0))
}


