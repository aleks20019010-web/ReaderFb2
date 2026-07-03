package com.example.ui

import android.widget.Toast
import android.os.Build
import android.os.Environment
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.BookEntity
import com.example.data.NoteEntity
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch

data class ReaderThemePreset(
    val id: String,
    val name: String,
    val bgColor: Color,
    val textColor: Color
)

val readerThemes = listOf(
    ReaderThemePreset("light", "Бумага", Color(0xFFFCFCFC), Color(0xFF1E1E1E)),
    ReaderThemePreset("dark", "Ночь", Color(0xFF121212), Color(0xFFE0E0E0)),
    ReaderThemePreset("sepia", "Сепия", Color(0xFFFBF0D9), Color(0xFF4F3824)),
    ReaderThemePreset("mint", "Мята", Color(0xFFE8F5E9), Color(0xFF1B5E20)),
    ReaderThemePreset("ocean", "Океан", Color(0xFF0F172A), Color(0xFFE2E8F0)),
    ReaderThemePreset("solarized", "Теплый", Color(0xFFFDF6E3), Color(0xFF586E75))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: BookViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val books by viewModel.allBooks.collectAsState()
    val notes by viewModel.allNotes.collectAsState()

    // Dialog trigger states
    var showAddBookDialog by remember { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var selectedTextForNote by remember { mutableStateOf("") }
    var showAiAssistantDialog by remember { mutableStateOf(false) }
    var customAiPrompt by remember { mutableStateOf("") }

    // Theme values based on preferences
    val currentThemePreset = readerThemes.find { it.id == viewModel.readerTheme } ?: readerThemes.first()
    val readerBackground = currentThemePreset.bgColor
    val readerText = currentThemePreset.textColor

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("main_navigation_bar"),
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = "Библиотека") },
                    label = { Text("Библиотека") },
                    selected = viewModel.currentTab == 0,
                    onClick = { viewModel.currentTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Book, contentDescription = "Читалка") },
                    label = { Text("Читалка") },
                    selected = viewModel.currentTab == 1,
                    onClick = {
                        // If no book open, fall back to showing info or open first book
                        if (viewModel.selectedBook == null && books.isNotEmpty()) {
                            viewModel.openBook(books.first())
                        }
                        viewModel.currentTab = 1
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.EditNote, contentDescription = "Заметки") },
                    label = { Text("Заметки") },
                    selected = viewModel.currentTab == 2,
                    onClick = { viewModel.currentTab = 2 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Sync, contentDescription = "Синхронизация") },
                    label = { Text("Синхро") },
                    selected = viewModel.currentTab == 3,
                    onClick = {
                        viewModel.refreshIpAddress()
                        viewModel.currentTab = 3
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (viewModel.currentTab) {
                0 -> LibraryTab(
                    books = books,
                    viewModel = viewModel,
                    onAddBookClick = { showAddBookDialog = true }
                )
                1 -> ReaderTab(
                    viewModel = viewModel,
                    readerBg = readerBackground,
                    readerText = readerText,
                    onAddNoteClick = {
                        selectedTextForNote = ""
                        showAddNoteDialog = true
                    },
                    onAiAssistantClick = {
                        customAiPrompt = ""
                        showAiAssistantDialog = true
                        showAiAssistantPageContext(viewModel)
                    }
                )
                2 -> NotesTab(
                    notes = notes,
                    viewModel = viewModel
                )
                3 -> SyncTab(
                    viewModel = viewModel
                )
            }
        }
    }

    // --- BOOK DETAILS OVERLAY ---
    viewModel.detailedBook?.let { book ->
        BookDetailsScreen(
            viewModel = viewModel,
            book = book,
            onBack = { viewModel.detailedBook = null }
        )
    }

    // --- ADD BOOK DIALOG ---
    if (showAddBookDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newAuthor by remember { mutableStateOf("") }
        var newCategory by remember { mutableStateOf("Классика") }
        var newContent by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddBookDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Добавить книгу",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Название книги") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_book_title_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newAuthor,
                        onValueChange = { newAuthor = it },
                        label = { Text("Автор") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_book_author_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newCategory,
                        onValueChange = { newCategory = it },
                        label = { Text("Жанр / Категория") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newContent,
                        onValueChange = { newContent = it },
                        label = { Text("Текст книги") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        maxLines = 10
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddBookDialog = false }) {
                            Text("Отмена")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newTitle.isNotBlank() && newContent.isNotBlank()) {
                                    viewModel.addNewBook(newTitle, newAuthor, newContent, newCategory)
                                    showAddBookDialog = false
                                    Toast.makeText(context, "Книга успешно добавлена!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Заполните название и текст книги!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("add_book_submit_button")
                        ) {
                            Text("Создать")
                        }
                    }
                }
            }
        }
    }

    // --- ADD NOTE DIALOG ---
    if (showAddNoteDialog) {
        var noteContent by remember { mutableStateOf("") }
        val currentBook = viewModel.selectedBook

        Dialog(onDismissRequest = { showAddNoteDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        "Создать заметку",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Книга: ${currentBook?.title ?: "Не выбрана"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = selectedTextForNote,
                        onValueChange = { selectedTextForNote = it },
                        label = { Text("Цитируемый текст (опционально)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        label = { Text("Текст вашей заметки") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddNoteDialog = false }) {
                            Text("Отмена")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (noteContent.isNotBlank()) {
                                    viewModel.createNote(selectedTextForNote, noteContent)
                                    showAddNoteDialog = false
                                    Toast.makeText(context, "Заметка сохранена!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Текст заметки не может быть пустым!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Сохранить")
                        }
                    }
                }
            }
        }
    }

    // --- AI ASSISTANT DIALOG ---
    if (showAiAssistantDialog) {
        Dialog(onDismissRequest = { showAiAssistantDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "AI",
                            tint = Color(0xFF9B5DE5),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "ИИ-Литературовед",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                "Ответ ИИ:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = viewModel.aiResult,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            viewModel.aiError?.let { err ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customAiPrompt,
                        onValueChange = { customAiPrompt = it },
                        label = { Text("Задайте вопрос по главе...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                val book = viewModel.selectedBook
                                val textContext = book?.content?.let { content ->
                                    val start = (viewModel.readerPage * 1000).coerceAtMost(content.length)
                                    val end = ((viewModel.readerPage + 1) * 1000).coerceAtMost(content.length)
                                    content.substring(start, end)
                                } ?: ""
                                val prompt = "Объясни краткий смысл этой страницы из произведения '${book?.title}':\n\n$textContext"
                                viewModel.askGemini(prompt)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            enabled = !viewModel.aiLoading
                        ) {
                            Text("Анализ страницы")
                        }

                        Button(
                            onClick = {
                                if (customAiPrompt.isNotBlank()) {
                                    val book = viewModel.selectedBook
                                    val textContext = book?.content?.let { content ->
                                        val start = (viewModel.readerPage * 1000).coerceAtMost(content.length)
                                        val end = ((viewModel.readerPage + 1) * 1000).coerceAtMost(content.length)
                                        content.substring(start, end)
                                    } ?: ""
                                    val fullPrompt = "Контекст из произведения '${book?.title}':\n\"$textContext\"\n\nВопрос читателя: $customAiPrompt"
                                    viewModel.askGemini(fullPrompt)
                                }
                            },
                            enabled = !viewModel.aiLoading && customAiPrompt.isNotBlank()
                        ) {
                            if (viewModel.aiLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                            } else {
                                Text("Спросить")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showAiAssistantDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Закрыть")
                    }
                }
            }
        }
    }
}

private fun showAiAssistantPageContext(viewModel: BookViewModel) {
    if (viewModel.aiResult.isEmpty()) {
        viewModel.aiResult = "Я готов помочь вам разобрать это произведение! Вы можете нажать кнопку «Анализ страницы» выше, чтобы получить краткий литературоведческий разбор текущей страницы, или ввести собственный вопрос в поле ввода."
    }
}

// ================= TAB 1: LIBRARY / SHELF =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTab(
    books: List<BookEntity>,
    viewModel: BookViewModel,
    onAddBookClick: () -> Unit
) {
    val categories = listOf("Все", "Классика", "Проза", "Поэзия", "Локальные")
    val context = LocalContext.current
    var showPermissionExplanationDialog by remember { mutableStateOf(false) }
    var showOlderPermissionExplanationDialog by remember { mutableStateOf(false) }

    val singleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val contentResolver = context.contentResolver
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                        val content = reader.readText()
                        
                        var fileName = "Импортированная книга"
                        val cursor = contentResolver.query(uri, null, null, null, null)
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (nameIndex != -1) {
                                    fileName = it.getString(nameIndex)
                                }
                            }
                        }
                        val title = fileName.substringBeforeLast(".")
                        viewModel.addNewBook(
                            title = title,
                            author = "Импорт",
                            content = content,
                            category = "Локальные"
                        )
                        Toast.makeText(context, "Книга \"$title\" успешно импортирована!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка импорта: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    // For Android < 11 (API < 30)
    val readPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.startLocalBookScan()
            } else {
                Toast.makeText(context, "Доступ к файлам отклонен", Toast.LENGTH_SHORT).show()
            }
        }
    )

    fun onScanClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                viewModel.startLocalBookScan()
            } else {
                showPermissionExplanationDialog = true
            }
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            
            if (hasPermission) {
                viewModel.startLocalBookScan()
            } else {
                showOlderPermissionExplanationDialog = true
            }
        }
    }

    if (showPermissionExplanationDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionExplanationDialog = false },
            title = { Text("Доступ ко всем файлам") },
            text = {
                Text(
                    "Для автоматического поиска книг на вашем устройстве (/storage/emulated/0) приложению требуется разрешение на доступ ко всем файлам.\n\n" +
                    "После нажатия «Предоставить» откроется системное меню, где вам нужно будет включить переключатель для этого приложения."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionExplanationDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                    }
                ) {
                    Text("Предоставить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionExplanationDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showOlderPermissionExplanationDialog) {
        AlertDialog(
            onDismissRequest = { showOlderPermissionExplanationDialog = false },
            title = { Text("Доступ к памяти") },
            text = {
                Text("Для автоматического поиска книг приложению требуется доступ к памяти вашего устройства.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOlderPermissionExplanationDialog = false
                        readPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                ) {
                    Text("Разрешить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOlderPermissionExplanationDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Heading Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Моя библиотека",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "В библиотеке: ${books.size} кн.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { singleFilePickerLauncher.launch(arrayOf("text/plain")) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("import_book_fab")
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = "Импортировать книгу")
                }
                IconButton(
                    onClick = onAddBookClick,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.testTag("add_book_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить книгу вручную")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        OutlinedTextField(
            value = viewModel.bookSearchQuery,
            onValueChange = { viewModel.bookSearchQuery = it },
            placeholder = { Text("Поиск книг или авторов...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("book_search_input"),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Scan Progress / Trigger Layout
        if (viewModel.isScanning) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = viewModel.scanProgressText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        } else if (viewModel.scanProgressText.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = viewModel.scanProgressText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.scanProgressText = "" },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Закрыть",
                            modifier = Modifier.size(16.dp),
                            tint = Color.Gray
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Action Buttons Row (Scan device)
        Button(
            onClick = { onScanClick() },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("scan_device_books_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Поиск книг на устройстве", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Categories Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                val selected = viewModel.selectedCategory == cat
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.selectedCategory = cat },
                    label = { Text(cat) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Book list filtering
        val filteredBooks = books.filter { book ->
            val matchesSearch = book.title.contains(viewModel.bookSearchQuery, ignoreCase = true) ||
                    book.author.contains(viewModel.bookSearchQuery, ignoreCase = true)
            val matchesCategory = viewModel.selectedCategory == "Все" || book.category == viewModel.selectedCategory
            matchesSearch && matchesCategory
        }

        if (filteredBooks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LibraryBooks,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Книги не найдены",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Gray
                    )
                    Text(
                        "Попробуйте изменить запрос или добавить новую книгу.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredBooks) { book ->
                    BookGridItem(book = book, onOpen = { viewModel.detailedBook = book }, onDelete = { viewModel.deleteBook(book.id) })
                }
            }
        }
    }
}

@Composable
fun BookGridItem(
    book: BookEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    // Elegant procedural book cover cards with premium gradients (Penguin style)
    val startColor = try { Color(android.graphics.Color.parseColor(book.coverGradientStart)) } catch (e: Exception) { Color(0xFFE94560) }
    val endColor = try { Color(android.graphics.Color.parseColor(book.coverGradientEnd)) } catch (e: Exception) { Color(0xFF1A1A2E) }

    val coverBitmap = remember(book.coverPath) {
        book.coverPath?.let { path ->
            try {
                if (java.io.File(path).exists()) {
                    android.graphics.BitmapFactory.decodeFile(path)
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clickable(onClick = onOpen)
            .testTag("book_card_${book.id}"),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Gradient cover top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .then(
                        if (coverBitmap != null) {
                            Modifier
                        } else {
                            Modifier.background(Brush.verticalGradient(colors = listOf(startColor, endColor)))
                        }
                    )
                    .padding(12.dp)
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = book.title,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                    )
                }
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Text(
                                book.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("delete_book_btn_${book.id}")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Удалить книгу",
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Column {
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            book.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Progress bar and info bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                val progressPercent = if (book.totalCharacters > 0) {
                    ((book.currentProgressChar.toFloat() / book.totalCharacters) * 100).toInt()
                } else 0

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Прогресс",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$progressPercent%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = if (book.totalCharacters > 0) book.currentProgressChar.toFloat() / book.totalCharacters else 0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Символов: ${book.currentProgressChar}/${book.totalCharacters}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}

// ================= TAB 2: READER =================
@Composable
fun ReaderTab(
    viewModel: BookViewModel,
    readerBg: Color,
    readerText: Color,
    onAddNoteClick: () -> Unit,
    onAiAssistantClick: () -> Unit
) {
    val book = viewModel.selectedBook

    if (book == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Библиотека пуста",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Выберите книгу на вкладке «Библиотека» или добавьте свою, чтобы начать чтение.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.currentTab = 0 }) {
                    Text("Перейти к Библиотеке")
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(readerBg)
        ) {
            // Reader Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = readerBg.copy(alpha = 0.9f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                book.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = readerText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                book.author,
                                style = MaterialTheme.typography.labelSmall,
                                color = readerText.copy(alpha = 0.7f)
                            )
                        }

                        // Theme switchers
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            readerThemes.forEach { preset ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(preset.bgColor, shape = CircleShape)
                                        .border(
                                            width = if (viewModel.readerTheme == preset.id) 2.dp else 1.dp,
                                            color = if (viewModel.readerTheme == preset.id) MaterialTheme.colorScheme.primary else readerText.copy(alpha = 0.3f),
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.readerTheme = preset.id }
                                        .testTag("reader_theme_${preset.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = preset.name.first().toString(),
                                        color = preset.textColor,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Font controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { if (viewModel.readerFontSize > 14f) viewModel.readerFontSize -= 2f },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.TextDecrease, contentDescription = "Уменьшить шрифт", tint = readerText)
                            }
                            Text(
                                "${viewModel.readerFontSize.toInt()}sp",
                                style = MaterialTheme.typography.labelMedium,
                                color = readerText,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = { if (viewModel.readerFontSize < 32f) viewModel.readerFontSize += 2f },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.TextIncrease, contentDescription = "Увеличить шрифт", tint = readerText)
                            }
                        }

                        // Auxiliary Action Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = onAddNoteClick,
                                colors = ButtonDefaults.textButtonColors(contentColor = readerText)
                            ) {
                                Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Заметка", fontSize = 12.sp)
                            }

                            Button(
                                onClick = onAiAssistantClick,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B5DE5)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Спросить ИИ", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Book text core engine
            val textContent = book.content
            val pageSize = 1000
            val totalPages = kotlin.math.max(1, kotlin.math.ceil(textContent.length.toDouble() / pageSize).toInt())
            val currentPageText = if (textContent.isNotEmpty()) {
                val start = (viewModel.readerPage * pageSize).coerceIn(0, textContent.length)
                val end = ((viewModel.readerPage + 1) * pageSize).coerceIn(0, textContent.length)
                textContent.substring(start, end)
            } else {
                "Книга пуста."
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = currentPageText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = viewModel.readerFontSize.sp,
                        fontFamily = FontFamily.Serif,
                        lineHeight = (viewModel.readerFontSize * 1.5).sp
                    ),
                    color = readerText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reader_text_content")
                )
            }

            // Reader Footer Control
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = readerBg.copy(alpha = 0.9f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.previousPage() },
                        enabled = viewModel.readerPage > 0,
                        modifier = Modifier.testTag("reader_prev_page_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Предыдущая страница",
                            tint = if (viewModel.readerPage > 0) readerText else readerText.copy(alpha = 0.3f)
                        )
                    }

                    Text(
                        "Страница ${viewModel.readerPage + 1} из $totalPages",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = readerText
                    )

                    IconButton(
                        onClick = { viewModel.nextPage() },
                        enabled = (viewModel.readerPage + 1) < totalPages,
                        modifier = Modifier.testTag("reader_next_page_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Следующая страница",
                            tint = if ((viewModel.readerPage + 1) < totalPages) readerText else readerText.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

// ================= TAB 3: NOTES LIST =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesTab(
    notes: List<NoteEntity>,
    viewModel: BookViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Заметки и Цитаты",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Всего заметок: ${notes.size}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar for notes
        OutlinedTextField(
            value = viewModel.noteSearchQuery,
            onValueChange = { viewModel.noteSearchQuery = it },
            placeholder = { Text("Поиск по тексту заметок...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("notes_search_input"),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        val filteredNotes = notes.filter {
            it.noteText.contains(viewModel.noteSearchQuery, ignoreCase = true) ||
                    it.selectedText.contains(viewModel.noteSearchQuery, ignoreCase = true) ||
                    it.bookTitle.contains(viewModel.noteSearchQuery, ignoreCase = true)
        }

        if (filteredNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Note,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Заметок нет",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredNotes) { note ->
                    NoteListItem(
                        note = note,
                        onGoToBook = {
                            viewModel.allBooks.value.find { it.title.equals(note.bookTitle, ignoreCase = true) }?.let { matchedBook ->
                                val updatedBook = matchedBook.copy(currentProgressChar = note.charOffset)
                                viewModel.openBook(updatedBook)
                            }
                        },
                        onDelete = { viewModel.deleteNote(note.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun NoteListItem(
    note: NoteEntity,
    onGoToBook: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("note_card_${note.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        note.bookTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Символ: ${note.charOffset}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                Row {
                    IconButton(
                        onClick = onGoToBook,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Перейти к книге", tint = MaterialTheme.colorScheme.secondary)
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("delete_note_btn_${note.id}")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить заметку", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                    }
                }
            }

            if (note.selectedText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "«${note.selectedText}»",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                        color = Color.DarkGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = note.noteText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ================= TAB 4: SYNC & SETTINGS =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncTab(
    viewModel: BookViewModel
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val currentPreset = readerThemes.find { it.id == viewModel.readerTheme } ?: readerThemes.first()
    val readerBackground = currentPreset.bgColor
    val readerText = currentPreset.textColor

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Настройки и Синхронизация",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Настройте оформление приложения и синхронизируйте ваши книги между вашими устройствами.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Card 0: Custom Themes & Design Preferences
        Card(
            modifier = Modifier.fillMaxWidth().testTag("theme_settings_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Оформление и Темы",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Настройте глобальную тему приложения и цветовую схему для комфортного чтения.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 1. App-wide Global Theme Selection
                Text(
                    "Тема интерфейса:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val appModes = listOf(
                        Triple("system", "Система", Icons.Default.Settings),
                        Triple("light", "Светлая", Icons.Default.WbSunny),
                        Triple("dark", "Тёмная", Icons.Default.DarkMode)
                    )
                    
                    appModes.forEach { (mode, label, icon) ->
                        val isSelected = viewModel.appThemeMode == mode
                        Button(
                            onClick = { viewModel.appThemeMode = mode },
                            modifier = Modifier.weight(1f).height(40.dp).testTag("app_theme_mode_$mode"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(1.dp, if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 2. Reader Theme Selection
                Text(
                    "Стиль бумаги читалки:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    readerThemes.take(3).forEach { preset ->
                        val isSelected = viewModel.readerTheme == preset.id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(preset.bgColor, shape = RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.readerTheme = preset.id }
                                .padding(vertical = 10.dp)
                                .testTag("settings_reader_theme_${preset.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(preset.textColor, shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = preset.name,
                                    color = preset.textColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    readerThemes.drop(3).forEach { preset ->
                        val isSelected = viewModel.readerTheme == preset.id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(preset.bgColor, shape = RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.readerTheme = preset.id }
                                .padding(vertical = 10.dp)
                                .testTag("settings_reader_theme_${preset.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(preset.textColor, shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = preset.name,
                                    color = preset.textColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Reader Font Size Control inside settings
                Text(
                    "Размер шрифта по умолчанию:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = { if (viewModel.readerFontSize > 14f) viewModel.readerFontSize -= 2f },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.TextDecrease, contentDescription = "Уменьшить шрифт")
                    }
                    Text(
                        "${viewModel.readerFontSize.toInt()}sp",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { if (viewModel.readerFontSize < 32f) viewModel.readerFontSize += 2f },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.TextIncrease, contentDescription = "Увеличить шрифт")
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Simple text preview box showing the selected style
                    Box(
                        modifier = Modifier
                            .background(readerBackground, RoundedCornerShape(6.dp))
                            .border(1.dp, readerText.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Абв",
                            color = readerText,
                            fontSize = viewModel.readerFontSize.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 1: Local Device P2P Wi-Fi Server Mode
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Router, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Режим Сервера Синхронизации (Wi-Fi)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Запустите сервер на одном из устройств, а со второго устройства подключитесь к нему по IP-адресу для объединения.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            "IP адрес вашего устройства:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Text(
                            "${viewModel.localIpAddress}:8080",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!viewModel.isServerRunning) {
                        Button(
                            onClick = { viewModel.startSyncServer() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("start_server_btn")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Включить Сервер")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.stopSyncServer() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stop_server_btn")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Выключить")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card 2: Local Device Client Mode
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DeviceHub, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Синхронизация как Клиент (Wi-Fi)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Введите IP-адрес первого устройства (сервера), чтобы мгновенно синхронизировать данные.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = viewModel.remoteServerIp,
                    onValueChange = { viewModel.remoteServerIp = it },
                    placeholder = { Text("Пример: 192.168.1.50") },
                    label = { Text("IP адрес сервера") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("remote_ip_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.startClientSync() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("start_client_sync_btn"),
                    enabled = !viewModel.isSyncLoading && viewModel.remoteServerIp.isNotBlank()
                ) {
                    if (viewModel.isSyncLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Объединить Прогресс")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card 3: REST Cloud Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Облачный Web-Сервер Синхронизации",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Настройте собственный удаленный REST API сервер для автоматической отправки и получения резервных копий.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = viewModel.cloudSyncUrl,
                    onValueChange = { viewModel.cloudSyncUrl = it },
                    placeholder = { Text("https://my-json-endpoint.com/api") },
                    label = { Text("Ссылка на веб-сервер") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.performCloudSync() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isSyncLoading && viewModel.cloudSyncUrl.isNotBlank()
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выгрузить в Облако")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card 4: Manual JSON Export/Import Console
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.TextSnippet, contentDescription = null, tint = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Резервная копия через текст (JSON)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.exportDatabaseState()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_backup_btn")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Экспорт JSON", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (viewModel.exportJsonString.isNotBlank()) {
                                viewModel.importDatabaseState(viewModel.exportJsonString)
                            } else {
                                Toast.makeText(context, "Вставьте JSON в консоль ниже!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_backup_btn")
                    ) {
                        Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Импорт JSON", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = viewModel.exportJsonString,
                    onValueChange = { viewModel.exportJsonString = it },
                    label = { Text("Окно экспорта/импорта") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .testTag("json_console_input"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                if (viewModel.exportJsonString.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(viewModel.exportJsonString))
                            Toast.makeText(context, "Копия сохранена в буфер обмена!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Скопировать текст копии")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // System Sync Console logs
        Text(
            "Логи синхронизации",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Color.Black, RoundedCornerShape(8.dp))
                .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (viewModel.syncServerLogs.isEmpty()) {
                    item {
                        Text(
                            "> Логи отсутствуют. Запустите сервер или выполните подключение.",
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    items(viewModel.syncServerLogs) { log ->
                        Text(
                            "> $log",
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsScreen(
    viewModel: BookViewModel,
    book: BookEntity,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showEditDialog by remember { mutableStateOf(false) }
    var reviewText by remember(book) { mutableStateOf(book.review ?: "") }
    var fileInfoExpanded by remember { mutableStateOf(true) }

    // TextToSpeech setup
    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val t = android.speech.tts.TextToSpeech(context) { status ->
            // Initialized
        }
        tts = t
        onDispose {
            t.stop()
            t.shutdown()
        }
    }

    val progressPercent = if (book.totalCharacters > 0) {
        (book.currentProgressChar * 100f / book.totalCharacters).coerceIn(0f, 100f).toInt()
    } else 0

    // Custom dark background color matching the image (#06161A)
    val customBackground = Color(0xFF06161A)
    val customSurface = Color(0xFF0F262B)
    val customYellow = Color(0xFFE5A93C)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "О документе",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.openBook(book)
                            onBack()
                        }
                    ) {
                        Text(
                            "ЧИТАТЬ",
                            color = customYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    IconButton(
                        onClick = {
                            tts?.let { t ->
                                if (isSpeaking) {
                                    t.stop()
                                    isSpeaking = false
                                } else {
                                    val textToSpeak = book.content.take(1000)
                                    if (textToSpeak.isNotEmpty()) {
                                        t.speak(textToSpeak, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "FB2_SPEAK")
                                        isSpeaking = true
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "Озвучить text",
                            tint = if (isSpeaking) customYellow else Color.White
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Procedural Cover Book
            val startColor = try { Color(android.graphics.Color.parseColor(book.coverGradientStart)) } catch (e: Exception) { Color(0xFFE94560) }
            val endColor = try { Color(android.graphics.Color.parseColor(book.coverGradientEnd)) } catch (e: Exception) { Color(0xFF1A1A2E) }

            val coverBitmap = remember(book.coverPath) {
                book.coverPath?.let { path ->
                    try {
                        if (java.io.File(path).exists()) {
                            android.graphics.BitmapFactory.decodeFile(path)
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            Card(
                modifier = Modifier
                    .width(180.dp)
                    .height(260.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (coverBitmap != null) {
                                Modifier
                            } else {
                                Modifier.background(
                                    Brush.linearGradient(
                                        colors = listOf(startColor, endColor)
                                    )
                                )
                            }
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverBitmap != null) {
                        Image(
                            bitmap = coverBitmap.asImageBitmap(),
                            contentDescription = book.title,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = book.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Large title
            Text(
                text = book.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Star (Favorite)
                IconButton(onClick = { viewModel.toggleFavorite(book.id) }) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "В избранное",
                        tint = if (book.isFavorite) customYellow else Color.Gray
                    )
                }

                // Clock (Recent time info)
                IconButton(onClick = {
                    val formatted = formatRussianDateTime(book.lastReadTime)
                    Toast.makeText(context, "Последнее чтение: $formatted", Toast.LENGTH_LONG).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "История чтения",
                        tint = Color.White
                    )
                }

                // Double check / read completion toggle
                IconButton(onClick = {
                    scope.launch {
                        val updatedProgress = if (book.currentProgressChar == book.totalCharacters) 0 else book.totalCharacters
                        viewModel.repository.updateProgress(book.id, updatedProgress)
                        // Trigger UI update
                        val updatedBook = book.copy(currentProgressChar = updatedProgress)
                        if (viewModel.selectedBook?.id == book.id) {
                            viewModel.selectedBook = updatedBook
                        }
                        viewModel.detailedBook = updatedBook
                        val status = if (updatedProgress > 0) "Прочитана" else "Не прочитана"
                        Toast.makeText(context, "Статус изменен: $status", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Отметить прочитанной",
                        tint = if (book.currentProgressChar == book.totalCharacters) Color.Green else Color.White
                    )
                }

                // Info (Stats/Pages)
                IconButton(onClick = {
                    Toast.makeText(context, "Размер книги: ${book.totalCharacters} симв. (примерно ${book.totalCharacters / 300} стр.)", Toast.LENGTH_LONG).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Статистика",
                        tint = Color.White
                    )
                }

                // Share
                IconButton(onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TITLE, book.title)
                        putExtra(Intent.EXTRA_TEXT, "Читаю книгу «${book.title}» автора ${book.author} в приложении NightRead!")
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Поделиться книгой")
                    context.startActivity(shareIntent)
                }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Поделиться",
                        tint = Color.White
                    )
                }

                // Delete
                IconButton(onClick = {
                    viewModel.deleteBook(book.id)
                    onBack()
                    Toast.makeText(context, "Книга удалена из библиотеки", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить",
                        tint = Color.Red
                    )
                }

                // Edit (Pencil / EditNote)
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = "Редактировать",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Authors Row
            DetailRow(label = "Авторы", value = book.author)

            // Series Row
            DetailRow(label = "Серия", value = book.series ?: "—")

            // Annotation Row (First 400 characters of book content as description)
            val desc = if (book.content.length > 400) book.content.take(400) + "..." else book.content
            DetailRow(label = "Аннотация", value = desc.ifEmpty { "Описание отсутствует." })

            // Language Row
            DetailRow(label = "Язык документа", value = book.language ?: "Русский")

            // Progress Row
            val progressLabel = if (progressPercent > 0) {
                "$progressPercent%, ${formatRussianDateTime(book.lastReadTime)}"
            } else {
                "Не начато"
            }
            DetailRow(label = "Текущая закладка и время последнего чтения", value = progressLabel)

            // Format & Size Row (expandable)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { fileInfoExpanded = !fileInfoExpanded }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val formatName = if (book.filePath?.endsWith(".epub", ignoreCase = true) == true) "EPUB" else if (book.filePath?.endsWith(".zip", ignoreCase = true) == true) "FB2 (ZIP)" else "FB2"
                    val displaySize = if (book.fileSize > 0) "${book.fileSize / 1024} кБ" else "Встроенная"
                    Text(
                        text = "$formatName, $displaySize, Файлы: 1",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Формат и размер файла",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(
                    imageVector = if (fileInfoExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = "Переключить детали",
                    tint = Color.White
                )
            }

            if (fileInfoExpanded) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = customSurface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Файл №1",
                            color = customYellow,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val cleanPath = book.filePath ?: "Интегрированная классика в БД"
                        Text(
                            cleanPath,
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // File SHA-1 Identifier Row
            DetailRow(
                label = "Идентификатор файла документа",
                value = "SHA-1: ${book.sha1 ?: "Не вычислен"}"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Review / Feedback box
            OutlinedTextField(
                value = reviewText,
                onValueChange = {
                    reviewText = it
                    viewModel.updateBookReview(book.id, it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(vertical = 8.dp),
                placeholder = { Text("Добавить отзыв", color = Color.Gray) },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = customYellow,
                    unfocusedBorderColor = Color.Gray,
                    focusedContainerColor = customSurface,
                    unfocusedContainerColor = customSurface
                )
            )
        }
    }

    if (showEditDialog) {
        var tempTitle by remember { mutableStateOf(book.title) }
        var tempAuthor by remember { mutableStateOf(book.author) }
        var tempSeries by remember { mutableStateOf(book.series ?: "") }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Редактировать книгу") },
            text = {
                Column {
                    OutlinedTextField(
                        value = tempTitle,
                        onValueChange = { tempTitle = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    OutlinedTextField(
                        value = tempAuthor,
                        onValueChange = { tempAuthor = it },
                        label = { Text("Автор") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    OutlinedTextField(
                        value = tempSeries,
                        onValueChange = { tempSeries = it },
                        label = { Text("Серия") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val updated = book.copy(
                                title = tempTitle,
                                author = tempAuthor,
                                series = tempSeries.ifEmpty { null }
                            )
                            viewModel.repository.updateBook(updated)
                            viewModel.detailedBook = updated
                            showEditDialog = false
                        }
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = label,
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

fun formatRussianDateTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("d MMMM yyyy 'г.' HH:mm", java.util.Locale("ru"))
    return sdf.format(java.util.Date(timestamp))
}
