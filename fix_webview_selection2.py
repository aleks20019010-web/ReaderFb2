import re

file_path = "app/src/main/java/com/nightread/app/ui/BookReaderActivity.kt"
with open(file_path, "r") as f:
    content = f.read()

replacement = """
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            textZoom = 100
            allowFileAccess = true
            allowContentAccess = true
        }

        webView.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                mode?.menuInflater?.inflate(R.menu.menu_ai_selection, menu)
                return true
            }

            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                return false
            }

            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean {
                if (item?.itemId == R.id.action_ai_assistant) {
                    webView.evaluateJavascript("(function(){ var sel = window.getSelection(); if(sel.rangeCount > 0) { var container = sel.getRangeAt(0).commonAncestorContainer; while(container && container.nodeType !== 1) { container = container.parentNode; } return JSON.stringify({text: sel.toString(), context: container ? (container.innerText || '') : ''}); } return ''; })();") { result ->
                        if (result != null && result != "null" && result.isNotBlank() && result != "\\"\\"") {
                            try {
                                val unescaped = result.substring(1, result.length - 1).replace("\\\\\"", "\"").replace("\\\\\\\\", "\\\\")
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
                    mode?.finish()
                    return true
                }
                return false
            }

            override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
        }
"""

content = re.sub(
    r'webView\.settings\.apply \{[\s\S]*?allowContentAccess = true\s*\}',
    replacement.strip(),
    content
)

with open(file_path, "w") as f:
    f.write(content)

