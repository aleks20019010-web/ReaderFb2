package com.nightread.app.service

object TitleHelper {
    /**
     * Transliterates a Latin-encoded Russian title (or filename) back into Russian Cyrillic.
     */
    fun transliterate(input: String): String {
        if (input.isBlank()) return ""
        
        val mappings = listOf(
            "shch" to "щ", "Shch" to "Щ", "SHCH" to "Щ",
            "kh" to "х", "Kh" to "Х", "KH" to "Х",
            "ts" to "ц", "Ts" to "Ц", "TS" to "Ц",
            "ch" to "ч", "Ch" to "Ч", "CH" to "Ч",
            "sh" to "ш", "Sh" to "Ш", "SH" to "Ш",
            "zh" to "ж", "Zh" to "Ж", "ZH" to "Ж",
            "yu" to "ю", "Yu" to "Ю", "YU" to "Ю",
            "ya" to "я", "Ya" to "Я", "YA" to "Я",
            "yo" to "ё", "Yo" to "Ё", "YO" to "Ё",
            "a" to "а", "b" to "б", "v" to "в", "g" to "г", "d" to "д",
            "e" to "е", "z" to "з", "i" to "и", "y" to "й", "k" to "к",
            "l" to "л", "m" to "м", "n" to "н", "o" to "о", "p" to "п",
            "r" to "р", "s" to "с", "t" to "т", "u" to "у", "f" to "ф",
            "e" to "э", "A" to "А", "B" to "Б", "V" to "В", "G" to "Г",
            "D" to "Д", "E" to "Е", "Z" to "З", "I" to "И", "Y" to "Й",
            "K" to "К", "L" to "Л", "M" to "М", "N" to "Н", "O" to "О",
            "P" to "П", "R" to "Р", "S" to "С", "T" to "Т", "U" to "У",
            "F" to "Ф", "E" to "Э"
        )
        
        var result = input
        for ((latin, cyrillic) in mappings) {
            result = result.replace(latin, cyrillic)
        }
        return result
    }
}
