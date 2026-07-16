package com.nightread.app.service

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import javax.xml.parsers.SAXParserFactory

object Fb2ToHtmlConverterAdvanced {

    fun convert(fb2Xml: String): String {
        try {
            val factory = SAXParserFactory.newInstance()
            val saxParser = factory.newSAXParser()
            val handler = Fb2SaxHandler()
            
            val inputStream = ByteArrayInputStream(fb2Xml.toByteArray(Charsets.UTF_8))
            saxParser.parse(inputStream, handler)

            return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                    <style>
                        body { margin: 0; padding: 0; overflow-x: hidden; font-family: sans-serif; }
                        p { margin: 0; padding: 0; text-align: justify; }
                        h1, h2, h3, h4, h5, h6 { margin: 0; padding: 0; font-weight: bold; }
                        h1 { font-size: 1.5em; }
                        h2 { font-size: 1.3em; }
                        h3 { font-size: 1.1em; }
                        strong { font-weight: bold; }
                        em { font-style: italic; }
                    </style>
                </head>
                <body>
                    ${handler.getHtml()}
                </body>
                </html>
            """.trimIndent()
        } catch (e: Exception) {
            e.printStackTrace()
            return "<html><body>Error rendering FB2: ${e.message}</body></html>"
        }
    }

    private class Fb2SaxHandler : DefaultHandler() {
        private val html = StringBuilder()
        
        fun getHtml(): String = html.toString()

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            when (qName?.lowercase()) {
                "p" -> html.append("<p>")
                "title", "section" -> html.append("<div>")
                "subtitle" -> html.append("<h3>")
                "strong", "b" -> html.append("<strong>")
                "emphasis", "i" -> html.append("<em>")
                "empty-line" -> html.append("<br/>")
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (qName?.lowercase()) {
                "p" -> html.append("</p>")
                "title", "section" -> html.append("</div>")
                "subtitle" -> html.append("</h3>")
                "strong", "b" -> html.append("</strong>")
                "emphasis", "i" -> html.append("</em>")
            }
        }

        override fun characters(ch: CharArray?, start: Int, length: Int) {
            if (ch == null) return
            for (i in start until start + length) {
                when (val c = ch[i]) {
                    '<' -> html.append("&lt;")
                    '>' -> html.append("&gt;")
                    '&' -> html.append("&amp;")
                    else -> html.append(c)
                }
            }
        }
    }
}
