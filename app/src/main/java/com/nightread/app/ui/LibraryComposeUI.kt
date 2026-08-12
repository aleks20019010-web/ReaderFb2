package com.nightread.app.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nightread.app.data.BookEntity

// =========================================================
// 1. ЦВЕТОВАЯ ПАЛИТРА ИЗОЛИРОВАНА ЗДЕСЬ
// =========================================================
object LibraryColors {
    val WoodBase = Color(0xFF3D2B1F)
    val WoodHighlight = Color(0xFF5E3A28)
    val WoodDark = Color(0xFF1E120C)
    val MetalPrimary = Color(0xFFC4A47A)
    val MetalHighlight = Color(0xFFEFDFC0)
    val MetalShadow = Color(0xFF6E5B42)
    val Glow = Color(0xFFFFD700).copy(alpha = 0.3f)
    val ParchmentBase = Color(0xFFEAD9B4)
    val ParchmentDark = Color(0xFFB89B6B)
    val ParchmentLight = Color(0xFFF4E8CE)
}

// =========================================================
// 2. ГЛАВНАЯ ТОЧКА ВХОДА (ПУБЛИЧНАЯ)
// =========================================================
@Composable
fun LibraryComposeUI(
    books: List<BookEntity>,
    onScanClicked: () -> Unit,
    onMenuClicked: () -> Unit,
    onBookClicked: (BookEntity) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(
            brush = Brush.verticalGradient(
                listOf(LibraryColors.WoodHighlight, LibraryColors.WoodBase, LibraryColors.WoodDark)
            )
        )
    ) {
        // Векторная текстура дерева
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (i in 0..10) drawLine(
                brush = SolidColor(Color(0xFF1A100A).copy(alpha = 0.3f)),
                start = Offset(0f, i * 120f + 40f),
                end = Offset(size.width, i * 120f + 80f),
                strokeWidth = (6..20).random().toFloat()
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Верхняя плашка
            VectorFullWidthMetalTopBar(onMenuClicked = onMenuClicked)

            // Логика переключения: Пусто или Книги
            if (books.isEmpty()) {
                EmptyLibraryScreen(onScanClicked = onScanClicked)
            } else {
                BookshelfScreen(books = books, onBookClicked = onBookClicked)
            }
        }
    }
}

// =========================================================
// 3. ВСПОМОГАТЕЛЬНЫЕ ПРИВАТНЫЕ ФУНКЦИИ (ОТРИСОВКА)
// =========================================================

// --- Верхняя металлическая плашка ---
@Composable
private fun VectorFullWidthMetalTopBar(onMenuClicked: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(LibraryColors.MetalShadow, LibraryColors.MetalPrimary, LibraryColors.MetalShadow)),
                cornerRadius = CornerRadius(8f, 8f), size = size
            )
            drawRoundRect(
                color = LibraryColors.MetalHighlight.copy(alpha = 0.2f),
                cornerRadius = CornerRadius(8f, 8f),
                size = Size(size.width - 4, size.height - 4),
                topLeft = Offset(2f, 2f),
                style = Stroke(width = 1.5f)
            )
        }
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = LibraryColors.MetalHighlight,
                    modifier = Modifier.size(28.dp).clickable { onMenuClicked() }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Библиотека", color = LibraryColors.MetalHighlight, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Icon(Icons.Default.Sort, contentDescription = "Sort", tint = LibraryColors.MetalHighlight, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.ViewAgenda, contentDescription = "View", tint = LibraryColors.MetalHighlight, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.Search, contentDescription = "Search", tint = LibraryColors.MetalHighlight, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.Download, contentDescription = "Download", tint = LibraryColors.MetalHighlight, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// --- Экран "Нет книг" ---
@Composable
private fun EmptyLibraryScreen(onScanClicked: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        VectorImportIcon()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Начните сканирование или импортируйте\nкниги",
            color = Color.LightGray.copy(alpha = 0.8f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
            letterSpacing = 0.5.sp,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(48.dp))
        VectorMetalScanButton(onClick = onScanClicked)
    }
}

// --- Экран "Есть книги" ---
@Composable
private fun BookshelfScreen(books: List<BookEntity>, onBookClicked: (BookEntity) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(books) { book ->
            BookCard(
                title = book.title,
                author = book.author ?: "Неизвестный автор",
                imageUrl = book.coverPath ?: "",
                onBookClicked = { onBookClicked(book) }
            )
        }
    }
}

