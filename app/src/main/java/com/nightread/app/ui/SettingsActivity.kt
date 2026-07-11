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

class SettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    
                        Image(
                            painter = painterResource(id = com.nightread.app.R.drawable.bg_starry_night),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x66000000))
                        )
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

        // Font options
        val fontOptions = listOf("Roboto", "Times New Roman", "Georgia", "OpenDyslexic", "Monospace")
        var selectedFont by remember { mutableStateOf(SettingsManager.getFontFamily(context)) }
        
        // Weight options
        val weightOptions = listOf("Normal", "Medium", "Bold", "ExtraBold")
        var selectedWeight by remember { mutableStateOf(SettingsManager.getFontWeight(context)) }
        
        // Float sliders
        var fontSize by remember { mutableStateOf(SettingsManager.getFontSize(context)) }
        var lineSpacing by remember { mutableStateOf(SettingsManager.getLineSpacing(context)) }

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
                        val intent = android.content.Intent(context, com.nightread.app.ui.OnboardingActivity::class.java)
                        context.startActivity(intent)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Обучение")
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
                item { Text("Версия: ${com.nightread.app.BuildConfig.VERSION_NAME}\nРазработчик: NightRead Team\nКонтакты: support@nightread.com") }

                item { Spacer(modifier = Modifier.height(24.dp)) }

                item {
                    SettingsDropdown(
                        label = "Шрифт",
                        options = fontOptions,
                        selectedOption = selectedFont,
                        onOptionSelected = {
                            selectedFont = it
                            SettingsManager.setFontFamily(context, it)
                        }
                    )
                }

                item {
                    SettingsDropdown(
                        label = "Жирность шрифта",
                        options = weightOptions,
                        selectedOption = selectedWeight,
                        onOptionSelected = {
                            selectedWeight = it
                            SettingsManager.setFontWeight(context, it)
                        }
                    )
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Размер шрифта",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${fontSize.toInt()} sp",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = fontSize,
                            onValueChange = {
                                fontSize = it
                                SettingsManager.setFontSize(context, it)
                            },
                            valueRange = 14f..28f,
                            steps = 14
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun <T> SettingsDropdown(
        label: String,
        options: List<T>,
        selectedOption: T,
        onOptionSelected: (T) -> Unit,
        enabled: Boolean = true,
        displayMapper: (T) -> String = { it.toString() }
    ) {
        var expanded by remember { mutableStateOf(false) }
        
        Column(modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled) { expanded = true }
                    .background(if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = displayMapper(selectedOption), style = MaterialTheme.typography.bodyLarge)
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Expand"
                    )
                }
                
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f).background(androidx.compose.ui.graphics.Color(0xFF2A1A3E))
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(text = displayMapper(option), color = androidx.compose.ui.graphics.Color(0xFFE8D8F0)) },
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
}
