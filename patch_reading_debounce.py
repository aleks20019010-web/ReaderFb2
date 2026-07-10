import sys

file = 'app/src/main/java/com/nightread/app/ui/ReadingActivity.kt'
content = open(file).read()

old_code = """        lifecycleScope.launch {
            SettingsManager.settingsChanged.collect {
                updateSystemBarsColors()
                updatePageTransformer()
                // Check if layout-affecting settings changed
                val newFontSize = SettingsManager.getFontSize(this@ReadingActivity)
                val newFontFamily = SettingsManager.getFontFamily(this@ReadingActivity)
                val newFontWeight = SettingsManager.getFontWeight(this@ReadingActivity)
                val newLineSpacing = SettingsManager.getLineSpacing(this@ReadingActivity)
                
                val layoutChanged = newFontSize != lastFontSize || 
                                   newFontFamily != lastFontFamily || 
                                   newFontWeight != lastFontWeight ||
                                   newLineSpacing != lastLineSpacing

                android.util.Log.d("ReadingActivity", "Settings changed. Layout changed: $layoutChanged")

                lastFontSize = newFontSize
                lastFontFamily = newFontFamily
                lastFontWeight = newFontWeight
                lastLineSpacing = newLineSpacing
                if (layoutChanged && bookContent.isNotEmpty() && splitResult != null) {
                    val currentIdx = viewPager.currentItem
                    val targetOffset = if (currentIdx >= 0 && currentIdx < splitResult.offsets.size) {
                        splitResult.offsets[currentIdx]
                    } else {
                        -1
                    }
                    recalculatePages(targetOffset)
                }
            }
        }"""

new_code = """        lifecycleScope.launch {
            SettingsManager.settingsChanged.collectLatest {
                updateSystemBarsColors()
                updatePageTransformer()
                // Check if layout-affecting settings changed
                val newFontSize = SettingsManager.getFontSize(this@ReadingActivity)
                val newFontFamily = SettingsManager.getFontFamily(this@ReadingActivity)
                val newFontWeight = SettingsManager.getFontWeight(this@ReadingActivity)
                val newLineSpacing = SettingsManager.getLineSpacing(this@ReadingActivity)
                
                val layoutChanged = newFontSize != lastFontSize || 
                                   newFontFamily != lastFontFamily || 
                                   newFontWeight != lastFontWeight ||
                                   newLineSpacing != lastLineSpacing

                if (layoutChanged) {
                    kotlinx.coroutines.delay(300) // Debounce fast slider changes
                }

                android.util.Log.d("ReadingActivity", "Settings changed. Layout changed: $layoutChanged")

                lastFontSize = newFontSize
                lastFontFamily = newFontFamily
                lastFontWeight = newFontWeight
                lastLineSpacing = newLineSpacing
                if (layoutChanged && bookContent.isNotEmpty() && splitResult != null) {
                    val currentIdx = viewPager.currentItem
                    val targetOffset = if (currentIdx >= 0 && currentIdx < splitResult.offsets.size) {
                        splitResult.offsets[currentIdx]
                    } else {
                        -1
                    }
                    recalculatePages(targetOffset)
                }
            }
        }"""

content = content.replace("SettingsManager.settingsChanged.collect {", "kotlinx.coroutines.flow.collectLatest {")
content = content.replace(old_code, new_code)
open(file, 'w').write(content)
