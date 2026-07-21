package com.nightread.app.data

object PromptTemplates {

    const val SYSTEM_PROMPT = """Ты — литературовед-аналитик. Твоя задача — помогать читателю разбираться в тексте: давать краткие содержания, пояснять персонажей и слова, делать глубокий анализ героев. Отвечай ясно, структурированно, без воды.

<|reasoning|>"""

    fun whoIsCharacter(characterName: String, bookTitle: String = "", context: String = ""): String =
        "Кто такой $characterName${if (bookTitle.isNotBlank()) " в книге «$bookTitle»" else ""}? Кратко: роль в сюжете, основные черты характера, связь с другими персонажами.${if (context.isNotBlank()) "\n\nКонтекст из текста:\n$context" else ""}"

    fun characterAnalysis(characterName: String, bookTitle: String = "", context: String = ""): String =
        """Ты — литературовед-аналитик.

<|reasoning|>

Персонаж: $characterName
Книга: ${if (bookTitle.isNotBlank()) bookTitle else "Произведение"}
Контекст из текста:
${if (context.isNotBlank()) context else "Текущий прочитанный фрагмент"}

Проанализируй персонажа по пунктам:
1. Мотивация и внутренние противоречия
2. Динамика по ходу сюжета
3. Внутренние и внешние конфликты
4. Влияние на других персонажей
5. Какую идею автора воплощает этот персонаж

Дай развернутый анализ. Не пересказывай сюжет. Используй цитаты для подтверждения тезисов."""

    fun characterAnalysisFromQuotes(characterName: String, quotes: String): String =
        """Задача: Проанализируй персонажа $characterName на основе следующих цитат из текста.

Цитаты:
$quotes

На основе этих эпизодов дай анализ персонажа. Какие черты характера проявляются? Как эти цитаты отражают его роль в произведении? Какой внутренний конфликт они иллюстрируют?"""

    fun bookSummary(bookTitle: String, context: String = ""): String =
        "Дай краткое содержание книги ${if (bookTitle.isNotBlank()) "«$bookTitle»" else "произведения"} в 3–5 предложениях. Укажи основную тему, главного героя и ключевой конфликт.${if (context.isNotBlank()) "\n\nКонтекст из прочитанного:\n$context" else ""}"

    fun explainTerm(term: String, bookTitle: String = "", context: String = ""): String =
        """Объясни значение слова или выражения "$term"${if (bookTitle.isNotBlank()) " в контексте книги «$bookTitle»" else ""}. Дай:
1. Прямое значение
2. Смысл в данном контексте
3. Если это устаревшее или диалектное слово — поясни${if (context.isNotBlank()) "\n\nКонтекст:\n$context" else ""}"""

    fun whatNext(context: String): String =
        "На основе прочитанного текста сформулируй гипотезы о дальнейшем развитии сюжета. Отметь явно, что это лишь предположения на основе контекста:\n\nКонтекст:\n$context"

    fun answerQuestion(question: String, context: String): String =
        "Ответь на вопрос по сюжету: «$question». Используй только факты из прочитанного контекста (без спойлеров к будущим главам):\n\nКонтекст:\n$context"

    fun characterRelations(context: String): String =
        "Опиши связи между главными персонажами в прочитанных главах и их влияние на сюжет. Контекст:\n$context"

    fun eventTimeline(context: String): String =
        "Выстрой ключевые события из контекста в строгой хронологической последовательности по порядку их появления в сюжете. Контекст:\n$context"

    fun semanticSearch(query: String, context: String): String =
        "Найди в тексте фрагмент или эпизод, соответствующий описанию «$query», и процитируй соответствующий отрывок. Контекст:\n$context"

    fun buildFullPrompt(taskPrompt: String): String {
        return "<system>\n$SYSTEM_PROMPT\n</system>\n\n<user>\n$taskPrompt\n</user>\n<assistant>\n"
    }
}

