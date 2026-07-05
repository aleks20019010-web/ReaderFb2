package com.nightread.app.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
        
        // Settings states loaded directly from SettingsManager
        var selectedTheme by remember { mutableStateOf(SettingsManager.getTheme(context)) }
        var selectedFont by remember { mutableStateOf(SettingsManager.getFontFamily(context)) }
        var selectedWeight by remember { mutableStateOf(SettingsManager.getFontWeight(context)) }
        var fontSize by remember { mutableStateOf(SettingsManager.getFontSize(context)) }
        var lineSpacing by remember { mutableStateOf(SettingsManager.getLineSpacing(context)) }

        // Dynamic visual properties matching current theme
        val (bgColor, contentColor, cardColor) = when (selectedTheme) {
            "light" -> Triple(Color(0xFFFFFFFF), Color(0xFF121212), Color(0xFFF5F5F5))
            "dark" -> Triple(Color(0xFF1E1A16), Color(0xFFE8E0D8), Color(0xFF2A261F))
            "sepia" -> Triple(Color(0xFFF5F0E8), Color(0xFF3D2C1A), Color(0xFFEDE5D8))
            "sepia_contrast" -> Triple(Color(0xFFF5E6C8), Color(0xFF1A1A1A), Color(0xFFEAD9B8))
            "contrast" -> Triple(Color(0xFF000000), Color(0xFFFFFF00), Color(0xFF111111))
            "beige" -> Triple(Color(0xFFF4ECD8), Color(0xFF3B2F1F), Color(0xFFEADFC5))
            else -> Triple(Color(0xFFF5F0E8), Color(0xFF3D2C1A), Color(0xFFEDE5D8))
        }

        val themeOptions = listOf("light", "dark", "sepia", "sepia_contrast", "contrast", "beige")
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Drag handle to indicate it's draggable
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(contentColor.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
            )

            // Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Настройки чтения",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = "Применить",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    modifier = Modifier
                        .clickable { dismiss() }
                        .padding(8.dp)
                )
            }

            // 1. Theme Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Тема оформления",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(themeOptions.size) { index ->
                        val themeKey = themeOptions[index]
                        val isSelected = themeKey == selectedTheme
                        SettingsChip(
                            text = themeNames[themeKey] ?: themeKey,
                            selected = isSelected,
                            onClick = {
                                selectedTheme = themeKey
                                SettingsManager.setTheme(context, themeKey)
                            },
                            bgColor = bgColor,
                            contentColor = contentColor
                        )
                    }
                }
            }

            // 2. Font Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Шрифт",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(fontOptions.size) { index ->
                        val fontKey = fontOptions[index]
                        val isSelected = fontKey == selectedFont
                        SettingsChip(
                            text = fontKey,
                            selected = isSelected,
                            onClick = {
                                selectedFont = fontKey
                                SettingsManager.setFontFamily(context, fontKey)
                            },
                            bgColor = bgColor,
                            contentColor = contentColor
                        )
                    }
                }
            }

            // 3. Weight Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Жирность шрифта",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(weightOptions.size) { index ->
                        val weightKey = weightOptions[index]
                        val isSelected = weightKey == selectedWeight
                        SettingsChip(
                            text = when (weightKey) {
                                "Normal" -> "Обычный"
                                "Medium" -> "Средний"
                                "Bold" -> "Жирный"
                                "ExtraBold" -> "Сверхжирный"
                                else -> weightKey
                            },
                            selected = isSelected,
                            onClick = {
                                selectedWeight = weightKey
                                SettingsManager.setFontWeight(context, weightKey)
                            },
                            bgColor = bgColor,
                            contentColor = contentColor
                        )
                    }
                }
            }

            // 4. Font Size Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Размер шрифта",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = "${fontSize.toInt()} sp",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
                Slider(
                    value = fontSize,
                    onValueChange = {
                        fontSize = it
                    },
                    onValueChangeFinished = {
                        SettingsManager.setFontSize(context, fontSize)
                    },
                    valueRange = 14f..28f,
                    steps = 14,
                    colors = SliderDefaults.colors(
                        thumbColor = contentColor,
                        activeTrackColor = contentColor,
                        inactiveTrackColor = contentColor.copy(alpha = 0.24f)
                    )
                )
            }

            // 5. Line Spacing Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Межстрочный интервал",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = String.format("%.2f", lineSpacing),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
                Slider(
                    value = lineSpacing,
                    onValueChange = {
                        lineSpacing = it
                    },
                    onValueChangeFinished = {
                        SettingsManager.setLineSpacing(context, lineSpacing)
                    },
                    valueRange = 1.0f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = contentColor,
                        activeTrackColor = contentColor,
                        inactiveTrackColor = contentColor.copy(alpha = 0.24f)
                    )
                )
            }

            // 6. Live Preview Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = cardColor
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Предпросмотр настроек:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "При изменении шрифта, размера или интервала этот текст пересчитывается мгновенно.",
                        color = contentColor,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
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
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    fun SettingsChip(
        text: String,
        selected: Boolean,
        onClick: () -> Unit,
        bgColor: Color,
        contentColor: Color
    ) {
        val chipBg = if (selected) contentColor.copy(alpha = 0.15f) else Color.Transparent
        val chipBorder = if (selected) contentColor else contentColor.copy(alpha = 0.25f)
        val chipWeight = if (selected) FontWeight.Bold else FontWeight.Normal

        Box(
            modifier = Modifier
                .border(1.dp, chipBorder, RoundedCornerShape(16.dp))
                .background(chipBg, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = contentColor,
                fontWeight = chipWeight,
                fontSize = 13.sp
            )
        }
    }
}
