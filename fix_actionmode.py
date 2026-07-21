import sys
import re

file_path = "app/src/main/java/com/nightread/app/ui/BookReaderActivity.kt"
with open(file_path, "r") as f:
    content = f.read()

# 1. Remove the customSelectionActionModeCallback block
content = re.sub(
    r'webView\.customSelectionActionModeCallback = object : android\.view\.ActionMode\.Callback \{[\s\S]*?override fun onDestroyActionMode\(mode: android\.view\.ActionMode\?\) \{\}\s*\}',
    "",
    content
)

# 2. Add override fun onActionModeStarted to the class
action_mode_override = """
    override fun onActionModeStarted(mode: android.view.ActionMode?) {
        super.onActionModeStarted(mode)
        mode?.menuInflater?.inflate(R.menu.menu_ai_selection, mode.menu)
        mode?.menu?.findItem(R.id.action_ai_assistant)?.setOnMenuItemClickListener {
            webView.evaluateJavascript("(function(){ var sel = window.getSelection(); if(sel.rangeCount > 0) { var container = sel.getRangeAt(0).commonAncestorContainer; while(container && container.nodeType !== 1) { container = container.parentNode; } return JSON.stringify({text: sel.toString(), context: container ? (container.innerText || '') : ''}); } return ''; })();") { result ->
                if (result != null && result != "null" && result.isNotBlank() && result != "\\"\\"") {
                    try {
                        val unescaped = result.substring(1, result.length - 1).replace("\\\\\"", "\\"").replace("\\\\\\\\", "\\\\")
                        val json = org.json.JSONObject(unescaped)
                        val text = json.optString("text", "")
                        val contextStr = json.optString("context", "")
                        if (text.isNotBlank()) {
                            showWordActionOrNoteDialog(text, contextStr)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            mode.finish()
            true
        }
    }
"""

# Insert it before the last closing brace of BookReaderActivity
# Actually it's better to insert it right before `private fun initWebView()` or something.
# Let's insert it before `private fun loadBookData()`

content = content.replace("private fun loadBookData() {", action_mode_override + "\n    private fun loadBookData() {")

with open(file_path, "w") as f:
    f.write(content)
