import re

with open('app/src/main/java/com/nightread/app/ui/BookNavigationDialog.kt', 'r') as f:
    content = f.read()

old_code = r"""                        if (isWebViewBook && readerActivity != null) {
                            val subContent = content.substring(0, offset.coerceAtMost(content.length))
                            val tagRegex = Regex("<(p|title|subtitle|h1|h2|h3|h4|h5|h6)(\\s+[^>]*|\\s*)>", RegexOption.IGNORE_CASE)
                            val pIndex = tagRegex.findAll(subContent).count()
                            readerActivity.navigateToParagraph(pIndex)
                        } else {"""

new_code = r"""                        if (isWebViewBook && readerActivity != null) {
                            val pIndex = viewModel.getParagraphIndexFromOffset(offset)
                            readerActivity.navigateToParagraph(pIndex)
                        } else {"""

if old_code in content:
    content = content.replace(old_code, new_code)
    with open('app/src/main/java/com/nightread/app/ui/BookNavigationDialog.kt', 'w') as f:
        f.write(content)
    print("Patched BookNavigationDialog.kt")
else:
    print("old_code not found in BookNavigationDialog.kt")

