package com.nightread.app.service

import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import java.io.StringWriter
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

object Fb2ToHtmlConverterAdvanced {

    fun convert(fb2Xml: String): String {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val inputStream = ByteArrayInputStream(fb2Xml.toByteArray(Charsets.UTF_8))
            val document = builder.parse(inputStream)

            // Normalize structure
            document.documentElement.normalize()

            // We need to extract the <body> and convert it to HTML
            val bodies = document.getElementsByTagName("body")
            val bodyNode = if (bodies.length > 0) bodies.item(0) else document.documentElement

            // Convert XML DOM to HTML structure
            val htmlContent = convertNodeToHtml(bodyNode)

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
                    $htmlContent
                </body>
                </html>
            """.trimIndent()
        } catch (e: Exception) {
            e.printStackTrace()
            return "<html><body>Error rendering FB2: ${e.message}</body></html>"
        }
    }

    private fun convertNodeToHtml(node: Node): String {
        val html = StringBuilder()
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            when (child.nodeType) {
                Node.ELEMENT_NODE -> {
                    when (child.nodeName.lowercase()) {
                        "p" -> html.append("<p>${convertNodeToHtml(child)}</p>")
                        "title", "section" -> html.append("<div>${convertNodeToHtml(child)}</div>")
                        "subtitle" -> html.append("<h3>${convertNodeToHtml(child)}</h3>")
                        "strong", "b" -> html.append("<strong>${convertNodeToHtml(child)}</strong>")
                        "emphasis", "i" -> html.append("<em>${convertNodeToHtml(child)}</em>")
                        "empty-line" -> html.append("<br/>")
                        else -> html.append(convertNodeToHtml(child))
                    }
                }
                Node.TEXT_NODE -> {
                    html.append(child.textContent)
                }
            }
        }
        return html.toString()
    }
}
