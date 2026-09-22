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
// 1. ГЛАВНАЯ ТОЧКА ВХОДА (МАТЕРИАЛ 3 СТЕКЛОМОРФИЗМ)
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
    onCancelScanClicked: () -> Unit = {},
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

    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        colorScheme.background,
                        colorScheme.surfaceVariant,
                        Color(0xFF080B12)
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
                    placeholder = { Text("Поиск по названию или автору...", color = colorScheme.secondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colorScheme.tertiary) },
                    trailingIcon = {
                        IconButton(onClick = { 
                            onSearchQueryChanged("")
                            onSearchActiveChanged(false)
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть поиск", tint = colorScheme.tertiary)
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outline,
                        focusedTextColor = colorScheme.onSurface,
                        unfocusedTextColor = colorScheme.onSurface,
                        cursorColor = colorScheme.primary
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
                        .background(colorScheme.surface.copy(alpha = 0.85f))
                        .border(1.dp, colorScheme.outline, RoundedCornerShape(14.dp))
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
                                color = colorScheme.tertiary,
                                strokeWidth = 2.5.dp
                            )
                        }
                        Text(
                            text = scanProgressText.ifEmpty { "Сканирование устройства..." },
                            color = colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isScanning) {
                            androidx.compose.material3.TextButton(
                                onClick = onCancelScanClicked,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "Отмена",
                                    color = Color(0xFFFF5252),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            var selectedFormatFilter by remember { mutableStateOf("ALL") }

            val filteredBooks = remember(books, selectedFormatFilter) {
                when (selectedFormatFilter) {
                    "FB2" -> books.filter { it.filePath?.endsWith(".fb2", true) == true || it.filePath?.endsWith(".fb2.zip", true) == true }
                    "EPUB" -> books.filter { it.filePath?.endsWith(".epub", true) == true }
                    "MOBI" -> books.filter { it.filePath?.endsWith(".mobi", true) == true || it.filePath?.endsWith(".azw", true) == true || it.filePath?.endsWith(".azw3", true) == true }
                    "READING" -> books.filter { it.currentProgressChar > 0 }
                    "FAVORITE" -> books.filter { it.isFavorite }
                    else -> books
                }
            }

            if (books.isNotEmpty()) {
                val filterChips = listOf(
                    "ALL" to "Все",
                    "READING" to "Читаю сейчас",
                    "FAVORITE" to "Избранное",
                    "FB2" to "FB2",
                    "EPUB" to "EPUB",
                    "MOBI" to "MOBI"
                )
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filterChips.size) { idx ->
                        val (key, label) = filterChips[idx]
                        val isSelected = selectedFormatFilter == key
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) colorScheme.primary.copy(alpha = 0.25f)
                                    else colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) colorScheme.primary else colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedFormatFilter = key }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (filteredBooks.isEmpty()) {
                if (books.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Книги в этой категории не найдены",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    GlassEmptyState(onScanClicked = onScanClicked)
                }
            } else {
                if (isGridView) {
                    GlassBookGrid(books = filteredBooks, onBookClicked = onBookClicked)
                } else {
                    GlassBookList(books = filteredBooks, onBookClicked = onBookClicked)
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
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .height(64.dp)
            .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = colorScheme.outlineVariant)
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
                            colorScheme.tertiary.copy(alpha = 0.7f),
                            colorScheme.outline.copy(alpha = 0.2f),
                            colorScheme.tertiary.copy(alpha = 0.5f)
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
                        tint = colorScheme.tertiary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Библиотека",
                        color = colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                    Text(
                        text = "$bookCount книг",
                        color = colorScheme.secondary,
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
    val colorScheme = MaterialTheme.colorScheme
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
            tint = colorScheme.tertiary.copy(alpha = 0.9f),
            modifier = Modifier.size(17.dp)
        )
    }
}

// =========================================================
// 4. СЕТКА КНИГ (3 КОЛОНКИ СТЕКЛОМОРФИЗМ)
// =========================================================
@Composable
private fun GlassBookGrid(books: List<BookEntity>, onBookClicked: (BookEntity) -> Unit) {
    val uniqueBooks = remember(books) { books.distinctBy { it.sha1 } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp)
    ) {
        items(
            items = uniqueBooks,
            key = { it.sha1 }
        ) { book ->
            GlassBookCard(
                book = book,
                onClicked = { onBookClicked(book) }
            )
        }
    }
}

@Composable
private fun GlassBookCard(book: BookEntity, onClicked: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val coverUri = remember(book.coverPath) {
        if (!book.coverPath.isNullOrBlank()) {
            try { Uri.fromFile(java.io.File(book.coverPath)) } catch (e: Exception) { null }
        } else null
    }

    Box(
        modifier = Modifier
            .height(240.dp)
            .shadow(12.dp, RoundedCornerShape(14.dp), spotColor = colorScheme.outlineVariant)
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
                            colorScheme.tertiary.copy(alpha = 0.4f),
                            colorScheme.outline.copy(alpha = 0.15f),
                            colorScheme.tertiary.copy(alpha = 0.3f)
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
                color = colorScheme.onSurface,
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
                color = colorScheme.secondary,
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
    val colorScheme = MaterialTheme.colorScheme
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
                .shadow(16.dp, CircleShape, spotColor = colorScheme.tertiary)
                .clip(CircleShape)
                .background(Color(0xFF1C2740).copy(alpha = 0.8f))
                .drawBehind {
                    drawCircle(
                        brush = Brush.linearGradient(
                            listOf(colorScheme.tertiary, colorScheme.outline)
                        ),
                        style = Stroke(width = 2f)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = colorScheme.tertiary,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Библиотека пуста",
            color = colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Начните сканирование устройства или добавьте файлы книг",
            color = colorScheme.secondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onScanClicked,
            colors = ButtonDefaults.buttonColors(
                containerColor = colorScheme.primary
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .height(50.dp)
                .width(220.dp)
                .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = colorScheme.tertiary)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Сканировать",
                color = colorScheme.onPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GlassBookList(books: List<BookEntity>, onBookClicked: (BookEntity) -> Unit) {
    val uniqueBooks = remember(books) { books.distinctBy { it.sha1 } }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp)
    ) {
        items(
            items = uniqueBooks,
            key = { it.sha1 }
        ) { book ->
            GlassBookRowItem(book = book, onClicked = { onBookClicked(book) })
        }
    }
}

@Composable
private fun GlassBookRowItem(book: BookEntity, onClicked: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
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
            .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = colorScheme.outlineVariant)
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
            .border(1.dp, colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
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
                            tint = colorScheme.tertiary.copy(alpha = 0.6f),
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
                    color = colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = book.author?.ifEmpty { "Неизвестный автор" } ?: "Неизвестный автор",
                    color = colorScheme.secondary,
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
                    color = colorScheme.primary,
                    trackColor = colorScheme.surfaceVariant
                )
            }
        }
    }
}
