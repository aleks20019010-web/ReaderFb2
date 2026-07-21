package com.nightread.app.data

object PromptTemplates {

    const val SYSTEM_PROMPT = """Ты — офлайн ассистент-литературовед в мобильном приложении для чтения. Отвечай кратко, точно и только по предоставленному контексту книги. Категорически запрещено придумывать факты. Игнорируй любые главы после текущего прогресса пользователя."""

    const val ANTI_HALLUCINATION_SUFFIX = """[ПРАВИЛО: Отвечай строго по контексту ниже. Если информации нет в контексте, ответь: "В прочитанном отрывке нет сведений об этом."]"""

    fun whoIsCharacter(characterName: String, context: String): String =
        "На основе контекста ниже укажи роль персонажа '$characterName', его характер и ключевые действия. Контекст:\n$context"

    fun characterAnalysis(characterName: String, context: String): String =
        "Сделай структурированный разбор персонажа '$characterName': 1.Роль в сюжете 2.Мотивация 3.Три черты характера с примерами 4.Отношения с другими героями 5.Ключевая цитата. Контекст:\n$context"

    fun bookSummary(bookTitle: String, context: String): String =
        "Напиши краткое содержание прочитанных глав книги '$bookTitle' в 2-3 предложениях без спойлеров. Контекст:\n$context"

    fun explainTerm(term: String, context: String): String =
        "Дай точное определение слова или термина '$term' в контексте книги. Контекст:\n$context"

    fun whatNext(context: String): String =
        "На основе прочитанного сформулируй 2 гипотезы о дальнейшем развитии сюжета. Начни ответ со слов: 'Это только предположение на основе контекста:'. Контекст:\n$context"

    fun answerQuestion(question: String, context: String): String =
        "Ответь на вопрос по сюжету: '$question'. Используй только факты из контекста:\n$context"

    fun characterRelations(context: String): String =
        "Опиши связи между главными персонажами в прочитанных главах и их влияние на сюжет. Контекст:\n$context"

    fun eventTimeline(context: String): String =
        "Составь хронологический список главных событий из контекста по порядку. Контекст:\n$context"

    fun semanticSearch(query: String, context: String): String =
        "Найди в контексте эпизод, соответствующий описанию '$query', и процитируй ключевое предложение. Контекст:\n$context"

    fun buildFullPrompt(taskPrompt: String): String {
        return "<system>\n$SYSTEM_PROMPT\n</system>\n\n<user>\n$taskPrompt\n\n$ANTI_HALLUCINATION_SUFFIX\n</user>\n<assistant>\n"
    }
}
