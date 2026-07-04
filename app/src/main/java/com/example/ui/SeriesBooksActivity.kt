package com.example.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.BookEntity
import java.io.File

class SeriesBooksActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val seriesName = intent.getStringExtra("SERIES_NAME") ?: "Неизвестная"
        
        setContent {
            val context = LocalContext.current
            val database = remember { AppDatabase.getDatabase(context.applicationContext) }
            val booksFlow = remember(seriesName) { database.bookDao().getBooksBySeries(seriesName) }
            val books by booksFlow.collectAsState(initial = emptyList())
            
            val customBackground = Color(0xFF06161A)
            val customSurface = Color(0xFF0F262B)
            val customYellow = Color(0xFFE5A93C)
            
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Серия: $seriesName",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = customBackground
                        )
                    )
                },
                containerColor = customBackground
            ) { paddingValues ->
                if (books.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Книг серии не найдено",
                            color = Color.LightGray,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(books) { book ->
                            SeriesBookListItem(
                                book = book,
                                customSurface = customSurface,
                                customYellow = customYellow,
                                onClick = {
                                    val readerIntent = Intent(context, ReaderActivity::class.java).apply {
                                        putExtra("BOOK_SHA1", book.sha1)
                                    }
                                    context.startActivity(readerIntent)
                                },
                                onAuthorClick = { authorName ->
                                    val authorIntent = Intent(context, AuthorBooksActivity::class.java).apply {
                                        putExtra("AUTHOR_NAME", authorName)
                                    }
                                    context.startActivity(authorIntent)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesBookListItem(
    book: BookEntity,
    customSurface: Color,
    customYellow: Color,
    onClick: () -> Unit,
    onAuthorClick: (String) -> Unit
) {
    val startColor = try { Color(android.graphics.Color.parseColor(book.coverGradientStart)) } catch (e: Exception) { Color(0xFFE94560) }
    val endColor = try { Color(android.graphics.Color.parseColor(book.coverGradientEnd)) } catch (e: Exception) { Color(0xFF1A1A2E) }

    val coverBitmap = remember(book.coverPath) {
        book.coverPath?.let { path ->
            try {
                if (File(path).exists()) {
                    BitmapFactory.decodeFile(path)
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = customSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Book Cover image/gradient
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(130.dp)
                    .then(
                        if (coverBitmap != null) {
                            Modifier.background(Color.Transparent, shape = RoundedCornerShape(8.dp))
                        } else {
                            Modifier.background(Brush.verticalGradient(colors = listOf(startColor, endColor)), shape = RoundedCornerShape(8.dp))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = book.title.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Details Column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (book.seriesIndex != null) "${book.seriesIndex}. ${book.title}" else book.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = book.author ?: "Неизвестен",
                    color = customYellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { book.author?.let { onAuthorClick(it) } },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                val annotation = if (book.content.length > 180) {
                    book.content.take(180) + "..."
                } else {
                    book.content
                }
                
                Text(
                    text = annotation.ifEmpty { "Описание отсутствует." },
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
