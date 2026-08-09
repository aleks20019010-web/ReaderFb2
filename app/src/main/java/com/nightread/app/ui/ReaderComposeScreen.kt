package com.nightread.app.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.List
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
import kotlinx.coroutines.delay
import kotlin.math.abs

enum class ThemeType(val displayName: String) {
    DAY("День"),
    NIGHT("Ночь"),
    SEPIA("Сепия"),
    SEPIA_CONTRAST("Сепия контраст")
}

data class ReaderSettings(
    val themeType: ThemeType = ThemeType.SEPIA_CONTRAST,
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

    var isHideBars by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var isSettingsOpen by remember { mutableStateOf(false) }
    
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

    // Status bar and nav bar hiding
    LaunchedEffect(isHideBars) {
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            WindowCompat.setDecorFitsSystemWindows(window, !isHideBars)
            if (isHideBars) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val themeType = when (readingThemeStr.lowercase()) {
        "day", "light" -> ThemeType.DAY
        "night", "dark", "amoled" -> ThemeType.NIGHT
        "sepia" -> ThemeType.SEPIA
        "contrast" -> ThemeType.SEPIA_CONTRAST
        else -> ThemeType.SEPIA_CONTRAST
    }

    val (bgColor, textColor) = when (themeType) {
        ThemeType.DAY -> Color(0xFFEEF3E8) to Color(0xFF000000)
        ThemeType.NIGHT -> Color(0xFF1A2216) to Color(0xFFD8E0D0)
        ThemeType.SEPIA -> Color(0xFFF8FAF0) to Color(0xFF000000)
        ThemeType.SEPIA_CONTRAST -> Color(0xFFF8FAF0) to Color(0xFF000000)
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

    LaunchedEffect(sha1) {
        if (sha1.isNotEmpty()) {
            val record = com.nightread.app.data.SafeProgressManager.getInstance(context).loadProgressRecord(sha1)
            savedTextOffset = record.textOffset
            isRestoringProgress = true
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (!isRestoringProgress && pageStartOffsets.isNotEmpty() && sha1.isNotEmpty()) {
            val currentOffset = pageStartOffsets.getOrElse(pagerState.currentPage) { 0 }
            com.nightread.app.data.SafeProgressManager.getInstance(context).saveProgress(
                bookId = sha1,
                pageIndex = pagerState.currentPage,
                totalPages = readerPages.size,
                textOffset = currentOffset
            )
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                if (sha1.isNotEmpty() && pageStartOffsets.isNotEmpty()) {
                    val currentOffset = pageStartOffsets.getOrElse(pagerState.currentPage) { 0 }
                    com.nightread.app.data.SafeProgressManager.getInstance(context).saveProgressSync(
                        bookId = sha1,
                        pageIndex = pagerState.currentPage,
                        totalPages = readerPages.size,
                        textOffset = currentOffset
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (sha1.isNotEmpty() && pageStartOffsets.isNotEmpty()) {
                val currentOffset = pageStartOffsets.getOrElse(pagerState.currentPage) { 0 }
                com.nightread.app.data.SafeProgressManager.getInstance(context).saveProgressSync(
                    bookId = sha1,
                    pageIndex = pagerState.currentPage,
                    totalPages = readerPages.size,
                    textOffset = currentOffset
                )
            }
        }
    }

    val activity = context as? BookReaderActivity
    DisposableEffect(pagerState) {
        activity?.onNextPage = {
            coroutineScope.launch {
                if (pagerState.currentPage < pagerState.pageCount - 1) {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            }
        }
        activity?.onPrevPage = {
            coroutineScope.launch {
                if (pagerState.currentPage > 0) {
                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                }
            }
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
                            coroutineScope.launch {
                                if (pagerState.currentPage < pagerState.pageCount - 1) {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                            coroutineScope.launch {
                                if (pagerState.currentPage > 0) {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
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
                val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val cutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
                val cameraCutoutHeight = maxOf(statusBarHeight, cutoutTop)
                val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                // Container for reader content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    // Main Text Content as HorizontalPager with dynamic text measuring
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = if (isHideBars) (cameraCutoutHeight + 3.dp) else (cameraCutoutHeight + 64.dp + 3.dp),
                                bottom = if (isHideBars) (navBarHeight + 16.dp) else (navBarHeight + 72.dp + 16.dp),
                                start = 8.dp,
                                end = 8.dp
                            )
                    ) {
                        val density = LocalDensity.current
                        val maxWidthPx = with(density) { constraints.maxWidth }
                        val maxHeightPx = with(density) { constraints.maxHeight }

                        val readerTextStyle = remember(fontSize, font, mappedFontWeight, lineSpacing) {
                            TextStyle(
                                fontSize = fontSize.sp,
                                fontFamily = font,
                                fontWeight = mappedFontWeight,
                                textAlign = TextAlign.Justify,
                                lineHeight = (fontSize * lineSpacing).sp,
                                letterSpacing = 0.1.sp,
                                lineBreak = LineBreak.Paragraph,
                                hyphens = Hyphens.Auto,
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

                        LaunchedEffect(readerDocument, fontSize, lineSpacing, font, mappedFontWeight, maxWidthPx, maxHeightPx) {
                            val doc = readerDocument
                            if (maxWidthPx > 0 && maxHeightPx > 0 && doc != null) {
                                isPreparingText = true
                                val config = com.nightread.app.ui.customlayout.ReaderConfiguration(
                                    fontSize = fontSize.sp,
                                    fontFamily = font,
                                    fontWeight = mappedFontWeight,
                                    lineSpacing = lineSpacing,
                                    maxWidthPx = maxWidthPx,
                                    maxHeightPx = maxHeightPx
                                )
                                val viewport = com.nightread.app.ui.customlayout.ReaderViewport(
                                    widthPx = maxWidthPx,
                                    heightPx = maxHeightPx,
                                    density = density
                                )
                                com.nightread.app.ui.customlayout.ReaderLayoutEngine.paginate(
                                    document = doc,
                                    config = config,
                                    viewport = viewport,
                                    textMeasurer = textMeasurer,
                                    onPagesUpdated = { updatedPages, isFirstChunk ->
                                        readerPages = updatedPages
                                        val currentOffsets = updatedPages.map { it.startOffset }
                                        if (isRestoringProgress && currentOffsets.isNotEmpty()) {
                                            val targetPage = findPageForOffset(currentOffsets, savedTextOffset)
                                            coroutineScope.launch {
                                                pagerState.scrollToPage(targetPage)
                                                isRestoringProgress = false
                                            }
                                        }
                                        if (isFirstChunk) {
                                            isPreparingText = false
                                        }
                                    }
                                )
                                isPreparingText = false
                                val currentOffsets = readerPages.map { it.startOffset }
                                if (isRestoringProgress && currentOffsets.isNotEmpty()) {
                                    val targetPage = findPageForOffset(currentOffsets, savedTextOffset)
                                    pagerState.scrollToPage(targetPage)
                                    isRestoringProgress = false
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
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clipToBounds()
                                        .pointerInput(page) {
                                            detectTapGestures(
                                                onDoubleTap = {
                                                    isHideBars = !isHideBars
                                                    lastInteractionTime = System.currentTimeMillis()
                                                },
                                                onTap = { offset ->
                                                    val screenWidth = size.width
                                                    if (offset.x < screenWidth * 0.25f) {
                                                        coroutineScope.launch {
                                                            if (pagerState.currentPage > 0) {
                                                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                            }
                                                        }
                                                    } else if (offset.x > screenWidth * 0.75f) {
                                                        coroutineScope.launch {
                                                            if (pagerState.currentPage < pagerState.pageCount - 1) {
                                                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                            }
                                                        }
                                                    } else {
                                                        isHideBars = !isHideBars
                                                        lastInteractionTime = System.currentTimeMillis()
                                                    }
                                                }
                                            )
                                        }
                                        .pointerInput(page) {
                                            var startX = 0f
                                            detectVerticalDragGestures(
                                                onDragStart = { offset ->
                                                    startX = offset.x
                                                    lastInteractionTime = System.currentTimeMillis()
                                                },
                                                onVerticalDrag = { change, dragAmount ->
                                                    if (abs(dragAmount) > 1.5f) {
                                                        change.consume()
                                                        lastInteractionTime = System.currentTimeMillis()
                                                        val screenWidth = size.width
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
                                        },
                                    contentAlignment = Alignment.TopStart
                                ) {
                                    val readerPage = readerPages.getOrElse(page) { com.nightread.app.ui.customlayout.ReaderPage(0, AnnotatedString(""), 0, 0) }
                                    Text(
                                        text = readerPage.text,
                                        color = textColor,
                                        style = readerTextStyle,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = with(density) { maxHeightPx.toDp() })
                                            .clipToBounds()
                                    )
                                }
                            }
                        }
                    }

                    // Warmth (Amber filter) overlay
                    if (currentWarmth > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFFF8000).copy(alpha = (currentWarmth / 100f) * 0.35f))
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

            val glassBgColor = bgColor.copy(alpha = if (themeType == ThemeType.NIGHT || themeType == ThemeType.SEPIA_CONTRAST) 0.85f else 0.90f)
            val glassBorder = BorderStroke(1.dp, textColor.copy(alpha = 0.18f))

            // Top Panel (Glassmorphism)
            AnimatedVisibility(
                visible = !isHideBars,
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
                            val activity = context as? androidx.fragment.app.FragmentActivity
                            IconButton(onClick = {
                                activity?.supportFragmentManager?.let { fm ->
                                    BookRagSearchBottomSheet.newInstance().show(fm, "BookRagSearch")
                                }
                            }) {
                                Icon(Icons.Filled.Search, contentDescription = "Поиск", tint = textColor)
                            }
                            IconButton(onClick = {
                                activity?.supportFragmentManager?.let { fm ->
                                    val sheet = ChapterListBottomSheet.newInstance(sha1, mainText)
                                    sheet.setOnChapterClickListener { offset ->
                                        val targetPage = findPageForOffset(pageStartOffsets, offset)
                                        coroutineScope.launch {
                                            pagerState.scrollToPage(targetPage)
                                        }
                                    }
                                    sheet.show(fm, "ChapterList")
                                }
                            }) {
                                Icon(Icons.Filled.List, contentDescription = "Оглавление", tint = textColor)
                            }
                            IconButton(onClick = {
                                openTtsSettingsSheet(activity, mainText, bookTitle)
                            }) {
                                Icon(Icons.Filled.VolumeUp, contentDescription = "TTS Озвучка", tint = textColor)
                            }
                            IconButton(onClick = {
                                activity?.supportFragmentManager?.let { fm ->
                                    BookmarksListBottomSheet.newInstance(sha1).show(fm, "BookmarksList")
                                }
                            }) {
                                Icon(Icons.Filled.Bookmark, contentDescription = "Закладки", tint = textColor)
                            }
                            IconButton(onClick = {
                                activity?.supportFragmentManager?.let { fm ->
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
                visible = !isHideBars,
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
                    val currentPage = pagerState.currentPage
                    val displayPage = if (isDraggingSlider) (sliderPageValue.toInt() + 1).coerceIn(1, totalPages) else (currentPage + 1)
                    val percentage = if (totalPages > 0) ((displayPage.toFloat() / totalPages.toFloat()) * 100).toInt() else 0

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Страница $displayPage из $totalPages",
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "$percentage%",
                                color = textColor.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (maxPage > 0) {
                            Spacer(modifier = Modifier.height(2.dp))
                            val currentSliderVal = if (isDraggingSlider) sliderPageValue else currentPage.toFloat()
                            Slider(
                                value = currentSliderVal.coerceIn(0f, maxPage.toFloat()),
                                onValueChange = { newValue ->
                                    isDraggingSlider = true
                                    sliderPageValue = newValue
                                },
                                onValueChangeFinished = {
                                    val targetPage = sliderPageValue.toInt().coerceIn(0, maxPage)
                                    coroutineScope.launch {
                                        pagerState.scrollToPage(targetPage)
                                    }
                                    isDraggingSlider = false
                                },
                                valueRange = 0f..maxPage.toFloat(),
                                colors = SliderDefaults.colors(
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
                                    SliderDefaults.Track(
                                        sliderState = sliderState,
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = textColor,
                                            inactiveTrackColor = textColor.copy(alpha = 0.2f)
                                        ),
                                        modifier = Modifier.height(4.dp)
                                    )
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


