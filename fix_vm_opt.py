with open("app/src/main/java/com/nightread/app/ui/BookViewModel.kt", "r") as f:
    content = f.read()
    
content = content.replace("@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)", "@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)")

with open("app/src/main/java/com/nightread/app/ui/BookViewModel.kt", "w") as f:
    f.write(content)
