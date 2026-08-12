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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
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
// 1. ЦВЕТОВАЯ ПАЛИТРА
// =========================================================
object LibraryColors {
    val WoodBase = Color(0xFF3D2B1F)
    val WoodHighlight = Color(0xFF5E3A28)
    val WoodDark = Color(0xFF1E120C)
    val MetalPrimary = Color(0xFFC4A47A) // Базовый цвет латуни
    val MetalHighlight = Color(0xFFEFDFC0) // Светлый блик
    val MetalShadow = Color(0xFF6E5B42) // Тёмная тень металла
    val Glow = Color(0xFFFFD700).copy(alpha = 0.4f)
    val ParchmentBase = Color(0xFFEAD9B4)
    val ParchmentDark = Color(0xFFB89B6B)
    val ParchmentLight = Color(0xFFF4E8CE)
}

// =========================================================
// 2. ГЛАВНАЯ ТОЧКА ВХОДА
// =========================================================
@Composable
fun LibraryComposeUI(
    books: List<BookEntity>,
    onScanClicked: () -> Unit,
    onMenuClicked: () -> Unit,
    onBookClicked: (BookEntity) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Фон: Дерево + прожилки
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(LibraryColors.WoodHighlight, LibraryColors.WoodBase, LibraryColors.WoodDark)
                )
            )
            // Текстура
            for (i in 0..10) {
                val yPos = i * 130f + 40f
                drawLine(
                    brush = SolidColor(Color(0xFF1A100A).copy(alpha = 0.25f)),
                    start = Offset(0f, yPos),
                    end = Offset(size.width, yPos + 40f),
                    strokeWidth = (6..25).random().toFloat()
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            VectorFullWidthMetalTopBar(onMenuClicked = onMenuClicked)

            if (books.isEmpty()) {
                EmptyLibraryScreen(onScanClicked = onScanClicked)
            } else {
                BookshelfScreen(books = books, onBookClicked = onBookClicked)
            }
        }
    }
}

// =========================================================
// 3. ВЕРХНЯЯ МЕТАЛЛИЧЕСКАЯ ПЛАШКА
// =========================================================
@Composable
private fun VectorFullWidthMetalTopBar(onMenuClicked: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(52.dp)
            .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = Color.Black.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(LibraryColors.MetalShadow, LibraryColors.MetalPrimary, LibraryColors.MetalHighlight, LibraryColors.MetalPrimary, LibraryColors.MetalShadow)
                )
            )
            .drawBehind {
                // Обводка
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.15f),
                    cornerRadius = CornerRadius(12f, 12f),
                    size = Size(size.width - 2, size.height - 2),
                    topLeft = Offset(1f, 1f),
                    style = Stroke(width = 1.5f)
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp).clickable { onMenuClicked() }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Библиотека", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Icon(Icons.Default.Sort, contentDescription = "Sort", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.ViewAgenda, contentDescription = "View", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
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

// =========================================================
// 4. ИСПРАВЛЕННАЯ 3D ИКОНКА ИМПОРТА
// =========================================================
@Composable
private fun VectorImportIcon() {
    Box(
        modifier = Modifier
            .size(100.dp)
            .shadow(20.dp, RoundedCornerShape(50), spotColor = LibraryColors.Glow)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF7C6B41)) // Темный фон под иконку
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cX = size.width / 2
            val cY = size.height / 2
            val metalGradient = Brush.linearGradient(
                listOf(Color(0xFFF5E6C8), Color(0xFFA0865B), Color(0xFF5C4A2E)),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            )

            // Сама иконка (ящик)
            val rectPath = Path().apply {
                moveTo(cX - 30f, cY - 5f)
                lineTo(cX - 40f, cY + 18f)
                lineTo(cX + 40f, cY + 18f)
                lineTo(cX + 30f, cY - 5f)
                close()
            }
            drawPath(path = rectPath, brush = metalGradient)

            // Полка
            drawRoundRect(
                brush = metalGradient,
                topLeft = Offset(cX - 42f, cY + 18f),
                size = Size(84f, 8f),
                cornerRadius = CornerRadius(4f, 4f)
            )

            // Стрелка
            val arrowPath = Path().apply {
                moveTo(cX - 22f, cY - 30f)
                lineTo(cX - 12f, cY - 30f)
                lineTo(cX - 12f, cY - 5f)
                lineTo(cX - 22f, cY - 5f)
                close()
            }
            drawPath(path = arrowPath, brush = metalGradient)

            val arrowHeadPath = Path().apply {
                moveTo(cX - 28f, cY - 5f)
                lineTo(cX, cY + 8f)
                lineTo(cX + 28f, cY - 5f)
                close()
            }
            drawPath(path = arrowHeadPath, brush = metalGradient)
        }
    }
}

