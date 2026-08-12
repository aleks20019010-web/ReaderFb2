package com.nightread.app.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nightread.app.data.BookEntity

// =========================================================
// 1. ПАЛИТРА ТЕМНОГО СТЕКЛОМОРФИЗМА И СЕРЕБРА
// =========================================================
object GlassLibraryColors {
    val SpaceTop = Color(0xFF0F1523)
    val SpaceMid = Color(0xFF141D30)
    val SpaceBottom = Color(0xFF080B12)

    val GlassSurface = Color(0xFF192236).copy(alpha = 0.65f)
    val GlassSurfaceHover = Color(0xFF222F4B).copy(alpha = 0.8f)
    
    val SilverBorder = Color(0xFFB0BEC5).copy(alpha = 0.45f)
    val SilverHighlight = Color(0xFFE2E8F0)
    val SilverGlow = Color(0xFF94A3B8).copy(alpha = 0.25f)

    val TextMain = Color(0xFFF1F5F9)
    val TextMuted = Color(0xFF94A3B8)
}

// =========================================================
// 2. ГЛАВНАЯ ТОЧКА ВХОДА
// =========================================================
@Composable
fun LibraryComposeUI(
    books: List<BookEntity>,
    isScanning: Boolean = false,
    scanProgressText: String = "",
    isGridView: Boolean = true,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChanged: (String) -> Unit = {},
    onSearchActiveChanged: (Boolean) -> Unit = {},
    onScanClicked: () -> Unit,
    onSearchClicked: () -> Unit = {},
    onViewModeClicked: () -> Unit = {},
    onManualImportClicked: () -> Unit = {},
    onSortClicked: () -> Unit = {},
    onMenuClicked: () -> Unit,
    onBookClicked: (BookEntity) -> Unit
) {
    var visibleBanner by remember { mutableStateOf(false) }
    
    LaunchedEffect(isScanning, scanProgressText) {
        if (isScanning) {
            visibleBanner = true
        } else if (scanProgressText.isNotEmpty()) {
            visibleBanner = true
            kotlinx.coroutines.delay(3000)
            visibleBanner = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        GlassLibraryColors.SpaceTop,
                        GlassLibraryColors.SpaceMid,
                        GlassLibraryColors.SpaceBottom
                    )
                )
            )
    ) {
        // Фоновые космические звезды / туманность
        Canvas(modifier = Modifier.fillMaxSize()) {
            val starColors = listOf(Color.White, Color(0xFFB3E5FC), Color(0xFFE1BEE7))
            // Несколько мягких световых бликов туманности
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF3F51B5).copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(size.width * 0.2f, size.height * 0.3f),
                    radius = 400f
                ),
                radius = 400f,
                center = Offset(size.width * 0.2f, size.height * 0.3f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF00BCD4).copy(alpha = 0.1f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.7f),
                    radius = 500f
                ),
                radius = 500f,
                center = Offset(size.width * 0.8f, size.height * 0.7f)
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            GlassmorphicTopBar(
                bookCount = books.size,
                onMenuClicked = onMenuClicked,
                onScanClicked = onScanClicked,
                onSearchClicked = onSearchClicked,
                onViewModeClicked = onViewModeClicked,
                onManualImportClicked = onManualImportClicked,
                onSortClicked = onSortClicked
            )

            if (isSearchActive) {
                val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Поиск по названию или автору...", color = GlassLibraryColors.TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GlassLibraryColors.SilverHighlight) },
                    trailingIcon = {
                        IconButton(onClick = { 
                            onSearchQueryChanged("")
                            onSearchActiveChanged(false)
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть поиск", tint = GlassLibraryColors.SilverHighlight)
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00BCD4),
                        unfocusedBorderColor = Color(0xFFB0BEC5).copy(alpha = 0.5f),
                        focusedTextColor = GlassLibraryColors.TextMain,
                        unfocusedTextColor = GlassLibraryColors.TextMain,
                        cursorColor = Color(0xFF00BCD4)
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            if (visibleBanner && (isScanning || scanProgressText.isNotEmpty())) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF192236).copy(alpha = 0.85f))
                        .border(1.dp, Color(0xFFB0BEC5).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFFB0BEC5),
                                strokeWidth = 2.5.dp
                            )
                        }
                        Text(
                            text = scanProgressText.ifEmpty { "Сканирование устройства..." },
                            color = Color(0xFFF1F5F9),
                            fontSize = 13.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (books.isEmpty()) {
                GlassEmptyState(onScanClicked = onScanClicked)
            } else {
                if (isGridView) {
                    GlassBookGrid(books = books, onBookClicked = onBookClicked)
                } else {
                    GlassBookList(books = books, onBookClicked = onBookClicked)
                }
            }
        }
    }
}

