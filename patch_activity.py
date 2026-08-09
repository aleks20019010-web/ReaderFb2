import re

with open("app/src/main/java/com/nightread/app/ui/BookReaderActivity.kt", "r") as f:
    content = f.read()

# Add SharedFlow to Activity
import_flow = "import kotlinx.coroutines.flow.MutableSharedFlow\n"
content = content.replace("import kotlinx.coroutines.launch\n", "import kotlinx.coroutines.launch\n" + import_flow)

shared_flow_def = """
    val navigationEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    
    fun navigateToOffset(offset: Int) {
        navigationEvents.tryEmit(offset)
    }
"""
content = content.replace("    fun navigateToOffset(offset: Int) {\n        Toast.makeText(this, \"Переход к позиции $offset\", Toast.LENGTH_SHORT).show()\n    }", shared_flow_def)

with open("app/src/main/java/com/nightread/app/ui/BookReaderActivity.kt", "w") as f:
    f.write(content)
