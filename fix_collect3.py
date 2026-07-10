import sys

file = 'app/src/main/java/com/nightread/app/ui/ReadingActivity.kt'
content = open(file).read()

content = content.replace("        import kotlinx.coroutines.flow.collectLatest\n", "")
content = content.replace("import kotlinx.coroutines.flow.collectLatest\n", "")
content = content.replace("import kotlinx.coroutines.isActive", "import kotlinx.coroutines.isActive\nimport kotlinx.coroutines.flow.collectLatest")

old_code = """        lifecycleScope.launch {
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

                android.util.Log.d("ReadingActivity", "Settings changed. Layout changed: $layoutChanged")

                lastFontSize = newFontSize
                lastFontFamily = newFontFamily
                lastFontWeight = newFontWeight
                lastLineSpacing = newLineSpacing

                if (layoutChanged && bookContent.isNotEmpty() && splitResult != null) {"""

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

                if (layoutChanged && bookContent.isNotEmpty() && splitResult != null) {"""
content = content.replace(old_code, new_code)
open(file, 'w').write(content)
