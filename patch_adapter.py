import re

with open("app/src/main/java/com/nightread/app/ui/BookAdapter.kt", "r") as f:
    content = f.read()

diff_import = "import androidx.recyclerview.widget.DiffUtil\n"
if diff_import not in content:
    content = content.replace("import java.io.File", "import java.io.File\n" + diff_import)

new_updateData = """
    fun updateData(newBooks: List<BookEntity>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize() = books.size
            override fun getNewListSize() = newBooks.size
            
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return books[oldItemPosition].sha1 == newBooks[newItemPosition].sha1
            }
            
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = books[oldItemPosition]
                val new = newBooks[newItemPosition]
                return old.title == new.title &&
                       old.currentProgressChar == new.currentProgressChar &&
                       old.coverPath == new.coverPath
            }
        }
        
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.books = newBooks
        diffResult.dispatchUpdatesTo(this)
    }
"""

content = re.sub(r'fun updateData\(newBooks: List<BookEntity>\) \{.*?\n    \}', new_updateData.strip(), content, flags=re.DOTALL)

with open("app/src/main/java/com/nightread/app/ui/BookAdapter.kt", "w") as f:
    f.write(content)

print("Patched BookAdapter.kt successfully!")
