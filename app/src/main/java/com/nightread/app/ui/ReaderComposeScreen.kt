package com.nightread.app.ui

import android.app.Activity
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoAwesome

import com.nightread.app.data.BookmarkDatabase
import com.nightread.app.data.BookmarkEntity
import com.nightread.app.data.BookmarkRepository
import com.nightread.app.ui.customlayout.ReaderSearchEngine
import com.nightread.app.ui.customlayout.ReaderSearchResult
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.Job
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun Volumetric3DIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    vectorIcon: ImageVector? = null,
    contentDescription: String? = null,
    buttonSize: Dp = 38.dp,
    iconSize: Dp = 22.dp,
    bgGradient: List<Color>? = null,
    badgeColor: Color = Color(0x26FFFFFF), // Translucent white (rgba(255, 255, 255, 0.15))
    borderColor: Color = Color(0x4DFFFFFF), // 1px solid rgba(255, 255, 255, 0.3)
    tint: Color = Color.Unspecified
) {
    val finalTint = if (tint == Color.Unspecified) Color.White else tint

    Surface(
        onClick = onClick,
        modifier = modifier
            .size(buttonSize)
            .graphicsLayer {
                clip = true
            },
        shape = CircleShape,
        color = badgeColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 4.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (bgGradient != null) {
                        Modifier.background(Brush.verticalGradient(bgGradient))
                    } else {
                        Modifier.background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x38FFFFFF), // Specular glassy top reflection
                                    Color(0x12FFFFFF)  // Translucent glass body
                                )
                            )
                        )
                    }
                )
        ) {
            if (vectorIcon != null) {
                Icon(
                    imageVector = vectorIcon,
                    contentDescription = contentDescription,
                    tint = finalTint,
                    modifier = Modifier.size(iconSize)
                )
            } else if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = contentDescription,
                    tint = finalTint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

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
    val isStatusBarEnabled = remember(settingsVersion, context) { SettingsManager.isStatusBarEnabled(context) }
    var activeFootnoteText by remember { mutableStateOf<String?>(null) }
    val isHyphenationEnabled = remember(settingsVersion, context) { SettingsManager.isHyphenationEnabled(context) }
    val isSleepTimerEnabled = remember(settingsVersion, context) { SettingsManager.isSleepTimerEnabled(context) }
    val sleepTimerDuration = remember(settingsVersion, context) { SettingsManager.getSleepTimerDuration(context) }
    val pageAnimation = remember(settingsVersion, context) { SettingsManager.getPageAnimation(context) }
    val isDistractionFreeSaved = remember(settingsVersion, context) { SettingsManager.isDistractionFreeEnabled(context) }
    var sleepTimerRemaining by remember { mutableLongStateOf(0L) }

    var isDistractionFreeMode by remember { mutableStateOf(isDistractionFreeSaved) }
    var isHideBars by remember { mutableStateOf(isDistractionFreeSaved) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(isDistractionFreeSaved) {
        if (isDistractionFreeMode != isDistractionFreeSaved) {
            isDistractionFreeMode = isDistractionFreeSaved
            if (isDistractionFreeSaved) {
                isHideBars = true
            }
        }
    }

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

    // Unified full-screen immersion: hides ALL system bars when bars are hidden (pure text mode)
    val isFullScreenCleanMode = isHideBars || isDistractionFreeMode
    DisposableEffect(window, themeType, isFullScreenCleanMode) {
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, view)
            val isDarkTheme = themeType == ThemeType.NIGHT
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
            insetsController.isAppearanceLightNavigationBars = !isDarkTheme
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT

            if (isFullScreenCleanMode) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val (bgColor, textColor) = when (themeType) {
        ThemeType.DAY -> Color(0xFFFBF9F1) to Color.Black
        ThemeType.NIGHT -> Color.Black to Color(0xFFE5E5E5)
        ThemeType.SEPIA -> Color(0xFFF4ECD8) to Color.Black
        ThemeType.HIGH_CONTRAST -> Color(0xFFFFFFFF) to Color.Black
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
    var currentWebViewPage by remember { mutableIntStateOf(0) }
    var totalWebViewPages by remember { mutableIntStateOf(1) }
    var nextSeriesBook by remember(sha1) { mutableStateOf<com.nightread.app.data.BookEntity?>(null) }
    var nextSeriesPositionText by remember(sha1) { mutableStateOf("") }
    var isNextSeriesCardDismissed by remember(sha1) { mutableStateOf(false) }

    LaunchedEffect(sha1) {
        if (sha1.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val dao = com.nightread.app.data.AppDatabase.getDatabase(context).bookDao()
                    val currentBook = dao.getBookBySha1(sha1)
                    val seriesName = currentBook?.series?.trim()
                    if (!seriesName.isNullOrEmpty()) {
                        val seriesBooks = dao.getBooksBySeriesSync(seriesName)
                        val idx = seriesBooks.indexOfFirst { it.sha1 == sha1 }
                        if (idx != -1 && idx + 1 < seriesBooks.size) {
                            nextSeriesBook = seriesBooks[idx + 1]
                            nextSeriesPositionText = "${idx + 2} из ${seriesBooks.size}"
                        } else {
                            nextSeriesBook = null
                            nextSeriesPositionText = ""
                        }
                    } else {
                        nextSeriesBook = null
                        nextSeriesPositionText = ""
                    }
                } catch (_: Exception) {
                    nextSeriesBook = null
                }
            }
        }
    }
    
    val pagerState = rememberPagerState(pageCount = { readerPages.size.coerceAtLeast(1) })

    val currentReadingOffset = remember(pagerState.currentPage, readerPages, savedTextOffset) {
        if (readerPages.isNotEmpty() && pagerState.currentPage < readerPages.size) {
            readerPages[pagerState.currentPage].startOffset
        } else {
            savedTextOffset
        }
    }

    LaunchedEffect(settingsVersion) {
        if (settingsVersion > 0) {
            val target = if (savedTextOffset > 0) savedTextOffset else currentReadingOffset
            if (target > 0) {
                pendingTargetOffset = target
                isRestoringProgress = true
            }
        }
    }
    var isPreparingText by remember { mutableStateOf(true) }
    val textMeasurer = rememberTextMeasurer()

    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }

    // Reliable vibration helper (Android 13+ VibrationAttributes + fallback)
    val triggerReaderVibration: (Boolean) -> Unit = remember(context) {
        { isWakeUp ->
            try {
                val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                }
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (isWakeUp) {
                        val timings = longArrayOf(0L, 400L, 150L, 400L, 150L, 500L)
                        val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            val attrs = android.os.VibrationAttributes.Builder()
                                .setUsage(android.os.VibrationAttributes.USAGE_ALARM)
                                .build()
                            vibrator.vibrate(android.os.VibrationEffect.createWaveform(timings, amplitudes, -1), attrs)
                        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            val attrs = android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(android.os.VibrationEffect.createWaveform(timings, amplitudes, -1), attrs)
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(timings, -1)
                        }
                    } else {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            val attrs = android.os.VibrationAttributes.Builder()
                                .setUsage(android.os.VibrationAttributes.USAGE_ALARM)
                                .build()
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(35L, 255), attrs)
                        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            val attrs = android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(35L, 255), attrs)
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(35L)
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore vibration errors
            }
        }
    }

    // Silent Mode effect (mute view click sound effects during reading)
    LaunchedEffect(isSilentModeEnabled, webViewRef) {
        try {
            view.isSoundEffectsEnabled = !isSilentModeEnabled
            webViewRef?.isSoundEffectsEnabled = !isSilentModeEnabled
        } catch (e: Exception) {
            // Ignore if not supported
        }
    }

    // Wake-up strong vibration + auto screen-off if no page turn in Silent Mode
    LaunchedEffect(isSilentModeEnabled, currentWebViewPage, pagerState.currentPage) {
        val act = context as? Activity
        if (!isSilentModeEnabled) return@LaunchedEffect
        try {
            act?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Exception) {}
        delay(3 * 60 * 1000L)
        triggerReaderVibration(true)
        repeat(2) {
            delay(2 * 60 * 1000L)
            triggerReaderVibration(true)
        }
        // User did not wake up after 3 vibrations -> allow screen to turn off
        delay(60 * 1000L)
        try {
            act?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val lp = act?.window?.attributes
            if (lp != null) {
                lp.screenBrightness = 0.01f
                act.window.attributes = lp
            }
        } catch (_: Exception) {}
    }

    // Sleep Timer logic
    LaunchedEffect(isSleepTimerEnabled, sleepTimerDuration) {
        if (isSleepTimerEnabled) {
            sleepTimerRemaining = sleepTimerDuration * 60 * 1000L
            while (sleepTimerRemaining > 0 && isSleepTimerEnabled) {
                delay(1000L)
                sleepTimerRemaining -= 1000L
            }
            if (isSleepTimerEnabled && sleepTimerRemaining <= 0) {
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
            val spOffset = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE).getInt("book_char_offset_$sha1", 0)
            val safeRecord = com.nightread.app.data.SafeProgressManager.getInstance(context).loadProgressRecord(sha1)
            val restoredOffset = maxOf(progress?.sourceOffset ?: 0, spOffset, safeRecord.textOffset)

            savedTextOffset = restoredOffset
            if (safeRecord.pageIndex >= 0) {
                currentWebViewPage = safeRecord.pageIndex
            }
            if (safeRecord.totalPages > 0) {
                totalWebViewPages = safeRecord.totalPages
            }

            if (restoredOffset > 0) {
                pendingTargetOffset = restoredOffset
            } else {
                isRestoringProgress = false
            }
            android.util.Log.d("ReadingProgress", "RESTORE bookId=$sha1 offset=$restoredOffset page=${safeRecord.pageIndex}/${safeRecord.totalPages}")
        }
    }

    LaunchedEffect(savedTextOffset, sha1, currentWebViewPage, totalWebViewPages) {
        if (!isRestoringProgress && sha1.isNotEmpty() && savedTextOffset >= 0) {
            delay(400) // Debounce 400ms
            
            android.util.Log.d("ReadingProgress", "SAVE (debounce) bookId=$sha1 offset=$savedTextOffset page=$currentWebViewPage/$totalWebViewPages")
            progressRepository.saveProgress(
                com.nightread.app.data.ReadingProgress(
                    bookId = sha1,
                    sourceOffset = savedTextOffset,
                    updatedAt = System.currentTimeMillis()
                )
            )
            com.nightread.app.data.SafeProgressManager.getInstance(context).saveProgressSync(
                bookId = sha1,
                pageIndex = currentWebViewPage,
                totalPages = totalWebViewPages,
                textOffset = savedTextOffset
            )
            context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE).edit()
                .putInt("book_char_offset_$sha1", savedTextOffset)
                .putLong("book_last_timestamp_$sha1", System.currentTimeMillis())
                .apply()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, savedTextOffset, currentWebViewPage, totalWebViewPages) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                val offsetToSave = savedTextOffset
                val pageToSave = currentWebViewPage
                val totalToSave = totalWebViewPages
                if (sha1.isNotEmpty() && offsetToSave >= 0) {
                    android.util.Log.d("ReadingProgress", "SAVE (lifecycle) bookId=$sha1 offset=$offsetToSave")
                    
                    kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                        try {
                            progressRepository.saveProgress(
                                com.nightread.app.data.ReadingProgress(
                                    bookId = sha1,
                                    sourceOffset = offsetToSave,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            com.nightread.app.data.SafeProgressManager.getInstance(context).saveProgressSync(
                                bookId = sha1,
                                pageIndex = pageToSave,
                                totalPages = totalToSave,
                                textOffset = offsetToSave
                            )
                            context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE).edit()
                                .putInt("book_char_offset_$sha1", offsetToSave)
                                .putLong("book_last_timestamp_$sha1", System.currentTimeMillis())
                                .commit()
                        } catch (e: Exception) {
                            android.util.Log.e("ReadingProgress", "Error during lifecycle save", e)
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val offsetToSave = savedTextOffset
            val pageToSave = currentWebViewPage
            val totalToSave = totalWebViewPages
            if (sha1.isNotEmpty() && offsetToSave >= 0) {
                android.util.Log.d("ReadingProgress", "SAVE (dispose) bookId=$sha1 offset=$offsetToSave")
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    try {
                        progressRepository.saveProgress(
                            com.nightread.app.data.ReadingProgress(
                                bookId = sha1,
                                sourceOffset = offsetToSave,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        com.nightread.app.data.SafeProgressManager.getInstance(context).saveProgressSync(
                            bookId = sha1,
                            pageIndex = pageToSave,
                            totalPages = totalToSave,
                            textOffset = offsetToSave
                        )
                        context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE).edit()
                            .putInt("book_char_offset_$sha1", offsetToSave)
                            .putLong("book_last_timestamp_$sha1", System.currentTimeMillis())
                            .commit()
                    } catch (e: Exception) {
                        android.util.Log.e("ReadingProgress", "Error during dispose save", e)
                    }
                }
            }
        }
    }

    val activity = context as? BookReaderActivity
    DisposableEffect(webViewRef) {
        activity?.onNextPage = {
            webViewRef?.evaluateJavascript("window.nextPage();", null)
            if (SettingsManager.isHapticFeedbackEnabled(context)) triggerReaderVibration(false)
        }
        activity?.onPrevPage = {
            webViewRef?.evaluateJavascript("window.prevPage();", null)
            if (SettingsManager.isHapticFeedbackEnabled(context)) triggerReaderVibration(false)
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
                            if (SettingsManager.isHapticFeedbackEnabled(context)) triggerReaderVibration(false)
                            true
                        }
                        android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                            webViewRef?.evaluateJavascript("window.prevPage();", null)
                            if (SettingsManager.isHapticFeedbackEnabled(context)) triggerReaderVibration(false)
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
                        val configuration = LocalConfiguration.current
                        
                        // Use screen-level configuration dimensions so system bars hiding/showing does not change viewport
                        val widthDp = configuration.screenWidthDp
                        val heightDp = configuration.screenHeightDp
                        val maxWidthPx = with(density) { widthDp.dp.roundToPx() }
                        val maxHeightPx = with(density) { heightDp.dp.roundToPx() }
                        
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
                        
                        LaunchedEffect(maxWidthPx, maxHeightPx) {
                            if (oldWidthPx != 0 && oldHeightPx != 0 && (oldWidthPx != maxWidthPx || oldHeightPx != maxHeightPx)) {
                                android.util.Log.e("ReaderViewport", "ERROR: Viewport changed! This will cause repagination.\nold viewport:\nwidth=$oldWidthPx\nheight=$oldHeightPx\n\nnew viewport:\nwidth=$maxWidthPx\nheight=$maxHeightPx")
                            } else {
                                android.util.Log.d("ReaderViewport", "viewport stable\nwidth=$maxWidthPx\nheight=$maxHeightPx")
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

                        LaunchedEffect(mainText, sha1, fontSize) {
                            if (mainText.isNotEmpty()) {
                                val cacheKey = "$sha1-${fontSize}"
                                val cachedDoc = com.nightread.app.data.ReaderDocumentCache.getDocument(cacheKey)
                                if (cachedDoc != null) {
                                    readerDocument = cachedDoc
                                } else {
                                    val doc = com.nightread.app.ui.customlayout.ReaderLayoutEngine.parseDocument(
                                        bookId = sha1.ifEmpty { "default" },
                                        mainText = mainText,
                                        baseFontSize = fontSize.sp
                                    )
                                    com.nightread.app.data.ReaderDocumentCache.putDocument(cacheKey, doc)
                                    readerDocument = doc
                                }
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
                                        
                                        val targetToRestore = pendingTargetOffset ?: targetOffset
                                        if (targetToRestore > 0) {
                                            val targetPage = findPageForOffset(updatedPages.map { it.startOffset }, targetToRestore)
                                            if (targetPage != -1) {
                                                pagerState.scrollToPage(targetPage)
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
                            val displayCutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
                            val topPaddingDpVal = remember(configuration.orientation) {
                                val rawCutout = displayCutoutTop.value.toInt()
                                if (rawCutout > 0) rawCutout + 3 else 28
                            }
                            val leftPaddingDpVal = 8
                            val rightPaddingDpVal = 8
                            val bottomPaddingDpVal = if (isStatusBarEnabled) 24 else 4

                            val currentTargetOffsetForHtml = if (savedTextOffset > 0) savedTextOffset else currentReadingOffset

                            val htmlContent = remember(mainText, widthDp, heightDp, topPaddingDpVal, leftPaddingDpVal, rightPaddingDpVal, bottomPaddingDpVal) {
                                com.nightread.app.ui.customlayout.ReaderWebViewEngine.prepareHtmlForBook(
                                    context = context,
                                    bookId = sha1.ifEmpty { "default" },
                                    mainText = mainText,
                                    fontFamily = fontFamilyStr,
                                    fontSize = fontSize,
                                    fontWeight = mappedFontWeight.weight.toFloat(),
                                    lineHeight = lineSpacing,
                                    textColorHex = textColorHex,
                                    bgColorHex = bgColorHex,
                                    viewportWidth = widthDp,
                                    viewportHeight = heightDp,
                                    pageAnimation = pageAnimation,
                                    topPaddingDp = topPaddingDpVal,
                                    bottomPaddingDp = bottomPaddingDpVal,
                                    leftPaddingDp = leftPaddingDpVal,
                                    rightPaddingDp = rightPaddingDpVal,
                                    isHyphenationEnabled = isHyphenationEnabled,
                                    initialTargetOffset = currentTargetOffsetForHtml
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
                                    fontFamily = fontFamilyStr,
                                    fontSize = fontSize,
                                    fontWeight = mappedFontWeight.weight.toFloat(),
                                    lineHeight = lineSpacing,
                                    textColorHex = textColorHex,
                                    bgColorHex = bgColorHex,
                                    isHyphenationEnabled = isHyphenationEnabled,
                                    pageAnimation = pageAnimation,
                                    topPaddingDp = topPaddingDpVal,
                                    bottomPaddingDp = bottomPaddingDpVal,
                                    leftPaddingDp = leftPaddingDpVal,
                                    rightPaddingDp = rightPaddingDpVal,
                                    themeColor = textColor,
                                    bgColor = Color.Transparent,
                                    currentPage = currentWebViewPage,
                                    targetOffset = pendingTargetOffset,
                                    onTargetOffsetHandled = {
                                        pendingTargetOffset = null
                                        isRestoringProgress = false
                                    },
                                    onPositionChanged = { offset, page, total ->
                                        if (page >= 0) currentWebViewPage = page
                                        if (total > 0) totalWebViewPages = total
                                        if (offset >= 0) {
                                            savedTextOffset = offset
                                            isRestoringProgress = false
                                        }
                                    },
                                    onWordSelected = { selectedText ->
                                        if (selectedText.isNotBlank()) {
                                            fragmentActivity?.supportFragmentManager?.let { fm ->
                                                val sheet = SelectionBottomSheet.newInstance(selectedText)
                                                sheet.show(fm, "SelectionBottomSheet")
                                            }
                                        }
                                    },
                                    onNoteClicked = { noteId ->
                                        val cleanId = noteId.trim().removePrefix("#").replace("'", "\\'")
                                        val wv = webViewRef
                                        if (wv != null && cleanId.isNotEmpty()) {
                                            val js = "(function(){ var el = document.getElementById('$cleanId') || document.querySelector('[name=\"$cleanId\"]'); return el ? (el.innerText || el.textContent || '') : ''; })()"
                                            wv.evaluateJavascript(js) { raw ->
                                                val decoded = raw
                                                    ?.trim()
                                                    ?.removeSurrounding("\"")
                                                    ?.replace("\\n", "\n")
                                                    ?.replace("\\\"", "\"")
                                                    ?.trim()
                                                activeFootnoteText = if (!decoded.isNullOrBlank() && decoded != "null") decoded else noteId
                                            }
                                        } else if (noteId.isNotBlank()) {
                                            activeFootnoteText = noteId
                                        }
                                    },
                                    onNextPage = { if (SettingsManager.isHapticFeedbackEnabled(context)) triggerReaderVibration(false) },
                                    onPreviousPage = { if (SettingsManager.isHapticFeedbackEnabled(context)) triggerReaderVibration(false) },
                                    onWebViewCreated = { webView ->
                                        webViewRef = webView
                                    },
                                    onTopLeftTap = {
                                        val currentTheme = SettingsManager.getReadingTheme(context).lowercase()
                                        val isCurrentlyNight = SettingsManager.isNightTheme(currentTheme)
                                        val targetTheme = if (isCurrentlyNight) "sepia" else "amoled"
                                        SettingsManager.setReadingTheme(context, targetTheme)
                                        settingsVersion++
                                        gestureIndicatorText = if (isCurrentlyNight) "Тема: Сепия" else "Тема: Ночь"
                                        gestureIndicatorIcon = Icons.Filled.WbSunny
                                        showGestureIndicatorTime = System.currentTimeMillis()
                                    },
                                    onTopRightTap = {
                                        val offsetNow = if (savedTextOffset > 0) savedTextOffset else currentReadingOffset
                                        val existing = bookmarks.any { kotlin.math.abs(it.charOffset - offsetNow) < 50 }
                                        coroutineScope.launch {
                                            if (existing) {
                                                val target = bookmarks.firstOrNull { kotlin.math.abs(it.charOffset - offsetNow) < 50 }
                                                if (target != null) {
                                                    bookmarkRepo.deleteBookmarkAtOffset(sha1, target.charOffset)
                                                }
                                            } else {
                                                bookmarkRepo.insertBookmark(
                                                    BookmarkEntity(
                                                        bookSha1 = sha1,
                                                        bookTitle = bookTitle,
                                                        charOffset = offsetNow,
                                                        pageIndex = currentWebViewPage,
                                                        snippet = "Стр. ${currentWebViewPage + 1}",
                                                        timestamp = System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                        }
                                        if (SettingsManager.isHapticFeedbackEnabled(context)) triggerReaderVibration(false)
                                        gestureIndicatorText = if (existing) "Закладка удалена" else "Закладка добавлена"
                                        gestureIndicatorIcon = if (existing) Icons.Filled.BookmarkBorder else Icons.Filled.Bookmark
                                        showGestureIndicatorTime = System.currentTimeMillis()
                                    },
                                    onToggleBars = {
                                        val nextHidden = !(isHideBars || isDistractionFreeMode)
                                        isHideBars = nextHidden
                                        isDistractionFreeMode = nextHidden
                                        SettingsManager.setDistractionFreeEnabled(context, nextHidden)
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

            // Next book in series card on the last page
            val nextBook = nextSeriesBook
            val isOnLastPage = totalWebViewPages > 1 && currentWebViewPage >= totalWebViewPages - 1
            AnimatedVisibility(
                visible = nextBook != null && isOnLastPage && !isNextSeriesCardDismissed && !isSearchMode,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = if (!isHideBars) 128.dp else 20.dp)
            ) {
                nextBook?.let { nb ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF192236).copy(alpha = 0.95f),
                        border = BorderStroke(1.dp, Color(0xFF00BCD4).copy(alpha = 0.45f)),
                        shadowElevation = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Следующая в серии" + if (nextSeriesPositionText.isNotEmpty()) " ($nextSeriesPositionText)" else "",
                                    color = Color(0xFF00BCD4),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = { isNextSeriesCardDismissed = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Закрыть",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = nb.title.ifBlank { "Без названия" },
                                color = Color(0xFFF1F5F9),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (!nb.series.isNullOrBlank()) {
                                Text(
                                    text = nb.series,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = {
                                    val openNext = Intent(context, BookReaderActivity::class.java).apply {
                                        putExtra("BOOK_SHA1", nb.sha1)
                                    }
                                    context.startActivity(openNext)
                                    (context as? Activity)?.finish()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp)
                            ) {
                                Text(
                                    text = "Читать дальше →",
                                    color = Color(0xFF0A101F),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // BottomStatusBarOverlay (thin status bar at the very bottom)
            if (isStatusBarEnabled && !isSearchMode && isHideBars) {
                var timeText by remember { mutableStateOf("") }
                var batteryPct by remember { mutableIntStateOf(-1) }
                LaunchedEffect(Unit) {
                    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                    while (true) {
                        timeText = fmt.format(java.util.Date())
                        batteryPct = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                        delay(30_000L)
                    }
                }
                val isNightNow = SettingsManager.isNightTheme(readingThemeStr.lowercase())
                val statusColor = if (isNightNow) Color(0xFFA8A49B).copy(alpha = 0.6f) else Color(0xFF3E3529).copy(alpha = 0.65f)
                val pct = if (totalWebViewPages > 1) ((currentWebViewPage + 1) * 100 / totalWebViewPages).coerceIn(0, 100) else 0
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 36.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (batteryPct in 0..100) "$timeText  •  $batteryPct%" else timeText,
                        color = statusColor,
                        fontSize = 11.sp
                    )
                    Text(
                        text = if (totalWebViewPages > 1) "${currentWebViewPage + 1} / $totalWebViewPages  ($pct%)" else "$pct%",
                        color = statusColor,
                        fontSize = 11.sp
                    )
                }
            }

            // Popup Footnote Card
            AnimatedVisibility(
                visible = activeFootnoteText != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 28.dp)
            ) {
                activeFootnoteText?.let { noteBody ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF192236).copy(alpha = 0.96f),
                        border = BorderStroke(1.dp, Color(0xFF00BCD4).copy(alpha = 0.4f)),
                        shadowElevation = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Примечание",
                                    color = Color(0xFF00BCD4),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = { activeFootnoteText = null },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Закрыть",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = noteBody,
                                color = Color(0xFFF1F5F9),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            val glassBgColor = Color(0xFF12141A).copy(alpha = 0.96f)
            val glassBorder = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.12f))
            val panelTextColor = Color(0xFFF4F5F7)
            val accentGold = Color(0xFFE5C07B)

            // Top Panel (Modern Minimalist Dark Glass)
            AnimatedVisibility(
                visible = !isHideBars && !isSearchMode,
                enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { -it / 2 }),
                exit = fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { -it / 2 }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = glassBgColor,
                    border = glassBorder,
                    shadowElevation = 12.dp
                ) {
                    val fragmentActivity = context as? androidx.fragment.app.FragmentActivity
                    val currentOffset = if (savedTextOffset > 0) savedTextOffset else currentReadingOffset
                    val isBookmarked = bookmarks.any { kotlin.math.abs(it.charOffset - currentOffset) < 50 }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx -> LiquidWaveBackgroundView(ctx).apply { setAlpha(0.24f) } },
                            modifier = Modifier.matchParentSize()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = panelTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                        ) {
                            Text(
                                text = bookTitle,
                                color = panelTextColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = authorAndChapter,
                                color = panelTextColor.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        if (isBookmarked) {
                                            val target = bookmarks.firstOrNull { kotlin.math.abs(it.charOffset - currentOffset) < 50 }
                                            if (target != null) bookmarkRepo.deleteBookmarkAtOffset(sha1, target.charOffset)
                                        } else {
                                            bookmarkRepo.insertBookmark(
                                                BookmarkEntity(
                                                    bookSha1 = sha1,
                                                    bookTitle = bookTitle,
                                                    charOffset = currentOffset,
                                                    pageIndex = currentWebViewPage,
                                                    snippet = "Стр. ${currentWebViewPage + 1}",
                                                    timestamp = System.currentTimeMillis()
                                                )
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = "Закладка",
                                    tint = if (isBookmarked) accentGold else panelTextColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { showTocSheet = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.List,
                                    contentDescription = "Оглавление",
                                    tint = panelTextColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    fragmentActivity?.supportFragmentManager?.let { fm ->
                                        BookmarksListBottomSheet.newInstance(sha1).show(fm, "BookmarksList")
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bookmarks,
                                    contentDescription = "Список закладок",
                                    tint = panelTextColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val newMode = !(isHideBars || isDistractionFreeMode)
                                    isDistractionFreeMode = newMode
                                    isHideBars = newMode
                                    SettingsManager.setDistractionFreeEnabled(context, newMode)
                                },
                                modifier = Modifier.size(36.dp).testTag("btn_distraction_free")
                            ) {
                                Icon(
                                    imageVector = if (isDistractionFreeMode) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                    contentDescription = "Полноэкранный режим",
                                    tint = if (isDistractionFreeMode) accentGold else panelTextColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    fragmentActivity?.supportFragmentManager?.let { fm ->
                                        SettingsBottomSheet().show(fm, "SettingsBottomSheet")
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Настройки",
                                    tint = panelTextColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    }
                }
            }

            // Bottom Panel (Modern Minimalist Dark Glass)
            AnimatedVisibility(
                visible = !isHideBars && !isSearchMode,
                enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                var isDraggingSlider by remember { mutableStateOf(false) }
                var sliderPageValue by remember { mutableStateOf(0f) }

                val totalChars = mainText.length.coerceAtLeast(1)
                val currentPercent = if (totalWebViewPages > 1) {
                    ((currentWebViewPage + 1).toFloat() / totalWebViewPages * 100f).coerceIn(0f, 100f)
                } else {
                    (savedTextOffset.toFloat() / totalChars * 100f).coerceIn(0f, 100f)
                }
                val currentSliderVal = if (isDraggingSlider) sliderPageValue else currentPercent
                val draggedOffset = ((sliderPageValue / 100f) * totalChars).toInt().coerceIn(0, totalChars)
                val draggedChapterTitle = remember(isDraggingSlider, draggedOffset, readerDocument) {
                    if (!isDraggingSlider) ""
                    else readerDocument?.chapters.orEmpty()
                        .lastOrNull { it.startOffset <= draggedOffset }
                        ?.title
                        ?.trim()
                        .orEmpty()
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(
                        visible = isDraggingSlider && draggedChapterTitle.isNotEmpty(),
                        enter = fadeIn(tween(120)),
                        exit = fadeOut(tween(120))
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = glassBgColor,
                            border = BorderStroke(0.8.dp, accentGold.copy(alpha = 0.45f)),
                            shadowElevation = 8.dp,
                            modifier = Modifier.padding(bottom = 8.dp, start = 12.dp, end = 12.dp)
                        ) {
                            Text(
                                text = draggedChapterTitle,
                                color = accentGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = glassBgColor,
                        border = glassBorder,
                        shadowElevation = 12.dp
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx -> LiquidWaveBackgroundView(ctx).apply { setAlpha(0.24f) } },
                            modifier = Modifier.matchParentSize()
                        )
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (totalWebViewPages > 1) "Стр. ${currentWebViewPage + 1} из $totalWebViewPages" else "Прогресс",
                                color = panelTextColor.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = panelTextColor.copy(alpha = 0.10f)
                            ) {
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f%%", currentSliderVal),
                                    color = panelTextColor.copy(alpha = 0.90f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
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
                                thumbColor = accentGold,
                                activeTrackColor = accentGold,
                                inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                            ),
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(accentGold, CircleShape)
                                        .border(1.5.dp, Color(0xFF12141A), CircleShape)
                                )
                            },
                            track = { sliderState ->
                                val fraction = ((sliderState.value - sliderState.valueRange.start) /
                                    (sliderState.valueRange.endInclusive - sliderState.valueRange.start).coerceAtLeast(0.001f))
                                    .coerceIn(0f, 1f)
                                val chapters = readerDocument?.chapters.orEmpty()
                                androidx.compose.foundation.Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(12.dp)
                                ) {
                                    val centerY = size.height / 2f
                                    val trackH = 3.dp.toPx()
                                    val activeEndX = size.width * fraction

                                    // ?????????? ?????? ?????
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.16f),
                                        start = androidx.compose.ui.geometry.Offset(0f, centerY),
                                        end = androidx.compose.ui.geometry.Offset(size.width, centerY),
                                        strokeWidth = trackH,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                    // ???????? ??????? ?????
                                    if (activeEndX > 0f) {
                                        drawLine(
                                            color = accentGold,
                                            start = androidx.compose.ui.geometry.Offset(0f, centerY),
                                            end = androidx.compose.ui.geometry.Offset(activeEndX, centerY),
                                            strokeWidth = trackH,
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                                        )
                                    }
                                    // ?????????? ??????? ????
                                    if (chapters.size > 1) {
                                        val tickW = 1.dp.toPx()
                                        val tickHalf = 3.5.dp.toPx()
                                        chapters.forEach { ch ->
                                            val frac = (ch.startOffset.toFloat() / totalChars.toFloat()).coerceIn(0f, 1f)
                                            if (frac > 0.01f && frac < 0.99f) {
                                                val x = size.width * frac
                                                drawLine(
                                                    color = if (x <= activeEndX) Color(0xFF12141A).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.35f),
                                                    start = androidx.compose.ui.geometry.Offset(x, centerY - tickHalf),
                                                    end = androidx.compose.ui.geometry.Offset(x, centerY + tickHalf),
                                                    strokeWidth = tickW
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                        )
                    }
                    }
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
                                placeholder = { Text("Поиск...", color = panelTextColor.copy(alpha = 0.5f)) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = panelTextColor,
                                    unfocusedTextColor = panelTextColor,
                                    cursorColor = panelTextColor,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true
                            )
                            
                            Volumetric3DIconButton(
                                onClick = {
                                    isSearchMode = false
                                    searchQuery = ""
                                    searchResults = emptyList()
                                    currentSearchIndex = -1
                                    isHideBars = false
                                },
                                vectorIcon = Icons.Filled.Close,
                                contentDescription = "Закрыть",
                                tint = Color.White,
                                buttonSize = 36.dp,
                                iconSize = 20.dp
                            )
                        }
                        
                        if (searchResults.isNotEmpty()) {
                            androidx.compose.material3.Divider(color = panelTextColor.copy(alpha = 0.12f))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("${currentSearchIndex + 1} из ${searchResults.size}", color = panelTextColor)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Volumetric3DIconButton(
                                        onClick = {
                                            if (currentSearchIndex > 0) {
                                                currentSearchIndex--
                                                pendingTargetOffset = searchResults[currentSearchIndex].sourceStartOffset
                                            }
                                        },
                                        vectorIcon = Icons.Filled.KeyboardArrowUp,
                                        contentDescription = "Предыдущий",
                                        tint = Color.White,
                                        buttonSize = 36.dp,
                                        iconSize = 20.dp
                                    )
                                    Volumetric3DIconButton(
                                        onClick = {
                                            if (currentSearchIndex < searchResults.size - 1) {
                                                currentSearchIndex++
                                                pendingTargetOffset = searchResults[currentSearchIndex].sourceStartOffset
                                            }
                                        },
                                        vectorIcon = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Следующий",
                                        tint = Color.White,
                                        buttonSize = 36.dp,
                                        iconSize = 20.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Fullscreen clean-text mode leaves zero floating overlay buttons (center tap toggles UI)
        }

        // --- Table of Contents Bottom Sheet ---
        if (showTocSheet && readerDocument != null) {
            ModalBottomSheet(
                onDismissRequest = { showTocSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF12141A),
                contentColor = Color(0xFFF4F5F7),
                tonalElevation = 8.dp,
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFFE5C07B).copy(alpha = 0.5f)) }
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
                        color = Color(0xFFF4F5F7)
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
                                        color = if (isCurrent) Color(0xFFE5C07B) else Color(0xFFF4F5F7)
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "Глава ${chapter.chapterIndex + 1}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF9BA1AE)
                                    )
                                },
                                modifier = Modifier.clickable {
                                    pendingTargetOffset = chapter.startOffset
                                    showTocSheet = false
                                    isHideBars = true
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            HorizontalDivider(color = Color(0xFF282D38))
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


