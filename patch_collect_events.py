import re

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "r") as f:
    content = f.read()

events_logic = """
    var pendingTargetOffset by remember { mutableStateOf<Int?>(null) }
    // --------------------------------
    
    val activity = context as? androidx.fragment.app.FragmentActivity
    LaunchedEffect(activity) {
        if (activity is BookReaderActivity) {
            activity.navigationEvents.collect { offset ->
                pendingTargetOffset = offset
            }
        }
    }
"""
content = content.replace("    var pendingTargetOffset by remember { mutableStateOf<Int?>(null) }\n    // --------------------------------", events_logic)

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "w") as f:
    f.write(content)
