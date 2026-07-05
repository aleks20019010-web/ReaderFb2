with open("app/src/main/java/com/nightread/app/ui/BookViewModel.kt", "r") as f:
    lines = f.readlines()

new_lines = []
skip = False
for i, line in enumerate(lines):
    if i == 48:
        pass # this is '        } else {\n' - wait, let's just do text replacement on the whole file.

with open("app/src/main/java/com/nightread/app/ui/BookViewModel.kt", "r") as f:
    content = f.read()

bad_str = """        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.allBooks.debounce(500)
            } else {
                repository.searchBooks(query).debounce(500)
            }
        } else {
                repository.searchBooks(query)
            }
        }"""

good_str = """        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.allBooks.debounce(500)
            } else {
                repository.searchBooks(query).debounce(500)
            }
        }"""

content = content.replace(bad_str, good_str)

with open("app/src/main/java/com/nightread/app/ui/BookViewModel.kt", "w") as f:
    f.write(content)
