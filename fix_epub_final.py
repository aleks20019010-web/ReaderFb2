import re

with open('app/src/main/java/com/nightread/app/data/EpubIdentifierHelper.kt', 'r') as f:
    content = f.read()

# Fix computeFileSha1(file)
content = content.replace('val identifier = computeFileSha1(file) ?: idMatch?.groupValues?.get(1)?.trim() ?: java.util.UUID.randomUUID().toString()', 'val identifier = idMatch?.groupValues?.get(1)?.trim() ?: java.util.UUID.randomUUID().toString()')

# Fix Logs
content = content.replace('Log.e(TAG, "Error getting EPUB metadata: ${file.name}", e)', 'Log.e(TAG, "Error getting EPUB metadata", e)')
content = content.replace('Log.e(TAG, "Error extracting EPUB cover: ${file.name}", e)', 'Log.e(TAG, "Error extracting EPUB cover", e)')

# Fix ZipInputStream in extractAndSaveEpubCoverImpl
content = content.replace('ZipInputStream(file.inputStream().buffered()).use { zip ->', 'ZipInputStream(createInputStream()!!.buffered()).use { zip ->')

# Fix extractAndSaveEpubCoverImpl signature and calls
# It says line 499: Argument type mismatch: actual type is 'InputStream', but 'Function0<InputStream?>' was expected.
# Wait, line 499 is the first `fun extractAndSaveEpubCover(file: File...)` or `bytes`.
# Ah! I see `fun extractAndSaveEpubCover(bytes: ByteArray...): String? { return extractAndSaveEpubCoverImpl({ bytes.inputStream() }, ...)`
# But earlier, maybe there's `extractAndSaveEpubCover(inputStream: InputStream...)` that calls `extractAndSaveEpubCoverImpl(inputStream, ...)` without `{}`!
content = content.replace('extractAndSaveEpubCoverImpl(inputStream, coverPath, sha1, context)', 'extractAndSaveEpubCoverImpl({ inputStream }, coverPath, sha1, context)')

with open('app/src/main/java/com/nightread/app/data/EpubIdentifierHelper.kt', 'w') as f:
    f.write(content)
