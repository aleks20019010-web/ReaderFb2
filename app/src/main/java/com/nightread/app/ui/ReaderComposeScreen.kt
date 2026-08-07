package com.nightread.app.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import com.nightread.app.data.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch

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
    var isSettingsOpen by remember { mutableStateOf(false) }
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

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
        ThemeType.DAY -> Color(0xFFEEF3E8) to Color(0xFF2A3A22)
        ThemeType.NIGHT -> Color(0xFF1A2216) to Color(0xFFD8E0D0)
        ThemeType.SEPIA -> Color(0xFFF8FAF0) to Color(0xFF5A6A4E)
        ThemeType.SEPIA_CONTRAST -> Color(0xFFF8FAF0) to Color(0xFF2A3A22)
    }

    val font = when (fontFamilyStr) {
        "Default" -> FontFamily.Default
        "Merriweather", "Serif", "Georgia", "Times New Roman", "Lora", "EB Garamond", "Literata" -> FontFamily.Serif
        "Roboto", "SansSerif", "OpenDyslexic" -> FontFamily.SansSerif
        "Monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
    
    val mappedFontWeight = FontWeight(fontWeightInt.coerceIn(100, 900))

    var pages by remember { mutableStateOf<List<String>>(emptyList()) }
    var isPreparingText by remember { mutableStateOf(true) }

    LaunchedEffect(mainText, fontSize, lineSpacing) {
        if (mainText.isEmpty()) {
            pages = emptyList()
            isPreparingText = false
        } else {
            isPreparingText = true
            val computedPages = withContext(Dispatchers.Default) {
                val words = mainText.split(Regex("(?<=\\s)"))
                val chunks = mutableListOf<String>()
                val currentChunk = StringBuilder()
                val charsPerPage = (800 * (18f / fontSize) * (1.2f / lineSpacing)).toInt().coerceAtLeast(300).coerceAtMost(1500)
                
                for (word in words) {
                    if (currentChunk.length + word.length > charsPerPage) {
                        chunks.add(currentChunk.toString().trimEnd())
                        currentChunk.clear()
                    }
                    currentChunk.append(word)
                }
                if (currentChunk.isNotEmpty()) chunks.add(currentChunk.toString().trimEnd())
                if (chunks.isEmpty()) listOf(mainText) else chunks
            }
            pages = computedPages
            isPreparingText = false
        }
    }

    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
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
            if (isLoading || isPreparingText) {
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
                val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                // Main Text Content as HorizontalPager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = if (isHideBars) (maxOf(statusBarHeight, 28.dp) + 12.dp) else maxOf(statusBarHeight + 56.dp, 72.dp),
                            bottom = navBarHeight + 16.dp,
                            start = 16.dp,
                            end = 16.dp
                        )
                ) { page ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        isHideBars = !isHideBars
                                    }
                                )
                            }
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Top
                    ) {
                        Text(
                            text = pages.getOrElse(page) { "" },
                            color = textColor,
                            fontSize = fontSize.sp,
                            fontFamily = font,
                            fontWeight = mappedFontWeight,
                            textAlign = TextAlign.Justify,
                            lineHeight = (fontSize * lineSpacing).sp,
                            letterSpacing = 0.1.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Top Panel
            AnimatedVisibility(
                visible = !isHideBars,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                    title = {
                        Column {
                            Text(
                                text = bookTitle,
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
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
                                ChapterListBottomSheet.newInstance(sha1, mainText).show(fm, "ChapterList")
                            }
                        }) {
                            Icon(Icons.Filled.List, contentDescription = "Оглавление", tint = textColor)
                        }
                        IconButton(onClick = {
                            activity?.supportFragmentManager?.let { fm ->
                                TtsControlBottomSheet.newInstance().show(fm, "TtsControl")
                            }
                        }) {
                            Icon(Icons.Filled.VolumeUp, contentDescription = "Озвучка", tint = textColor)
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
                    },
                )
            }

            // Bottom Panel
            AnimatedVisibility(
                visible = !isHideBars,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} из ${pagerState.pageCount}",
                        color = textColor,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
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
