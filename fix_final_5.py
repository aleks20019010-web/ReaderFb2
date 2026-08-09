import re

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "r") as f:
    content = f.read()

# Fix fragmentActivity / activity in LaunchedEffect
content = content.replace("LaunchedEffect(activity) {", "LaunchedEffect(fragmentActivity) {")
content = content.replace("if (activity is BookReaderActivity) {", "if (fragmentActivity is BookReaderActivity) {")
content = content.replace("activity.navigationEvents.collect", "fragmentActivity.navigationEvents.collect")

# Fix openTtsSettingsSheet to use `activity: androidx.fragment.app.FragmentActivity?` and `activity?.supportFragmentManager`
content = content.replace("fragmentActivity?.supportFragmentManager?.let { fm ->\n        val sheet = TtsSettingsBottomSheet", "activity?.supportFragmentManager?.let { fm ->\n        val sheet = TtsSettingsBottomSheet")

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "w") as f:
    f.write(content)
