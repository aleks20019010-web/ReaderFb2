package com.nightread.app.ui

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// Цвета палитры
val WoodBase = Color(0xFF3D2B1F)
val WoodHighlight = Color(0xFF5E3A28)
val WoodDark = Color(0xFF1E120C)
val MetalPrimary = Color(0xFFC4A47A)
val MetalHighlight = Color(0xFFEFDFC0)
val MetalShadow = Color(0xFF6E5B42)

// Цвета пергаментной карточки
val ParchmentBase = Color(0xFFEAD9B4)
val ParchmentDark = Color(0xFFB89B6B)
val ParchmentLight = Color(0xFFF4E8CE)

@Composable
fun LibraryWithBooksScreen(
    onMenuClicked: () -> Unit = {},
    onSearchClicked: () -> Unit = {},
    onSortClicked: () -> Unit = {},
    onViewModeClicked: () -> Unit = {},
    onDownloadClicked: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(WoodHighlight, WoodBase, WoodDark),
                    startY = 0f,
                    endY = 1000f
                )
            )
    ) {
        // Текстура дерева (векторная)
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (i in 0..10) {
                drawLine(
                    brush = SolidColor(Color(0xFF1A100A).copy(alpha = 0.3f)),
                    start = Offset(0f, i * 120f + 40f),
                    end = Offset(size.width, i * 120f + 80f),
                    strokeWidth = (6..20).random().toFloat()
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Верхняя панель (с поддержкой колбэков)
            VectorFullWidthMetalTopBar(
                onMenuClicked = onMenuClicked,
                onSearchClicked = onSearchClicked,
                onSortClicked = onSortClicked,
                onViewModeClicked = onViewModeClicked,
                onDownloadClicked = onDownloadClicked
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Горизонтальный список книг
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val sampleBooks = listOf(
                    Triple("Системный практик IV", "Олег Свиридов", "https://cdn.litres.ru/pub/c/cover/72568549.jpg"),
                    Triple("Мастер снов", "Алексей Иванов", "https://cdn.litres.ru/pub/c/cover/44324925.jpg"),
                    Triple("Путь программиста", "Джон Доу", "https://cdn.litres.ru/pub/c/cover/11432201.jpg")
                )
                
                items(sampleBooks) { (title, author, url) ->
                    BookCard(
                        title = title,
                        author = author,
                        imageUrl = url
                    )
                }
            }
        }
    }
}

// =========================================================
//  ВЕРХНЯЯ ПЛАШКА (С колбэками)
// =========================================================
@Composable
fun VectorFullWidthMetalTopBar(
    onMenuClicked: () -> Unit = {},
    onSearchClicked: () -> Unit = {},
    onSortClicked: () -> Unit = {},
    onViewModeClicked: () -> Unit = {},
    onDownloadClicked: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(52.dp)
    ) {
        // Металлический фон плашки
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(MetalShadow, MetalPrimary, MetalShadow)
                ),
                cornerRadius = CornerRadius(8f, 8f),
                size = size
            )
            drawRoundRect(
                color = MetalHighlight.copy(alpha = 0.2f),
                cornerRadius = CornerRadius(8f, 8f),
                size = Size(size.width - 4, size.height - 4),
                topLeft = Offset(2f, 2f),
                style = Stroke(width = 1.5f)
            )
        }

        // Контент
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Левая часть
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onMenuClicked() }
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = MetalHighlight,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Библиотека",
                    color = MetalHighlight,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Правая часть (иконки прижаты к краю, луны нет)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = "Sort",
                    tint = MetalHighlight,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onSortClicked() }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.ViewAgenda,
                    contentDescription = "View",
                    tint = MetalHighlight,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onViewModeClicked() }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MetalHighlight,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onSearchClicked() }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download",
                    tint = MetalHighlight,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onDownloadClicked() }
                )
            }
        }
    }
}

// =========================================================
//  КАРТОЧКА КНИГИ (С 3D ЭФФЕКТОМ И ПОДДЕРЖКОЙ URL)
// =========================================================
@Composable
fun BookCard(
    title: String,
    author: String,
    imageUrl: String
) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(260.dp)
    ) {
        // 1. Фон (Рамка + Пергамент)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = Color(0xFF5D4037),
                cornerRadius = CornerRadius(12f, 12f),
                size = size
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(ParchmentLight, ParchmentBase, ParchmentDark)
                ),
                cornerRadius = CornerRadius(8f, 8f),
                size = Size(size.width - 8, size.height - 8),
                topLeft = Offset(4f, 4f)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.15f),
                topLeft = Offset(8f, 16f),
                size = Size(size.width - 40, 120f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 2. 3D Обложка с картинкой
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(110.dp)
            ) {
                // Сама картинка обложки (Скачивается по URL через Coil)
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Book Cover",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 20.dp), // Отступ для корешка
                    contentScale = ContentScale.Crop
                )
                
                // 3D Эффект и корешок (рисуем поверх картинки)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Корешок
                    drawRect(
                        color = Color.Black.copy(alpha = 0.4f),
                        topLeft = Offset(0f, 0f),
                        size = Size(20f, size.height)
                    )
                    // Тень корешка (переход объема)
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(30f, size.height)
                    )
                    // Блик и тень справа (изгиб)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.1f), Color.Transparent, Color.Black.copy(alpha = 0.3f))
                        ),
                        topLeft = Offset(20f, 0f),
                        size = Size(size.width - 20, size.height)
                    )
                }
                
                // Текст на обложке (с минимальным наклоном в -3 градуса)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 26.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF76FF03), Color(0xFF64DD17))
                            )
                        ),
                        modifier = Modifier.rotate(-3f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Тексты внизу
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
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Системный практик",
                color = Color(0xFF8D6E63),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewLibraryWithBooks() {
    LibraryWithBooksScreen()
}
