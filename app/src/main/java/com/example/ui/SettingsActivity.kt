package com.example.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.data.SettingsManager
import com.example.ui.theme.MyApplicationTheme

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
        
        // Theme states
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
                    title = { Text("Настройки чтения", fontWeight = FontWeight.Bold) },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Theme Selection
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

                // Font Family Selection
                SettingsDropdown(
                    label = "Шрифт",
                    options = fontOptions,
                    selectedOption = selectedFont,
                    onOptionSelected = {
                        selectedFont = it
                        SettingsManager.setFontFamily(context, it)
                    }
                )

                // Font Weight Selection
                SettingsDropdown(
                    label = "Жирность шрифта",
                    options = weightOptions,
                    selectedOption = selectedWeight,
                    onOptionSelected = {
                        selectedWeight = it
                        SettingsManager.setFontWeight(context, it)
                    }
                )

                // Font Size Slider
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

                // Line Spacing Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Межстрочный интервал",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format("%.2f", lineSpacing),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = lineSpacing,
                        onValueChange = {
                            lineSpacing = it
                            SettingsManager.setLineSpacing(context, it)
                        },
                        valueRange = 1.0f..2.0f
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Live Preview Box to demonstrate styling
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Предпросмотр текста:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Так будет выглядеть текст книги на экране чтения при текущих настройках.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = when (selectedWeight) {
                                    "Medium" -> FontWeight.Medium
                                    "Bold" -> FontWeight.Bold
                                    "ExtraBold" -> FontWeight.ExtraBold
                                    else -> FontWeight.Normal
                                }
                            )
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
