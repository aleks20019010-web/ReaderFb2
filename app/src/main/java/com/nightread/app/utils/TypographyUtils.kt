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

        // 5. Обеспечиваем \u000C перед [CHAPTER] если его нет
        result = result.replace(Regex("(?<!\u000C)\\[CHAPTER\\]"), "\u000C[CHAPTER]")

        return result
    }
}
