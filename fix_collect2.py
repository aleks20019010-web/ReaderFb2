import sys

file = 'app/src/main/java/com/nightread/app/ui/ReadingActivity.kt'
content = open(file).read()

old_code = """        lifecycleScope.launch {
            kotlinx.coroutines.flow.collectLatest(SettingsManager.settingsChanged) {"""

new_code = """        import kotlinx.coroutines.flow.collectLatest
        lifecycleScope.launch {
            SettingsManager.settingsChanged.collectLatest {"""
content = content.replace(old_code, new_code)
open(file, 'w').write(content)