// --- Иконка импорта ---
@Composable
private fun VectorImportIcon() {
    Canvas(modifier = Modifier.size(120.dp)) {
        val cX = size.width / 2
        val cY = size.height / 2
        val metalGradient = Brush.linearGradient(
            listOf(LibraryColors.MetalHighlight, LibraryColors.MetalPrimary, LibraryColors.MetalShadow),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        )
        drawCircle(color = LibraryColors.Glow, radius = 70f, center = Offset(cX, cY))
        val rectPath = Path().apply { moveTo(cX - 40f, cY); lineTo(cX - 50f, cY + 30f); lineTo(cX + 50f, cY + 30f); lineTo(cX + 40f, cY); close() }
        drawPath(path = rectPath, brush = metalGradient)
        drawRoundRect(brush = metalGradient, topLeft = Offset(cX - 55f, cY + 30f), size = Size(110f, 8f), cornerRadius = CornerRadius(4f, 4f))
        val arrowPath = Path().apply { moveTo(cX - 30f, cY - 40f); lineTo(cX - 20f, cY - 40f); lineTo(cX - 20f, cY - 10f); lineTo(cX - 30f, cY - 10f); close() }
        drawPath(path = arrowPath, brush = metalGradient)
        val arrowHeadPath = Path().apply { moveTo(cX - 35f, cY - 10f); lineTo(cX, cY + 10f); lineTo(cX + 35f, cY - 10f); close() }
        drawPath(path = arrowHeadPath, brush = metalGradient)
    }
}

// --- Кнопка "Сканировать" ---
@Composable
private fun VectorMetalScanButton(onClick: () -> Unit) {
    Box(modifier = Modifier.width(260.dp).height(60.dp).clickable { onClick() }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(LibraryColors.MetalShadow, LibraryColors.MetalPrimary, LibraryColors.MetalShadow)),
                cornerRadius = CornerRadius(12f, 12f), size = size
            )
            drawRoundRect(
                color = LibraryColors.MetalHighlight.copy(alpha = 0.3f),
                cornerRadius = CornerRadius(12f, 12f),
                size = Size(size.width - 4, size.height - 4),
                topLeft = Offset(2f, 2f),
                style = Stroke(width = 2f)
            )
        }
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Canvas(modifier = Modifier.size(24.dp)) {
                drawRect(brush = SolidColor(Color.White.copy(alpha = 0.5f)), topLeft = Offset(0f, 8f), size = Size(24f, 16f))
                drawLine(brush = SolidColor(Color.White), start = Offset(4f, 8f), end = Offset(12f, 0f), strokeWidth = 4f)
                drawLine(brush = SolidColor(Color.White), start = Offset(20f, 8f), end = Offset(12f, 0f), strokeWidth = 4f)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Сканировать",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(brush = Brush.linearGradient(listOf(Color.White, LibraryColors.MetalHighlight)))
            )
        }
    }
}

// --- Карточка 3D книги (САМАЯ ВАЖНАЯ ОТРИСОВКА) ---
@Composable
private fun BookCard(title: String, author: String, imageUrl: String, onBookClicked: () -> Unit) {
    // Конвертация пути в Uri
    val coverUri = remember(imageUrl) {
        if (imageUrl.isNotBlank()) {
            try {
                Uri.fromFile(java.io.File(imageUrl))
            } catch (e: Exception) {
                null
            }
        } else null
    }

    Box(
        modifier = Modifier
            .width(160.dp)
            .height(260.dp)
            .clickable { onBookClicked() } // Весь блок кликабельный
    ) {
        // Фон карточки
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(color = Color(0xFF5D4037), cornerRadius = CornerRadius(12f, 12f), size = size)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(LibraryColors.ParchmentLight, LibraryColors.ParchmentBase, LibraryColors.ParchmentDark)),
                cornerRadius = CornerRadius(8f, 8f),
                size = Size(size.width - 8, size.height - 8),
                topLeft = Offset(4f, 4f)
            )
            drawRect(color = Color.Black.copy(alpha = 0.15f), topLeft = Offset(8f, 16f), size = Size(size.width - 40, 120f))
        }

        Column(modifier = Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(8.dp))

            // 3D Обложка
            Box(modifier = Modifier.width(130.dp).height(110.dp)) {
                AsyncImage(
                    model = coverUri,
                    contentDescription = "Book Cover",
                    modifier = Modifier.fillMaxSize().padding(start = 20.dp),
                    contentScale = ContentScale.Crop,
                    error = androidx.compose.ui.res.painterResource(com.nightread.app.R.drawable.ic_launcher_background)
                )
                // 3D Тени (Корешок и изгиб) - ОТРИСОВКА ТУТ
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = Color.Black.copy(alpha = 0.4f), topLeft = Offset(0f, 0f), size = Size(20f, size.height))
                    drawRect(
                        brush = Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)),
                        topLeft = Offset(0f, 0f),
                        size = Size(30f, size.height)
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.1f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f)
                            )
                        ),
                        topLeft = Offset(20f, 0f),
                        size = Size(size.width - 20, size.height)
                    )
                }
                // Текст на обложке
                Box(
                    modifier = Modifier.fillMaxSize().padding(start = 26.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        style = TextStyle(brush = Brush.linearGradient(listOf(Color(0xFF76FF03), Color(0xFF64DD17)))),
                        modifier = Modifier.rotate(-3f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Название внизу
            Text(
                text = title,
                color = Color(0xFF2E1B0E),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Автор внизу
            Text(
                text = author,
                color = Color(0xFF5D4037),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}
