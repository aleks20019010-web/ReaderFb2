package com.nightread.app.data

import android.content.Context
import com.nightread.app.service.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

object BookPreloader {

    fun preload(context: Context, sha1: String, filePath: String?) {
        if (filePath.isNullOrEmpty() || sha1.isEmpty()) return
        val appContext = context.applicationContext
        val cacheHtmlFile = File(appContext.cacheDir, "$sha1.html")
        val contentCacheFile = File(appContext.cacheDir, "$sha1.content")

        if (cacheHtmlFile.exists() && contentCacheFile.exists()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = File(filePath)
                if (!file.exists()) return@launch

                val theme = SettingsManager.getReadingTheme(appContext)
                val fontSize = SettingsManager.getFontSize(appContext)
                val lineSpacing = SettingsManager.getLineSpacing(appContext)
                val fontFamily = SettingsManager.getFontFamily(appContext)
                val fontWeight = SettingsManager.getFontWeightAsInt(appContext)

                val rawContent = if (contentCacheFile.exists()) {
                    contentCacheFile.readText()
                } else {
                    val parsed = when (file.extension.lowercase()) {
                        "fb3" -> Fb3Parser.parse(file, file.nameWithoutExtension).content
                        "epub" -> EpubParser.parse(file, file.nameWithoutExtension).content
                        "mobi", "azw", "azw3" -> MobiParser.parse(file, file.nameWithoutExtension).content
                        else -> file.readText()
                    }
                    try { contentCacheFile.writeText(parsed) } catch (e: Exception) {}
                    parsed
                }

                if (!cacheHtmlFile.exists()) {
                    val paragraphIndent = SettingsManager.getParagraphIndent(appContext)
                    val converted = if (file.extension.lowercase() == "fb2" || file.name.endsWith(".fb2.zip", true) || file.name.endsWith(".zip", true)) {
                        Fb2ToHtmlConverterAdvanced.convert(
                            fb2Xml = rawContent,
                            theme = theme,
                            fontSize = fontSize,
                            lineSpacing = lineSpacing,
                            fontFamily = fontFamily,
                            fontWeight = fontWeight,
                            fontAlignment = "JUSTIFY",
                            pageMargins = true,
                            paddingTop = 15,
                            paddingBottom = 16,
                            paddingLeft = 8,
                            paddingRight = 8,
                            paragraphIndent = paragraphIndent
                        )
                    } else {
                        EpubToHtmlConverter.convert(
                            xhtmlContent = rawContent,
                            theme = theme,
                            fontSize = fontSize,
                            lineSpacing = lineSpacing,
                            fontFamily = fontFamily,
                            fontWeight = fontWeight,
                            fontAlignment = "JUSTIFY",
                            pageMargins = true,
                            paddingTop = 15,
                            paddingBottom = 16,
                            paddingLeft = 8,
                            paddingRight = 8,
                            paragraphIndent = paragraphIndent
                        )
                    }
                    try { cacheHtmlFile.writeText(converted) } catch (e: Exception) {}
                }
            } catch (e: Exception) {
                // Ignore background preloading exceptions
            }
        }
    }
}
