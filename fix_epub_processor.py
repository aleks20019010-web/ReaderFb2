import re

with open('app/src/main/java/com/nightread/app/service/NewBookScanner.kt', 'r') as f:
    content = f.read()

content = content.replace(
'''            val meta = withContext(Dispatchers.Default) {
                contentResolver.openInputStream(book.uri)?.use { input ->
                    EpubIdentifierHelper.getEpubMetadata(input)
                }
            } ?: return null''',
'''            val meta = withContext(Dispatchers.Default) {
                EpubIdentifierHelper.getEpubMetadata { contentResolver.openInputStream(book.uri) }
            } ?: return null'''
)

content = content.replace(
'''            val savedCover = try {
                contentResolver.openInputStream(book.uri)?.use { input ->
                    EpubIdentifierHelper.extractAndSaveEpubCover(
                        input,
                        meta.coverPath,
                        meta.identifier,
                        context
                    )
                }
            } catch (e: Exception) {''',
'''            val savedCover = try {
                EpubIdentifierHelper.extractAndSaveEpubCover(
                    { contentResolver.openInputStream(book.uri) },
                    meta.coverPath,
                    meta.identifier,
                    context
                )
            } catch (e: Exception) {'''
)

with open('app/src/main/java/com/nightread/app/service/NewBookScanner.kt', 'w') as f:
    f.write(content)
