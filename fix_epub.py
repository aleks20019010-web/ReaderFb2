import re

with open('app/src/main/java/com/nightread/app/data/EpubIdentifierHelper.kt', 'r') as f:
    content = f.read()

# Replace file.inputStream().buffered() with createInputStream()!!.buffered() in getEpubMetadataImpl
content = re.sub(r'private fun getEpubMetadataImpl\(inputStream: java\.io\.InputStream\): EpubMetadata\? \{([\s\S]*?)ZipInputStream\(file\.inputStream\(\)\.buffered\(\)\)\.use \{ zip ->', r'private fun getEpubMetadataImpl(createInputStream: () -> java.io.InputStream?): EpubMetadata? {\1ZipInputStream(createInputStream()!!.buffered()).use { zip ->', content)

# Also fix the getEpubMetadata overloads
content = content.replace('fun getEpubMetadata(inputStream: java.io.InputStream): EpubMetadata? {\n        return getEpubMetadataImpl(inputStream)\n    }', 'fun getEpubMetadata(createInputStream: () -> java.io.InputStream?): EpubMetadata? {\n        return getEpubMetadataImpl(createInputStream)\n    }')
content = content.replace('fun getEpubMetadata(bytes: ByteArray): EpubMetadata? {\n        return getEpubMetadataImpl(bytes.inputStream())\n    }', 'fun getEpubMetadata(bytes: ByteArray): EpubMetadata? {\n        return getEpubMetadataImpl({ bytes.inputStream() })\n    }')
content = content.replace('fun getEpubMetadata(file: File): EpubMetadata? {\n        return file.inputStream().use { getEpubMetadataImpl(it) }\n    }', 'fun getEpubMetadata(file: File): EpubMetadata? {\n        return getEpubMetadataImpl({ file.inputStream() })\n    }')

# Now for extractAndSaveEpubCoverImpl
content = re.sub(r'private fun extractAndSaveEpubCoverImpl\(createInputStream: \(\) -> java\.io\.InputStream\?, coverPath: String\?, sha1: String, context: android\.content\.Context\): String\? \{([\s\S]*?)ZipInputStream\(file\.inputStream\(\)\.buffered\(\)\)\.use \{ zip ->([\s\S]*?)ZipInputStream\(file\.inputStream\(\)\.buffered\(\)\)\.use \{ zip ->([\s\S]*?)ZipInputStream\(file\.inputStream\(\)\.buffered\(\)\)\.use \{ zip ->', r'private fun extractAndSaveEpubCoverImpl(createInputStream: () -> java.io.InputStream?, coverPath: String?, sha1: String, context: android.content.Context): String? {\1ZipInputStream(createInputStream()!!.buffered()).use { zip ->\2ZipInputStream(createInputStream()!!.buffered()).use { zip ->\3ZipInputStream(createInputStream()!!.buffered()).use { zip ->', content)

content = content.replace('fun extractAndSaveEpubCover(bytes: ByteArray, coverPath: String?, sha1: String, context: android.content.Context): String? {\n        return extractAndSaveEpubCoverImpl(bytes.inputStream(), coverPath, sha1, context)\n    }', 'fun extractAndSaveEpubCover(bytes: ByteArray, coverPath: String?, sha1: String, context: android.content.Context): String? {\n        return extractAndSaveEpubCoverImpl({ bytes.inputStream() }, coverPath, sha1, context)\n    }')

content = content.replace('fun extractAndSaveEpubCover(file: File, coverPath: String?, sha1: String, context: android.content.Context): String? {\n        return file.inputStream().use { extractAndSaveEpubCoverImpl(it, coverPath, sha1, context) }\n    }', 'fun extractAndSaveEpubCover(file: File, coverPath: String?, sha1: String, context: android.content.Context): String? {\n        return extractAndSaveEpubCoverImpl({ file.inputStream() }, coverPath, sha1, context)\n    }')

with open('app/src/main/java/com/nightread/app/data/EpubIdentifierHelper.kt', 'w') as f:
    f.write(content)