// =========================================================
// 5. ИСПРАВЛЕННАЯ 3D КНОПКА "СКАНИРОВАТЬ"
// =========================================================
@Composable
private fun VectorMetalScanButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(260.dp)
            .height(60.dp)
            .shadow(12.dp, RoundedCornerShape(12.dp), spotColor = LibraryColors.Glow)
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(LibraryColors.MetalShadow, LibraryColors.MetalPrimary, LibraryColors.MetalHighlight, LibraryColors.MetalPrimary, LibraryColors.MetalShadow)
                )
            )
            .clickable { onClick() }
            .drawBehind {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.2f),
                    cornerRadius = CornerRadius(12f, 12f),
                    size = Size(size.width - 4, size.height - 4),
                    topLeft = Offset(2f, 2f),
                    style = Stroke(width = 1f)
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Иконка внутри кнопки
            Box(modifier = Modifier.size(24.dp).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))) {
                Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                    drawRect(
                        brush = SolidColor(Color.White.copy(alpha = 0.7f)),
                        topLeft = Offset(0f, 8f),
                        size = Size(16f, 8f)
                    )
                    drawLine(
                        brush = SolidColor(Color.White),
                        start = Offset(4f, 8f),
                        end = Offset(8f, 0f),
                        strokeWidth = 4f
                    )
                    drawLine(
                        brush = SolidColor(Color.White),
                        start = Offset(12f, 8f),
                        end = Offset(8f, 0f),
                        strokeWidth = 4f
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = "Сканировать",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// =========================================================
// 6. 3D КАРТОЧКА КНИГИ
// =========================================================
@Composable
private fun BookCard(title: String, author: String, imageUrl: String, onBookClicked: () -> Unit) {
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
            .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = Color.Black.copy(alpha = 0.4f))
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF5D4037)) // Внешняя деревянная рамка
            .clickable { onBookClicked() }
    ) {
        // Внутренний пергамент
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    brush = Brush.verticalGradient(listOf(LibraryColors.ParchmentLight, LibraryColors.ParchmentBase, LibraryColors.ParchmentDark))
                )
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(4.dp))

                // 3D Обложка
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(110.dp)
                        .shadow(6.dp, RoundedCornerShape(4.dp), spotColor = Color.Black.copy(alpha = 0.3f))
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    AsyncImage(
                        model = coverUri,
                        contentDescription = "Book Cover",
                        modifier = Modifier.fillMaxSize().padding(start = 16.dp), // Корешок
                        contentScale = ContentScale.Crop,
                        error = androidx.compose.ui.res.painterResource(com.nightread.app.R.drawable.ic_launcher_background)
                    )
                    // 3D Тени обложки
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(color = Color.Black.copy(alpha = 0.4f), topLeft = Offset(0f, 0f), size = Size(16f, size.height))
                        drawRect(
                            brush = Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)),
                            topLeft = Offset(0f, 0f),
                            size = Size(24f, size.height)
                        )
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.15f), Color.Transparent, Color.Black.copy(alpha = 0.2f))
                            ),
                            topLeft = Offset(16f, 0f),
                            size = Size(size.width - 16, size.height)
                        )
                    }
                    // Текст на обложке
                    Box(
                        modifier = Modifier.fillMaxSize().padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
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
}
