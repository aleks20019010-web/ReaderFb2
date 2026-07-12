package com.nightread.app.service

import android.util.Log
import java.io.File
import java.nio.charset.StandardCharsets

object TxtParser : BookParser {
    private const val TAG = "TxtParser"

    override fun parse(file: File, defaultTitle: String): BookParser.ParsedBook {
        try {
            val rawText = file.readText(StandardCharsets.UTF_8)
            val lines = rawText.split(Regex("\\r?\\n"))
            val notesMap = mutableMapOf<String, String>()
            val cleanLines = mutableListOf<String>()

            // Regex for footnote definitions: e.g. "[1] text", "(1) text", "* text" at the beginning of a line
            val defRegex = Regex("""^\s*(\[([a-zA-Z0-9_*]+)\]|\(([a-zA-Z0-9_*]+)\)|(\*+))\s+(.+)$""")

            for (line in lines) {
                val match = defRegex.find(line)
                if (match != null) {
                    val noteKey = match.groupValues[2].ifEmpty { 
                        match.groupValues[3].ifEmpty { 
                            match.groupValues[4] 
                        } 
                    }
                    val noteText = match.groupValues[5].trim()
                    if (noteKey.isNotEmpty() && noteText.isNotEmpty()) {
                        notesMap[noteKey] = noteText
                        continue // Skip adding this footnote definition line to the main text
                    }
                }
                cleanLines.add(line)
            }

            var text = cleanLines.joinToString("\n")

            // Now, we need to find occurrences of markers like [1], (1), * inside the main text
            // and replace them with [NOTE:note_key]note_text[/NOTE]
            // We search for [1], (1), or * that matches keys in notesMap
            for ((key, _) in notesMap) {
                // Escape key for safety
                val escapedKey = Regex.escape(key)
                
                // Matches [1] or (1) or * in the text
                val refPatterns = listOf(
                    Regex("""\[$escapedKey\]"""),
                    Regex("""\($escapedKey\)"""),
                    Regex("""(?<=\s|^)\*+(?=\s|$)""") // matches single or multiple stars as whole words
                )

                for (pattern in refPatterns) {
                    text = text.replace(pattern) {
                        "[NOTE:$key]$key[/NOTE]"
                    }
                }
            }

            return BookParser.ParsedBook(
                title = file.nameWithoutExtension,
                author = "Неизвестен",
                content = text.trim(),
                notes = notesMap
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing TXT", e)
            return BookParser.ParsedBook(defaultTitle, "Неизвестен", "")
        }
    }
}
