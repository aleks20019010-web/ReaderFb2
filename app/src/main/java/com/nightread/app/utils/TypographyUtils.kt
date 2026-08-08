package com.nightread.app.utils

object TypographyUtils {

    /**
     * Быстрая O(N) микротипографика без тяжёлых циклов и без тормозов на больших книгах.
     */
    fun applyMicroTypography(text: String): String {
        if (text.isEmpty()) return text

        var result = text

        // 1. Замена кавычек-"лапок" на ёлочки « »
        result = result.replace(Regex("""\"([^\"]*)\""""), "«$1»")

        // 2. Замена дефисов в прямой речи и между словами на длинное тире
        result = result.replace(Regex("""\s-\s"""), " — ")
        result = result.replace(Regex("""(?m)^-\s"""), "— ")

        // 3. Исправление троеточий
        result = result.replace(Regex("""\.\.\."""), "…")

        // 4. Очистка повторных пробелов (сохраняя переносы строк)
        result = result.replace(Regex("""[ \t]{2,}"""), " ")

        // 5. Очистка двойных и повторных переносов строк (делаем стандартный интервал между абзацами)
        result = result.replace(Regex("""(?<!\u000C)\n[ \t]*\n+"""), "\n")

        // 6. Преобразование [CHAPTER] в <CHAPTER>
        result = result.replace("[CHAPTER]", "<CHAPTER>").replace("[/CHAPTER]", "</CHAPTER>")

        // 7. Обеспечиваем \u000C перед маркерами глав, чтобы каждая новая глава начиналась с новой страницы
        result = result.replace(
            Regex("(?<!\u000C)(?i)(<CHAPTER>|<title[^>]*>|(?<=\n|^)(?:Глава|Chapter|ГЛАВА|CHAPTER|Часть|Part)\\b)"),
            "\u000C$1"
        )

        return result
    }
}
