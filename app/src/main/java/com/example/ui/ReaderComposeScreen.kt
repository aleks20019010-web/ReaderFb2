package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderComposeScreen(
    bookTitle: String = "Орден Архитекторов",
    authorAndChapter: String = "Олег Сапфир, Юрий Винокуров | Глава 11",
    pageInfo: String = "122 из 239",
    mainText: String = sampleText,
    onBackClick: () -> Unit = {}
) {
    val bgColor = Color(0xFFF5ECD7)
    val textColor = Color(0xFF333333)
    val sheetColor = Color(0xFF2C2C2E)

    var isMenuVisible by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        containerColor = bgColor,
        topBar = {
            if (isMenuVisible) {
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
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(Icons.Filled.VolumeUp, contentDescription = "Громкость", tint = textColor)
                        }
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(Icons.Filled.Search, contentDescription = "Поиск", tint = textColor)
                        }
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(Icons.Filled.List, contentDescription = "Список", tint = textColor)
                        }
                        IconButton(onClick = { isSettingsOpen = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = textColor)
                        }
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Меню", tint = textColor)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        },
        bottomBar = {
            if (isMenuVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = pageInfo,
                        color = textColor,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(Icons.Filled.Sync, contentDescription = "Синхронизация", tint = textColor)
                        }
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(Icons.Filled.Share, contentDescription = "Поделиться", tint = textColor)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isMenuVisible = !isMenuVisible
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = mainText,
                    color = textColor,
                    fontSize = 20.sp, // Approximate mapping to '39' font setting from the screenshot
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Justify,
                    lineHeight = 28.sp
                )
            }
        }
    }

    if (isSettingsOpen) {
        ModalBottomSheet(
            onDismissRequest = { isSettingsOpen = false },
            sheetState = sheetState,
            containerColor = sheetColor,
            dragHandle = null
        ) {
            SettingsContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent() {
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
            text = "НАСТРОЙКИ EPUB, FB2, MOBI,\nDOC, DOCX, RTF, TXT И CHM",
            color = Color(0xFF00BCD4),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        SettingsDropdown(label = "ЛИСТАТЬ СТРАНИЦЫ", selectedValue = "Горизонтально")
        SettingsDropdown(label = "ЦВЕТОВАЯ СХЕМА", selectedValue = "Сепия контраст")
        SettingsDropdown(label = "ШРИФТ", selectedValue = "Merriweather")
        SettingsStepper(label = "РАЗМЕР ШРИФТА", value = "39")

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("ЖИРНОСТЬ ШРИФТА", color = secondaryTextColor, fontSize = 12.sp)
            Slider(
                value = 0.5f,
                onValueChange = {},
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = Color.DarkGray
                )
            )
        }

        SettingsStepper(label = "МЕЖСТРОЧНЫЙ ИНТЕРВАЛ", value = "100%")
        SettingsDropdown(label = "ВЫРАВНИВАТЬ ТЕКСТ ПО", selectedValue = "Ширине + Перенос слов")
        SettingsSwitch(label = "Две страницы в альбомной\nориентации экрана", checked = true)
        SettingsSwitch(label = "Поля страниц", checked = false)

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { /* TODO */ }) {
            Text("ОБЩИЕ НАСТРОЙКИ", color = sheetTextColor)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdown(label: String, selectedValue: String) {
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
                modifier = Modifier.menuAnchor().fillMaxWidth(),
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
                DropdownMenuItem(
                    text = { Text(selectedValue, color = Color.White) },
                    onClick = { expanded = false }
                )
            }
        }
    }
}

@Composable
fun SettingsStepper(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black, shape = CircleShape)
                    .clickable { },
                contentAlignment = Alignment.Center
            ) {
                Text("–", color = Color.White, fontSize = 24.sp)
            }
            Text(value, color = Color.White, fontSize = 16.sp)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black, shape = CircleShape)
                    .clickable { },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Color.White, fontSize = 24.sp)
            }
        }
    }
}

@Composable
fun SettingsSwitch(label: String, checked: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = {},
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFFC107),
                checkedTrackColor = Color(0xFFFFC107).copy(alpha = 0.5f),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray
            )
        )
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
