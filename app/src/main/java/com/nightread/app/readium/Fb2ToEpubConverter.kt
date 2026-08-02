package com.nightread.app.readium

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

object Fb2ToEpubConverter {

    private const val TAG = "Fb2ToEpubConverter"

    fun convertIfNeeded(context: Context, inputFile: File): File {
        val nameLower = inputFile.name.lowercase()
        if (!nameLower.endsWith(".fb2") && !nameLower.endsWith(".fb2.zip")) {
            return inputFile
        }

        val outputDir = File(context.cacheDir, "converted_epubs")
        if (!outputDir.exists()) outputDir.mkdirs()

        val fileHash = getFileHash(inputFile)
        val outputFile = File(outputDir, "$fileHash.epub")

        if (outputFile.exists() && outputFile.length() > 0) {
            Log.d(TAG, "Using cached converted EPUB: ${outputFile.absolutePath}")
            return outputFile
        }

        Log.d(TAG, "Converting FB2 to EPUB: ${inputFile.name}")
        try {
            convertFb2ToEpub(inputFile, outputFile)
            Log.d(TAG, "Successfully converted FB2 to EPUB: ${outputFile.absolutePath}")
            return outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert FB2 to EPUB, returning original file", e)
            return inputFile
        }
    }

    private fun getFileHash(file: File): String {
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            val bytes = file.readBytes().take(1024 * 1024).toByteArray()
            val digest = md.digest(bytes)
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            file.name.hashCode().toString()
        }
    }

    private fun convertFb2ToEpub(inputFile: File, outputFile: File) {
        val xmlInputStream = getXmlInputStream(inputFile)
            ?: throw IllegalArgumentException("Cannot open XML input stream for FB2")

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val builder = factory.newDocumentBuilder()
        val doc = xmlInputStream.use { builder.parse(it) }
        doc.documentElement.normalize()

        var bookTitle = "Untitled"
        var bookAuthor = "Unknown Author"
        val titleNodes = doc.getElementsByTagName("title-info")
        if (titleNodes.length > 0) {
            val titleInfo = titleNodes.item(0) as Element
            val bt = titleInfo.getElementsByTagName("book-title")
            if (bt.length > 0) bookTitle = bt.item(0).textContent.trim()

            val authors = titleInfo.getElementsByTagName("author")
            if (authors.length > 0) {
                val authorElem = authors.item(0) as Element
                val fn = authorElem.getElementsByTagName("first-name").item(0)?.textContent ?: ""
                val ln = authorElem.getElementsByTagName("last-name").item(0)?.textContent ?: ""
                bookAuthor = "$fn $ln".trim().ifEmpty { "Unknown Author" }
            }
        }

        val binaries = mutableMapOf<String, ByteArray>()
        val binaryNodes = doc.getElementsByTagName("binary")
        for (i in 0 until binaryNodes.length) {
            val binElem = binaryNodes.item(i) as Element
            val id = binElem.getAttribute("id").replace("#", "")
            val content = binElem.textContent.replace("\n", "").replace("\r", "").trim()
            try {
                val bytes = Base64.decode(content, Base64.DEFAULT)
                binaries[id] = bytes
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode binary $id", e)
            }
        }

        val chaptersHtml = mutableListOf<String>()
        val chapterTitles = mutableListOf<String>()

        val bodyNodes = doc.getElementsByTagName("body")
        for (i in 0 until bodyNodes.length) {
            val bodyElem = bodyNodes.item(i) as Element
            val sections = bodyElem.getElementsByTagName("section")
            if (sections.length > 0) {
                for (j in 0 until sections.length) {
                    val secElem = sections.item(j) as Element
                    val titleElem = secElem.getElementsByTagName("title").item(0) as? Element
                    val titleText = titleElem?.textContent?.trim() ?: "Глава ${j + 1}"
                    val secHtml = parseElementToHtml(secElem)
                    chaptersHtml.add(secHtml)
                    chapterTitles.add(titleText)
                }
            } else {
                val bodyHtml = parseElementToHtml(bodyElem)
                chaptersHtml.add(bodyHtml)
                chapterTitles.add("Текст книги")
            }
        }

        if (chaptersHtml.isEmpty()) {
            chaptersHtml.add("<h1>" + escapeXml(bookTitle) + "</h1><p>Содержимое книги не найдено.</p>")
            chapterTitles.add("Глава 1")
        }

        val tempZipFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZipFile))).use { zos ->
            // 1. mimetype (MUST be uncompressed and first)
            val mimetypeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mimetypeEntry = ZipEntry("mimetype")
            mimetypeEntry.method = ZipEntry.STORED
            mimetypeEntry.size = mimetypeBytes.size.toLong()
            mimetypeEntry.compressedSize = mimetypeBytes.size.toLong()
            val crc = java.util.zip.CRC32()
            crc.update(mimetypeBytes)
            mimetypeEntry.crc = crc.value
            zos.putNextEntry(mimetypeEntry)
            zos.write(mimetypeBytes)
            zos.closeEntry()

            // 2. META-INF/container.xml
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            val containerXml = "<?xml version=\"1.0\"?>\n" +
                    "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n" +
                    "   <rootfiles>\n" +
                    "      <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n" +
                    "   </rootfiles>\n" +
                    "</container>"
            zos.write(containerXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. OEBPS/style.css
            zos.putNextEntry(ZipEntry("OEBPS/style.css"))
            val css = "body { font-family: sans-serif; margin: 5%; line-height: 1.6; }\n" +
                    "h1, h2, h3 { text-align: center; margin-top: 1.5em; }\n" +
                    "p { text-indent: 1.2em; margin-bottom: 0.5em; text-align: justify; }\n" +
                    "blockquote { font-style: italic; margin: 1em 2em; }\n" +
                    "img { max-width: 100%; height: auto; display: block; margin: 1em auto; }\n"
            zos.write(css.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 4. OEBPS/chapter_X.xhtml
            for (idx in chaptersHtml.indices) {
                val fileName = "OEBPS/chapter_${idx + 1}.xhtml"
                zos.putNextEntry(ZipEntry(fileName))
                val xhtmlContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<!DOCTYPE html>\n" +
                        "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
                        "<head>\n" +
                        "  <title>" + escapeXml(chapterTitles[idx]) + "</title>\n" +
                        "  <link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\"/>\n" +
                        "</head>\n" +
                        "<body>\n" +
                        chaptersHtml[idx] + "\n" +
                        "</body>\n" +
                        "</html>"
                zos.write(xhtmlContent.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            // 5. Images
            for ((id, bytes) in binaries) {
                zos.putNextEntry(ZipEntry("OEBPS/images/$id"))
                zos.write(bytes)
                zos.closeEntry()
            }

            // 6. OEBPS/content.opf
            val manifestItems = StringBuilder()
            val spineItems = StringBuilder()
            manifestItems.append("<item id=\"style\" href=\"style.css\" media-type=\"text/css\"/>\n")

            for (idx in chaptersHtml.indices) {
                manifestItems.append("<item id=\"chap_$idx\" href=\"chapter_${idx + 1}.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
                spineItems.append("<itemref idref=\"chap_$idx\"/>\n")
            }

            for ((id, _) in binaries) {
                manifestItems.append("<item id=\"img_$id\" href=\"images/$id\" media-type=\"image/jpeg\"/>\n")
            }

            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            val opfContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<package xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"BookId\" version=\"2.0\">\n" +
                    "  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n" +
                    "    <dc:title>" + escapeXml(bookTitle) + "</dc:title>\n" +
                    "    <dc:creator>" + escapeXml(bookAuthor) + "</dc:creator>\n" +
                    "    <dc:language>ru</dc:language>\n" +
                    "  </metadata>\n" +
                    "  <manifest>\n" +
                    manifestItems.toString() +
                    "  </manifest>\n" +
                    "  <spine>\n" +
                    spineItems.toString() +
                    "  </spine>\n" +
                    "</package>"
            zos.write(opfContent.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        if (tempZipFile.exists()) {
            if (outputFile.exists()) outputFile.delete()
            tempZipFile.renameTo(outputFile)
        }
    }

    private fun getXmlInputStream(inputFile: File): InputStream? {
        val nameLower = inputFile.name.lowercase()
        return if (nameLower.endsWith(".fb2.zip") || nameLower.endsWith(".zip")) {
            val zis = ZipInputStream(FileInputStream(inputFile))
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.lowercase().endsWith(".fb2")) {
                    return zis
                }
                entry = zis.nextEntry
            }
            null
        } else {
            FileInputStream(inputFile)
        }
    }

    private fun parseElementToHtml(node: Node): String {
        val sb = StringBuilder()
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            when (child.nodeType) {
                Node.TEXT_NODE -> sb.append(escapeXml(child.textContent))
                Node.ELEMENT_NODE -> {
                    val elem = child as Element
                    when (elem.tagName.lowercase()) {
                        "p" -> sb.append("<p>").append(parseElementToHtml(elem)).append("</p>\n")
                        "title" -> sb.append("<h2>").append(parseElementToHtml(elem)).append("</h2>\n")
                        "subtitle" -> sb.append("<h3>").append(parseElementToHtml(elem)).append("</h3>\n")
                        "empty-line" -> sb.append("<br/>\n")
                        "emphasis" -> sb.append("<em>").append(parseElementToHtml(elem)).append("</em>")
                        "strong" -> sb.append("<strong>").append(parseElementToHtml(elem)).append("</strong>")
                        "cite" -> sb.append("<blockquote>").append(parseElementToHtml(elem)).append("</blockquote>\n")
                        "v" -> sb.append("<p class=\"verse\">").append(parseElementToHtml(elem)).append("</p>\n")
                        "image" -> {
                            var href = elem.getAttribute("l:href").ifEmpty { elem.getAttribute("href") }
                            href = href.replace("#", "")
                            if (href.isNotEmpty()) {
                                sb.append("<img src=\"images/$href\" alt=\"image\"/>\n")
                            }
                        }
                        "a" -> {
                            val href = elem.getAttribute("l:href").ifEmpty { elem.getAttribute("href") }
                            sb.append("<a href=\"$href\">").append(parseElementToHtml(elem)).append("</a>")
                        }
                        else -> sb.append(parseElementToHtml(elem))
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
