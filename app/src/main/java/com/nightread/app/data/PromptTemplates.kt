package com.nightread.app.data

object PromptTemplates {

    const val SYSTEM_PROMPT = """Ты — литературовед-аналитик. Твоя задача — давать интерпретацию, а не пересказ.

Правила:
1. Не пересказывай сюжет.
2. Не цитируй отрывки без анализа.
3. Объединяй все падежи одного имени в одного персонажа.
4. Если в отрывках нет информации — скажи об этом честно.
5. Отвечай на русском языке.
6. Начинай сразу с ответа, без вступлений.

<|reasoning|>"""

    data class GenerationSettings(
        val temperature: Float,
        val maxTokens: Int
    )

    val SETTINGS_SUMMARY = GenerationSettings(temperature = 0.3f, maxTokens = 1024)
    val SETTINGS_WHO_IS = GenerationSettings(temperature = 0.5f, maxTokens = 512)
    val SETTINGS_CHARACTER_ANALYSIS = GenerationSettings(temperature = 0.7f, maxTokens = 4096)
    val SETTINGS_EXPLAIN_WORD = GenerationSettings(temperature = 0.3f, maxTokens = 512)
    val SETTINGS_COMPARE = GenerationSettings(temperature = 0.7f, maxTokens = 4096)
    val SETTINGS_CHARACTER_ARC = GenerationSettings(temperature = 0.7f, maxTokens = 4096)
    val SETTINGS_ALL_CHARACTERS = GenerationSettings(temperature = 0.7f, maxTokens = 4096)

    // 1. Краткое содержание
    fun bookSummary(text: String): String =
        """На основе этих отрывков из книги составь краткое содержание в 3–5 предложениях. Укажи основную тему, главного героя, ключевой конфликт и развязку.

Отрывки из книги (контекст для анализа):
$text

Начинай сразу с содержания. Не пиши "Краткое содержание:" или "Ответ:".
<|reasoning|>"""

    // 2. Кто это? (краткая справка о персонаже)
    fun whoIsCharacter(characterName: String, text: String): String =
        """Кто такой $characterName в этой книге? Дай краткую справку: роль в сюжете, основные черты характера, связь с другими.

Отрывки из книги (контекст для анализа):
$text

Начинай сразу с ответа. Не пиши "Ответ:" или "Справка:".
<|reasoning|>"""

    // 3. Глубокий анализ персонажа
    fun characterAnalysis(characterName: String, bookTitle: String, text: String): String =
        """Проанализируй персонажа $characterName из книги ${if (bookTitle.isNotBlank()) bookTitle else "книги"}.

Отрывки из книги (контекст для анализа):
$text

Проанализируй по пунктам:

1. МОТИВАЦИЯ И ВНУТРЕННИЕ ПРОТИВОРЕЧИЯ
   - Что движет персонажем? Чего он хочет?
   - В чём его внутренний конфликт?

2. ДИНАМИКА ПО ХОДУ СЮЖЕТА
   - Как меняется персонаж в этих отрывках?
   - Какие события влияют на его трансформацию?

3. ВНУТРЕННИЕ И ВНЕШНИЕ КОНФЛИКТЫ
   - С чем борется внутри себя?
   - С кем или с чем борется во внешнем мире?

4. ВЛИЯНИЕ НА ДРУГИХ ПЕРСОНАЖЕЙ
   - Как его действия влияют на окружающих?

5. АВТОРСКАЯ ИДЕЯ
   - Какую мысль автор вкладывает в этого героя?

Важно: Используй отрывки для подтверждения тезисов, но не ограничивайся ими. Дай интерпретацию. Начинай сразу с пункта 1.

<|reasoning|>"""

    // 4. Пояснение слова
    fun explainTerm(wordOrPhrase: String, text: String): String =
        """Объясни значение слова или выражения "$wordOrPhrase" в контексте этой книги.

Отрывки из книги (контекст для анализа):
$text

Дай:
1. Прямое значение
2. Смысл в данном контексте
3. Если устаревшее или диалектное — поясни

Начинай сразу с объяснения. Не пиши "Ответ:" или "Пояснение:".
<|reasoning|>"""

    // 5. Сравнительный анализ двух персонажей
    fun compareCharacters(name1: String, name2: String, bookTitle: String, text: String): String =
        """Сравни персонажей $name1 и $name2 из книги ${if (bookTitle.isNotBlank()) bookTitle else "книги"}.

Отрывки из книги (контекст для анализа):
$text

Сравни по пунктам:
1. Общие черты
2. Различия в мотивации
3. Различия в динамике
4. Как их конфликт или союз влияет на сюжет
5. Авторская идея в каждом

Начинай сразу со сравнения. Не пиши "Сравнительный анализ:" или "Ответ:".
<|reasoning|>"""

    // 6. Анализ сюжетной арки персонажа
    fun characterArcAnalysis(characterName: String, bookTitle: String, text: String): String =
        """Проанализируй сюжетную арку персонажа $characterName из книги ${if (bookTitle.isNotBlank()) bookTitle else "книги"}.

Отрывки из книги (контекст для анализа):
$text

Опиши:
1. Экспозицию — появление в сюжете
2. Развитие конфликта
3. Кульминацию
4. Развязку

Покажи, как каждый этап влияет на персонажа. Не пересказывай сюжет — анализируй. Начинай сразу с экспозиции. Не пиши "Анализ арки:" или "Ответ:".
<|reasoning|>"""

    // 7. Анализ всех персонажей книги
    fun analyzeAllCharacters(bookTitle: String, text: String): String =
        """Ты — литературовед-аналитик. Проанализируй всех персонажей книги ${if (bookTitle.isNotBlank()) bookTitle else "книги"}.

Отрывки из книги (контекст для анализа):
$text

Объединяй все падежи одного имени в одного персонажа.

Для каждого персонажа дай:
- Мотивацию и внутренние противоречия
- Динамику по ходу сюжета
- Конфликты
- Влияние на других
- Авторскую идею

Не используй шаблонные фразы. Не пересказывай сюжет. Начинай сразу с первого персонажа. Не пиши "Анализ персонажей:" или "Ответ:".
<|reasoning|>"""

    fun buildFullPrompt(taskPrompt: String): String {
        return "<system>\n$SYSTEM_PROMPT\n</system>\n\n<user>\n$taskPrompt\n</user>\n<assistant>\n"
    }
}


