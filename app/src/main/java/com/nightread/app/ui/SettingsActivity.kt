package com.nightread.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nightread.app.data.SettingsManager
import com.nightread.app.ui.theme.MyApplicationTheme
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        SettingsScreen(
                            onBack = { finish() }
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsScreen(onBack: () -> Unit) {
        val context = this@SettingsActivity
        val viewModel: com.nightread.app.ui.BookViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let {
                viewModel.importBookFromUri(it, context) { success, message ->
                    CustomToast.show(context, message)
                }
            }
        }



        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Настройки", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // New Functionality Section
                item { Text("Основные функции", style = MaterialTheme.typography.titleMedium) }
                item { 
                    Button(onClick = { viewModel.startLocalBookScan() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Сканировать библиотеку")
                    }
                }
                item {
                    Button(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Ручной импорт")
                    }
                }
                item {
                    Button(onClick = { 
                        val intent = Intent(context, com.nightread.app.MainActivity::class.java).apply {
                            putExtra("OPEN_SYNC", true)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(intent)
                        context.finish()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Синхронизация с Яндекс Диском")
                    }
                }
                item {
                    Button(
                        onClick = { 
                            CleanupDialogFragment().show(
                                context.supportFragmentManager,
                                "CleanupDialogFragment"
                            )
                        }, 
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text("Найти и удалить дубликаты книг")
                    }
                }
                
                item { Text("Настройки библиотеки", style = MaterialTheme.typography.titleMedium) }
                item {
                    var showAllFormats by remember { mutableStateOf(SettingsManager.isShowAllFormatsEnabled(context)) }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Показывать все форматы",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Отображать в библиотеке EPUB, MOBI, PDF, DJVU и другие форматы",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Switch(
                                checked = showAllFormats,
                                onCheckedChange = { checked ->
                                    showAllFormats = checked
                                    SettingsManager.setShowAllFormatsEnabled(context, checked)
                                }
                            )
                        }
                    }
                }
                
                item { Text("Очистка и восстановление", style = MaterialTheme.typography.titleMedium) }
                item { 
                    Button(
                        onClick = { 
                            viewModel.clearScanCache()
                            CustomToast.show(context, "Кэш сканирования успешно сброшен")
                        }, 
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Сбросить кэш сканирования")
                    }
                }
                item { 
                    Button(
                        onClick = { 
                            androidx.appcompat.app.AlertDialog.Builder(context)
                                .setTitle("Очистить библиотеку?")
                                .setMessage("Все книги будут удалены из базы данных приложения. Это действие необратимо.")
                                .setPositiveButton("Удалить всё") { _, _ ->
                                    viewModel.clearLibrary()
                                    viewModel.cancelAllScanningTasks()
                                    CustomToast.show(context, "Библиотека полностью очищена")
                                }
                                .setNegativeButton("Отмена", null)
                                .show()
                        }, 
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Очистить библиотеку")
                    }
                }
                item { Text("Информация", style = MaterialTheme.typography.titleMedium) }
                item { Text("Версия: ${com.nightread.app.BuildConfig.VERSION_NAME}") }
            }
        }
    }
}
