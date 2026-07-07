package com.nightread.app.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import com.nightread.app.data.SettingsManager

class SettingsBottomSheet : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                SettingsBottomSheetContent()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            window.setGravity(Gravity.BOTTOM)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.setWindowAnimations(android.R.style.Animation_InputMethod)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsBottomSheetContent() {
        val context = requireContext()
        
        var selectedTheme by remember { mutableStateOf(SettingsManager.getTheme(context)) }
        var selectedFont by remember { mutableStateOf(SettingsManager.getFontFamily(context)) }
        var selectedWeight by remember { mutableStateOf(SettingsManager.getFontWeight(context)) }
        var fontSize by remember { mutableStateOf(SettingsManager.getFontSize(context)) }
        var lineSpacing by remember { mutableStateOf(SettingsManager.getLineSpacing(context)) }
        
        val themeNames = mapOf(
            "light" to "День",
            "dark" to "Ночь",
            "sepia" to "Сепия",
            "sepia_contrast" to "Сепия контраст",
            "contrast" to "Контраст",
            "beige" to "Бежевый"
        )
        val fontOptions = listOf("Roboto", "Times New Roman", "Georgia", "Merriweather", "OpenDyslexic", "Monospace")
        val weightOptions = listOf("Normal", "Medium", "Bold", "ExtraBold")
        val themeOptions = themeNames.keys.toList()

        val bgColor = Color(0xFF1A0D2A)
        val contentColor = Color(0xFFE0E0E0)
        val accentColor = Color(0xFF9B59B6)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(contentColor.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Настройки",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = "Закрыть",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    modifier = Modifier
                        .clickable { dismiss() }
                        .padding(8.dp)
                )
            }

            // 1. Font (Dropdown)
            SettingsDropdown(
                label = "Шрифт",
                options = fontOptions,
                selectedOption = selectedFont,
                onOptionSelected = { 
                    selectedFont = it
                    SettingsManager.setFontFamily(context, it)
                },
                contentColor = contentColor,
                bgColor = bgColor
            )

            // 2. Font Size (Slider)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Размер шрифта", color = contentColor, fontWeight = FontWeight.Bold)
                    Text("${fontSize.toInt()} sp", color = contentColor)
                }
                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    onValueChangeFinished = { SettingsManager.setFontSize(context, fontSize) },
                    valueRange = 14f..28f,
                    steps = 13,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = contentColor.copy(alpha = 0.24f)
                    )
                )
            }

            // 3. Font Weight (Dropdown)
            SettingsDropdown(
                label = "Жирность",
                options = weightOptions,
                selectedOption = selectedWeight,
                onOptionSelected = { 
                    selectedWeight = it
                    SettingsManager.setFontWeight(context, it)
                },
                contentColor = contentColor,
                bgColor = bgColor
            )

            // 4. Theme (Dropdown)
            SettingsDropdown(
                label = "Тема",
                options = themeOptions,
                displayNames = themeNames,
                selectedOption = selectedTheme,
                onOptionSelected = { 
                    selectedTheme = it
                    SettingsManager.setTheme(context, it)
                },
                contentColor = contentColor,
                bgColor = bgColor
            )

            // 5. Line Spacing (Slider)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Межстрочный интервал", color = contentColor, fontWeight = FontWeight.Bold)
                    Text(String.format("%.2f", lineSpacing), color = contentColor)
                }
                Slider(
                    value = lineSpacing,
                    onValueChange = { lineSpacing = it },
                    onValueChangeFinished = { SettingsManager.setLineSpacing(context, lineSpacing) },
                    valueRange = 1.0f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = contentColor.copy(alpha = 0.24f)
                    )
                )
            }
            
            var isAutoDiscovery by remember { mutableStateOf(SettingsManager.isAutoDiscoveryEnabled(context)) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        isAutoDiscovery = !isAutoDiscovery
                        SettingsManager.setAutoDiscoveryEnabled(context, isAutoDiscovery)
                        if (isAutoDiscovery) {
                            com.nightread.app.service.AutoDiscoveryWorker.schedule(context)
                            com.nightread.app.service.AutoDiscoveryService.start(context)
                        } else {
                            com.nightread.app.service.AutoDiscoveryWorker.cancel(context)
                            com.nightread.app.service.AutoDiscoveryService.stop(context)
                        }
                    }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Авто-обнаружение",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = "Отслеживание новых файлов fb2 и fb2.zip",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = isAutoDiscovery,
                    onCheckedChange = { 
                        isAutoDiscovery = it
                        SettingsManager.setAutoDiscoveryEnabled(context, isAutoDiscovery)
                        if (isAutoDiscovery) {
                            com.nightread.app.service.AutoDiscoveryWorker.schedule(context)
                            com.nightread.app.service.AutoDiscoveryService.start(context)
                        } else {
                            com.nightread.app.service.AutoDiscoveryWorker.cancel(context)
                            com.nightread.app.service.AutoDiscoveryService.stop(context)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = bgColor,
                        checkedTrackColor = accentColor,
                        uncheckedThumbColor = contentColor,
                        uncheckedTrackColor = bgColor,
                        uncheckedBorderColor = contentColor
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsDropdown(
        label: String,
        options: List<String>,
        displayNames: Map<String, String>? = null,
        selectedOption: String,
        onOptionSelected: (String) -> Unit,
        contentColor: Color,
        bgColor: Color
    ) {
        var expanded by remember { mutableStateOf(false) }

        Column {
            Text(label, color = contentColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = displayNames?.get(selectedOption) ?: selectedOption,
                    onValueChange = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = contentColor,
                        unfocusedTextColor = contentColor,
                        focusedBorderColor = contentColor,
                        unfocusedBorderColor = contentColor.copy(alpha = 0.5f),
                        focusedTrailingIconColor = contentColor,
                        unfocusedTrailingIconColor = contentColor,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(bgColor)
                ) {
                    options.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(displayNames?.get(selectionOption) ?: selectionOption, color = contentColor) },
                            onClick = {
                                onOptionSelected(selectionOption)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
