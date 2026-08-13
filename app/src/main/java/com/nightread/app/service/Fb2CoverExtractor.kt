package com.nightread.app.service

import android.content.Context
import android.util.Base64
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

object Fb2CoverExtractor {
    fun extract(inputStream: InputStream, sha1: String, context: Context): String? {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

            var targetCoverId: String? = null
            var eventType = parser.eventType
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val name = parser.name?.lowercase()
                    if (name == "image") {
                        val href = parser.getAttributeValue(null, "l:href") ?: parser.getAttributeValue("http://www.w3.org/1999/xlink", "href")
                        if (href != null && href.startsWith("#") && targetCoverId == null) {
                            targetCoverId = href.removePrefix("#")
                        }
                    } else if (name == "binary") {
                        val id = parser.getAttributeValue(null, "id")
                        if (id != null) {
                            val text = parser.nextText()
                            if (id == targetCoverId || (targetCoverId == null && id.contains("cover", ignoreCase = true))) {
                                val bytes = Base64.decode(text.replace("\\s".toRegex(), ""), Base64.DEFAULT)
                                return NewCoverExtractor.saveCoverBytes(bytes, sha1, context)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Throwable) {
            Log.e("Fb2CoverExtractor", "Error extracting cover", e)
        }
        return null
    }
}