// =========================================================
// 3. ВЕРХНЯЯ СТЕКЛЯННАЯ ПЛАШКА С СЕРЕБРОМ
// =========================================================
@Composable
private fun GlassmorphicTopBar(
    bookCount: Int,
    onMenuClicked: () -> Unit,
    onScanClicked: () -> Unit,
    onSearchClicked: () -> Unit,
    onViewModeClicked: () -> Unit,
    onManualImportClicked: () -> Unit,
    onSortClicked: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .height(64.dp)
            .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = GlassLibraryColors.SilverGlow)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF222B40).copy(alpha = 0.8f),
                        Color(0xFF1A2234).copy(alpha = 0.7f),
                        Color(0xFF25304A).copy(alpha = 0.8f)
                    )
                )
            )
            .drawBehind {
                // Тонкий серебряный контур стекломорфизма
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            GlassLibraryColors.SilverHighlight.copy(alpha = 0.7f),
                            GlassLibraryColors.SilverBorder.copy(alpha = 0.2f),
                            GlassLibraryColors.SilverHighlight.copy(alpha = 0.5f)
                        )
                    ),
                    cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                    size = Size(size.width - 2f, size.height - 2f),
                    topLeft = Offset(1f, 1f),
                    style = Stroke(width = 1.5f)
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Левая часть: Меню с серебряным фоном + Текст "Библиотека" / счетчик
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A3754).copy(alpha = 0.6f))
                        .clickable { onMenuClicked() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = GlassLibraryColors.SilverHighlight,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Библиотека",
                        color = GlassLibraryColors.TextMain,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                    Text(
                        text = "$bookCount книг",
                        color = GlassLibraryColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Правая часть: Иконки инструментов в стиле стекло
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GlassActionIcon(Icons.Default.Refresh, "Сканировать книги") { onScanClicked() }
                GlassActionIcon(Icons.Default.Search, "Поиск книг") { onSearchClicked() }
                GlassActionIcon(Icons.Default.GridView, "Смена вида") { onViewModeClicked() }
                GlassActionIcon(Icons.Default.FolderOpen, "Ручной импорт") { onManualImportClicked() }
                GlassActionIcon(Icons.Default.Sort, "Сортировка") { onSortClicked() }
            }
        }
    }
}

@Composable
private fun GlassActionIcon(imageVector: ImageVector, description: String, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(0xFF222C44).copy(alpha = 0.5f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = description,
            tint = GlassLibraryColors.SilverHighlight.copy(alpha = 0.9f),
            modifier = Modifier.size(17.dp)
        )
    }
}

// =========================================================
// 4. СЕТКА КНИГ (3 КОЛОНКИ СТЕКЛОМОРФИЗМ)
// =========================================================
@Composable
private fun GlassBookGrid(books: List<BookEntity>, onBookClicked: (BookEntity) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp)
    ) {
        items(books) { book ->
            GlassBookCard(
                book = book,
                onClicked = { onBookClicked(book) }
            )
        }
    }
}

