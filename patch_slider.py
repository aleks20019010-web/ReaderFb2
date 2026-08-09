import sys

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "r") as f:
    lines = f.readlines()

start_idx = -1
end_idx = -1

for i in range(len(lines)):
    if "val currentPage = pagerState.currentPage" in lines[i]:
        start_idx = i
        # Find end of Slider block
        for j in range(i, len(lines)):
            if "modifier = Modifier.height(4.dp)" in lines[j]:
                end_idx = j + 4
                break
        if end_idx != -1:
            break

if start_idx == -1 or end_idx == -1:
    print("Could not find slider bounds")
    sys.exit(1)

new_slider = """                    val currentOffset = if (readerPages.isNotEmpty() && pagerState.currentPage < readerPages.size) readerPages[pagerState.currentPage].startOffset else 0
                    val totalChars = mainText.length.coerceAtLeast(1)
                    val currentPercent = (currentOffset.toFloat() / totalChars) * 100f

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Прогресс",
                                color = textColor.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = String.format("%.1f%%", currentPercent),
                                color = textColor.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        val currentSliderVal = if (isDraggingSlider) sliderPageValue else currentPercent
                        androidx.compose.material3.Slider(
                            value = currentSliderVal.coerceIn(0f, 100f),
                            onValueChange = { newValue ->
                                isDraggingSlider = true
                                sliderPageValue = newValue
                            },
                            onValueChangeFinished = {
                                val targetOffset = ((sliderPageValue / 100f) * totalChars).toInt().coerceIn(0, totalChars)
                                pendingTargetOffset = targetOffset
                                isDraggingSlider = false
                            },
                            valueRange = 0f..100f,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = textColor,
                                activeTrackColor = textColor,
                                inactiveTrackColor = textColor.copy(alpha = 0.2f)
                            ),
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(textColor, CircleShape)
                                )
                            },
                            track = { sliderState ->
                                androidx.compose.material3.SliderDefaults.Track(
                                    sliderState = sliderState,
                                    colors = androidx.compose.material3.SliderDefaults.colors(
                                        activeTrackColor = textColor,
                                        inactiveTrackColor = textColor.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier.height(4.dp)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                        )
                    }
"""

lines[start_idx:end_idx] = [new_slider]

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "w") as f:
    f.writelines(lines)
print("Patched successfully")
