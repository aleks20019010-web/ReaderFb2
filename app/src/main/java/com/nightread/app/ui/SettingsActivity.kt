package com.nightread.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nightread.app.data.SettingsManager
import com.nightread.app.ui.theme.MyApplicationTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(
                        onBack = { finish() }
                    )
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
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Theme states (Existing)
        val themeOptions = listOf("light", "dark", "sepia", "contrast", "beige")
        val themeNames = mapOf(
            "light" to "День",
            "dark" to "Ночь",
            "sepia" to "Сепия",
            "contrast" to "Контраст",
            "beige" to "Бежевый"
        )
        var selectedTheme by remember { mutableStateOf(SettingsManager.getTheme(context)) }
        
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
            }
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
                item { Text("Очистка", style = MaterialTheme.typography.titleMedium) }
                item { 
                    Button(onClick = { viewModel.clearCache() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Очистить кэш")
                    }
                }
                item { Text("Информация", style = MaterialTheme.typography.titleMedium) }
                item { Text("Версия: 1.0.0\nРазработчик: NightRead Team\nКонтакты: support@nightread.com") }
                
                item { Spacer(modifier = Modifier.height(24.dp)) }

                // Existing Reading Settings Section
                item { Text("Настройки чтения", style = MaterialTheme.typography.titleMedium) }
                
                item {
                    SettingsDropdown(
                        label = "Тема оформления",
                        options = themeOptions,
                        selectedOption = selectedTheme,
                        onOptionSelected = {
                            selectedTheme = it
                            SettingsManager.setTheme(context, it)
                        },
                        displayMapper = { themeNames[it] ?: it }
                    )
                }

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
        displayMapper: (T) -> String = { it.toString() }
    ) {
        var expanded by remember { mutableStateOf(false) }
        
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
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
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(text = displayMapper(option)) },
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
