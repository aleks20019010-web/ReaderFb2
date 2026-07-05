import re

with open("app/src/main/java/com/nightread/app/ui/BookViewModel.kt", "r") as f:
    content = f.read()

import_str = "import kotlinx.coroutines.flow.debounce\n"
if "import kotlinx.coroutines.flow.debounce" not in content:
    content = content.replace("import kotlinx.coroutines.flow.flatMapLatest", "import kotlinx.coroutines.flow.flatMapLatest\n" + import_str)

new_str = """        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.allBooks.debounce(500)
            } else {
                repository.searchBooks(query).debounce(500)
            }
        }"""
        
content = re.sub(r'\.flatMapLatest \{ query ->.*?\}', new_str, content, flags=re.DOTALL)

with open("app/src/main/java/com/nightread/app/ui/BookViewModel.kt", "w") as f:
    f.write(content)
print("Done")
