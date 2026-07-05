package com.nightread.app.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
    bookTitle: String = "Орден Архитекторов",
    authorAndChapter: String = "Олег Сапфир, Юрий Винокуров | Глава 11",
    mainText: String = sampleText,
    onBackClick: () -> Unit = {}
) {
    var settings by remember { mutableStateOf(ReaderSettings()) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val view = LocalView.current
    val window = (context as? Activity)?.window
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // Status bar and nav bar hiding
    LaunchedEffect(settings.isHideBars) {
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            WindowCompat.setDecorFitsSystemWindows(window, !settings.isHideBars)
            if (settings.isHideBars) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val (bgColor, textColor) = when (settings.themeType) {
        ThemeType.DAY -> Color(0xFFFFFFFF) to Color(0xFF000000)
        ThemeType.NIGHT -> Color(0xFF121212) to Color(0xFFCCCCCC)
        ThemeType.SEPIA -> Color(0xFFF5ECD7) to Color(0xFF333333)
        ThemeType.SEPIA_CONTRAST -> Color(0xFFE8DCC4) to Color(0xFF1A1A1A)
    }

    val font = when (settings.fontFamily) {
        "Default" -> FontFamily.Default
        "Merriweather", "Serif" -> FontFamily.Serif
        "Roboto", "SansSerif" -> FontFamily.SansSerif
        "Monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
    
    // Map slider 0..1 to FontWeight 100..900
    val weightValue = (settings.fontWeight * 800).toInt() + 100
    val mappedFontWeight = FontWeight(weightValue.coerceIn(100, 900))

    // Chunk text into pages
    val pages = remember(mainText, settings.fontSize, settings.lineHeight) {
        val words = mainText.split(Regex("(?<=\\s)"))
        val chunks = mutableListOf<String>()
        val currentChunk = StringBuilder()
        // Approximation: a page holds fewer characters if font size is larger
        // Base characters per page at fontSize 20f could be around 600
        val charsPerPage = (600 * (20f / settings.fontSize) * (1.4f / settings.lineHeight)).toInt().coerceAtLeast(100)
        
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

    val pagerState = rememberPagerState(pageCount = { pages.size })
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
            // Main Text Content as HorizontalPager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                settings = settings.copy(isHideBars = !settings.isHideBars)
                            }
                        )
                    }
                    .padding(
                        top = if (settings.isHideBars) 16.dp else 80.dp, 
                        bottom = if (settings.isHideBars) 16.dp else 80.dp,
                        start = 16.dp, 
                        end = 16.dp
                    )
            ) { page ->
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = pages[page],
                        color = textColor,
                        fontSize = settings.fontSize.sp,
                        fontFamily = font,
                        fontWeight = mappedFontWeight,
                        textAlign = TextAlign.Justify,
                        lineHeight = (settings.fontSize * settings.lineHeight).sp
                    )
                }
            }

            // Top Panel
            AnimatedVisibility(
                visible = !settings.isHideBars,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TopAppBar(
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
                        IconButton(onClick = { isSettingsOpen = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = textColor)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }

            // Bottom Panel
            AnimatedVisibility(
                visible = !settings.isHideBars,
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

    if (isSettingsOpen) {
        ModalBottomSheet(
            onDismissRequest = { 
                isSettingsOpen = false 
                focusRequester.requestFocus() // Regain focus for volume keys after closing sheet
            },
            sheetState = sheetState,
            containerColor = Color(0xFF2C2C2E),
            dragHandle = null
        ) {
            SettingsContent(
                settings = settings,
                onSettingsChange = { settings = it }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit
) {
    val sheetTextColor = Color.White
    val secondaryTextColor = Color.Gray
    val accentColor = Color(0xFFFFC107)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "НАСТРОЙКИ ЧТЕНИЯ",
            color = Color(0xFF00BCD4),
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
            Text("Скрыть статус-бар и навигацию", color = Color.White, fontSize = 14.sp)
            Switch(
                checked = settings.isHideBars,
                onCheckedChange = { onSettingsChange(settings.copy(isHideBars = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFFFC107),
                    checkedTrackColor = Color(0xFFFFC107).copy(alpha = 0.5f),
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
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.DarkGray.copy(alpha = 0.3f),
                    unfocusedContainerColor = Color.DarkGray.copy(alpha = 0.3f)
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF3A3A3C))
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = Color.White) },
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
                    .background(Color.Black, shape = CircleShape)
                    .clickable { onDecrease() },
                contentAlignment = Alignment.Center
            ) {
                Text("–", color = Color.White, fontSize = 24.sp)
            }
            Text(value, color = Color.White, fontSize = 16.sp)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black, shape = CircleShape)
                    .clickable { onIncrease() },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Color.White, fontSize = 24.sp)
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