@Composable
private fun GlassBookCard(book: BookEntity, onClicked: () -> Unit) {
    val coverUri = remember(book.coverPath) {
        if (!book.coverPath.isNullOrBlank()) {
            try { Uri.fromFile(java.io.File(book.coverPath)) } catch (e: Exception) { null }
        } else null
    }

    Box(
        modifier = Modifier
            .height(240.dp)
            .shadow(12.dp, RoundedCornerShape(14.dp), spotColor = GlassLibraryColors.SilverGlow)
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF202A42).copy(alpha = 0.75f),
                        Color(0xFF131A2B).copy(alpha = 0.85f)
                    )
                )
            )
            .clickable { onClicked() }
            .drawBehind {
                // Серебряная окантовка карточки
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            GlassLibraryColors.SilverHighlight.copy(alpha = 0.4f),
                            GlassLibraryColors.SilverBorder.copy(alpha = 0.15f),
                            GlassLibraryColors.SilverHighlight.copy(alpha = 0.3f)
                        )
                    ),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                    size = Size(size.width - 2f, size.height - 2f),
                    topLeft = Offset(1f, 1f),
                    style = Stroke(width = 1f)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Обложка книги
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .shadow(4.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0D121F))
            ) {
                AsyncImage(
                    model = coverUri,
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = androidx.compose.ui.res.painterResource(com.nightread.app.R.drawable.ic_launcher_background)
                )
                // Легкий отсвет стекла поверх обложки
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f))
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Название книги
            Text(
                text = book.title,
                color = GlassLibraryColors.TextMain,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // Автор
            Text(
                text = book.author ?: "Неизвестный автор",
                color = GlassLibraryColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

// =========================================================
// 5. ПУСТОЙ ЭКРАН В СТИЛЕ СТЕКЛОМОРФИЗМ
// =========================================================
@Composable
private fun GlassEmptyState(onScanClicked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .shadow(16.dp, CircleShape, spotColor = GlassLibraryColors.SilverHighlight)
                .clip(CircleShape)
                .background(Color(0xFF1C2740).copy(alpha = 0.8f))
                .drawBehind {
                    drawCircle(
                        brush = Brush.linearGradient(
                            listOf(GlassLibraryColors.SilverHighlight, GlassLibraryColors.SilverBorder)
                        ),
                        style = Stroke(width = 2f)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = GlassLibraryColors.SilverHighlight,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Библиотека пуста",
            color = GlassLibraryColors.TextMain,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Начните сканирование устройства или добавьте файлы книг",
            color = GlassLibraryColors.TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onScanClicked,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2A3A5E)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .height(50.dp)
                .width(220.dp)
                .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = GlassLibraryColors.SilverHighlight)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = GlassLibraryColors.SilverHighlight,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Сканировать",
                color = GlassLibraryColors.SilverHighlight,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GlassBookList(books: List<BookEntity>, onBookClicked: (BookEntity) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp)
    ) {
        items(books) { book ->
            GlassBookRowItem(book = book, onClicked = { onBookClicked(book) })
        }
    }
}

@Composable
private fun GlassBookRowItem(book: BookEntity, onClicked: () -> Unit) {
    val coverUri = remember(book.coverPath) {
        if (!book.coverPath.isNullOrBlank()) {
            try { Uri.fromFile(java.io.File(book.coverPath)) } catch (e: Exception) { null }
        } else null
    }

    val progress = if (book.totalCharacters > 0) (book.currentProgressChar.toFloat() / book.totalCharacters) else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = GlassLibraryColors.SilverGlow)
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xFF202A42).copy(alpha = 0.8f),
                        Color(0xFF131A2B).copy(alpha = 0.9f)
                    )
                )
            )
            .clickable { onClicked() }
            .border(1.dp, Color(0xFFB0BEC5).copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2E3B55))
            ) {
                if (coverUri != null) {
                    coil.compose.AsyncImage(
                        model = coverUri,
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = GlassLibraryColors.SilverHighlight.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = book.title.ifEmpty { "Без названия" },
                    color = GlassLibraryColors.TextMain,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = book.author?.ifEmpty { "Неизвестный автор" } ?: "Неизвестный автор",
                    color = GlassLibraryColors.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF00BCD4),
                    trackColor = Color(0xFF2A3752)
                )
            }
        }
    }
}
