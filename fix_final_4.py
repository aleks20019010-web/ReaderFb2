import re

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "r") as f:
    content = f.read()

# Add imports
if "import androidx.compose.animation.slideInVertically" not in content:
    content = content.replace("import androidx.compose.animation.fadeOut\n", "import androidx.compose.animation.fadeOut\nimport androidx.compose.animation.slideInVertically\nimport androidx.compose.animation.slideOutVertically\n")

if "import androidx.compose.material.icons.filled.Menu" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.List\n", "import androidx.compose.material.icons.filled.List\nimport androidx.compose.material.icons.filled.Menu\n")

# Fix activity shadowing
content = content.replace("val activity = context as? androidx.fragment.app.FragmentActivity", "val fragmentActivity = context as? androidx.fragment.app.FragmentActivity")
content = content.replace("activity?.supportFragmentManager", "fragmentActivity?.supportFragmentManager")

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "w") as f:
    f.write(content)
