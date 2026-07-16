package com.nightread.app.service

import java.util.regex.Pattern

object Fb2ToHtmlConverter {
    fun convert(fb2Xml: String): String {
        var html = fb2Xml

        // Minimalist FB2 to HTML conversion
        html = html.replace("<book-title>", "<h1>")
            .replace("</book-title>", "</h1>")
            .replace("<p>", "<p>")
            .replace("</p>", "</p>")
            .replace("<emphasis>", "<em>")
            .replace("</emphasis>", "</em>")
            .replace("<strong>", "<strong>")
            .replace("</strong>", "</strong>")
            .replace("<empty-line/>", "<br/>")

        // Wrap in a standard HTML5 structure with no margin CSS
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <style>
                    body { margin: 0; padding: 0; }
                    p { margin: 0; padding: 0; }
                </style>
            </head>
            <body>
                $html
            </body>
            </html>
        """.trimIndent()
    }
}
