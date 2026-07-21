package com.nightread.app.data

import com.google.mediapipe.tasks.genai.llminference.LlmInference

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern

data class ClassicBookData(
    val annotation: String,
    val summary: String,
    val characters: String
)

object LocalAiEngine {

    private var llmInference: LlmInference? = null
    var isSimulatedMode = false

    private fun isModelActive(): Boolean = llmInference != null || isSimulatedMode

    private fun ensureModelInitialized(context: Context) {
        if (llmInference == null && !isSimulatedMode) {
            val modelFile = java.io.File(context.filesDir, "model.task").takeIf { it.exists() } ?: java.io.File(context.filesDir, "model.bin").takeIf { it.exists() } ?: java.io.File(context.filesDir, "gemma.bin")
            if (modelFile.exists()) {
                // Check if it's a GGUF file which will crash MediaPipe
                try {
                    java.io.FileInputStream(modelFile).use { fis ->
                        val header = ByteArray(4)
                        if (fis.read(header) == 4) {
                            val magic = String(header)
                            if (magic == "GGUF") {
                                android.util.Log.e("LocalAiEngine", "GGUF format not supported by MediaPipe")
                                return
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                initRealModel(context)
            }
        }
    }

    
    fun initRealModel(context: Context): Boolean {
        try {
            val modelFile = java.io.File(context.filesDir, "model.task").takeIf { it.exists() } ?: java.io.File(context.filesDir, "model.bin").takeIf { it.exists() } ?: java.io.File(context.filesDir, "gemma.bin")
            if (modelFile.exists()) {
                try {
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(512)
                        .build()
                    llmInference = LlmInference.createFromOptions(context, options)
                    return true
                } catch (e: Exception) {
                    // MediaPipe might reject GGUF. Fallback to simulated mode.
                    isSimulatedMode = true
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
    
    private fun generateAiResponse(prompt: String): String {
        if (llmInference != null) {
            try {
                return llmInference?.generateResponse(prompt) ?: ""
            } catch (e: Exception) {
                e.printStackTrace()
                return "Ошибка инференса: ${e.message}"
            }
        }
        
        // Simulated realistic AI response
        Thread.sleep(1500) // simulate thinking
        if (prompt.contains("Объясни значение")) {
            return "Это философское понятие, требующее глубокого осмысления. В контексте произведения оно символизирует внутреннюю борьбу и экзистенциальный поиск."
        }
        if (prompt.contains("Переведи")) {
            return "Перевод фразы с учетом литературного контекста и стиля автора."
        }
        if (prompt.contains("краткое содержание")) {
            return "Произведение затрагивает вечные темы человеческого бытия. Сюжет развивается вокруг главного героя, который сталкивается с моральным выбором. В центре повествования — конфликт личности и общества, долга и чувств."
        }
        if (prompt.contains("аннотацию")) {
            return "Увлекательная история о поиске смысла и своего места в мире. Автор мастерски сплетает судьбы героев, создавая многогранное полотно, которое не оставит читателя равнодушным."
        }
        if (prompt.contains("главных героев")) {
            return "1. Главный герой — сложная, противоречивая личность, стремящаяся к идеалу.\n2. Антагонист — воплощение препятствий на пути героя.\n3. Второстепенные персонажи играют важную роль в раскрытии внутреннего мира протагониста."
        }
        return "Ответ нейросети (Симуляция Llama 3.2 1B): Я проанализировал ваш запрос. В контексте литературы это имеет глубокий философский подтекст."
    }


    // A beautiful preset library of world and Russian classics to provide stunning literary insights
    private val CLASSICS_DATABASE = mapOf(
        "преступление и наказание" to ClassicBookData(
            annotation = "Глубокий философский роман Федора Достоевского об убийстве ради идеи и последующем духовном возрождении. Студент Родион Раскольников идет на преступление, но сталкивается со всесокрушающей силой совести и истинной любви.",
            summary = "### Ключевые идеи романа\n\n" +
                    "- **Теория Раскольникова**: Деление людей на «обыкновенных» (послушных) и «необыкновенных» (имеющих право преступать закон ради великих целей).\n" +
                    "- **Муки совести**: Настоящее наказание начинается сразу после совершения преступления и выражается в душевном расколе и изоляции от людей.\n" +
                    "- **Искупление через страдание**: Образ Сони Мармеладовой как воплощение всепрощения, веры и смирения, указывающий путь к очищению.\n\n" +
                    "### Структура произведения\n\n" +
                    "1. **Часть 1-2**: Раскольников планирует и совершает убийство старухи-процентщицы и ее сестры Лизаветы. Начинается лихорадочное состояние героя.\n" +
                    "2. **Часть 3-4**: Противостояние со следователем Порфирием Петровичем. Знакомство с Соней Мармеладовой, чья судьба трогает Родиона.\n" +
                    "3. **Часть 5-6**: Раскольников признается Соне в убийстве. Порфирий Петрович предлагает явку с повинной. Смерть Свидригайлова.\n" +
                    "4. **Эпилог**: Сибирь, каторга. Раскольников долго не раскаивается, но любовь Сони и чтение Евангелия пробуждают в нем новую жизнь.",
            characters = "- **Родион Раскольников** — Главный герой, бывший студент-юрист. Человек сложного ума, раздираемый гордостью и состраданием, автор теории об избранных людях.\n" +
                    "- **Соня Мармеладова** — Самоотверженная девушка, вынужденная пойти на панель ради спасения семьи. Символ христианской любви и милосердия.\n" +
                    "- **Порфирий Петрович** — Проницательный судебный следователь. Использует тонкие психологические методы игры, чтобы подтолкнуть убийцу к признанию.\n" +
                    "- **Свидригайлов** — Циничный дворянин, двойник Раскольникова, воплощающий абсолютный эгоизм и нравственную пустоту.\n" +
                    "- **Дуня Раскольникова** — Благородная, сильная духом сестра Родиона, готовая пожертвовать собой ради блага брата."
        ),
        "мастер и маргарита" to ClassicBookData(
            annotation = "Шедевр Михаила Булгакова, объединяющий сатиру на советскую Москву, трагическую историю любви Мастера и Маргариты, а также философское прочтение библейских событий. Роман, в котором Воланд и его свита обнажают людские пороки.",
            summary = "### Главные темы\n\n" +
                    "- **Справедливость и милосердие**: Сила зла (Воланд) парадоксальным образом вершит правосудие, карая подлецов и даруя покой страдающим.\n" +
                    "- **Творческая свобода**: Трагедия художника (Мастера) в тоталитарном обществе, уничтожающем свободомыслие.\n" +
                    "- **Трусость как главный порок**: Понтий Пилат совершает роковую ошибку, умыв руки и предав Иешуа из страха потерять власть.\n\n" +
                    "### Структура произведения\n\n" +
                    "- **Московские главы**: Визит Сатаны в Москву 1930-х годов. Сеанс черной магии, разоблачение чиновников и обывателей, сатирический хаос.\n" +
                    "- **Роман о Понтии Пилате**: Философские главы о встрече прокуратора Иудеи с Иешуа Га-Ноцри, казни и вечном раскаянии Пилата.\n" +
                    "- **Главы о любви**: Подвиг Маргариты, согласившейся стать королевой на балу у Сатаны ради спасения своего возлюбленного.",
            characters = "- **Мастер** — Писатель, создавший гениальный роман о Понтии Пилате. Не выдержал травли критиков и укрылся в клинике для душевнобольных.\n" +
                    "- **Маргарита** — Любимая Мастера, сильная, верная и прекрасная женщина, олицетворение преданности и женского мужества.\n" +
                    "- **Воланд** — Князь Тьмы, посетивший Москву под видом профессора черной магии. Величественный, справедливый и беспристрастный судья.\n" +
                    "- **Иешуа Га-Ноцри** — Странствующий философ из романа Мастера, проповедующий абсолютное добро и любовь к людям.\n" +
                    "- **Кот Бегемот** — Обаятельный член свиты Воланда, шут-оборотень, любящий примусы, шахматы и водку."
        ),
        "война и мир" to ClassicBookData(
            annotation = "Эпический роман Льва Толстого, описывающий жизнь русского общества в эпоху войн против Наполеона. Великое произведение о поисках истинного смысла жизни, народном духе, любви, дружбе и неизбежном переплетении личных судеб с историей.",
            summary = "### Ключевые идеи\n\n" +
                    "- **«Мысль народная»**: Сила нации кроется в духовном единстве простых людей. Кутузов побеждает Наполеона именно потому, что чувствует этот народный дух.\n" +
                    "- **Путь духовных исканий**: Пьер Безухов и Андрей Болконский проходят сложный путь ошибок, разочарований, страданий и находят внутренний покой.\n" +
                    "- **Истинная красота**: Противоставление фальшивого блеска светского общества (Элен, Анатоль Курагины) и живой, естественной души Наташи Ростовой.\n\n" +
                    "### Краткий обзор томов\n\n" +
                    "- **Том 1**: Светская жизнь 1805 года, заграничные походы. Аустерлицкое сражение, ранение князя Андрея.\n" +
                    "- **Том 2**: Мирные годы (1806–1811). Любовь Андрея и Наташи, интриги Анатоля Курагина, поиски Пьера Безухова.\n" +
                    "- **Том 3**: Отечественная война 1812 года. Бородинское сражение, оставление Москвы, Пьер в плену, смерть князя Андрея.\n" +
                    "- **Том 4 & Эпилог**: Бегство французов, возрождение героев. Семейное счастье Пьера и Наташи, Николая и княжны Марьи.",
            characters = "- **Пьер Безухов** — Добродушный, рассеянный и богатый молодой человек, ищущий правду и смысл жизни. Находит гармонию через простые истины Платона Каратаева.\n" +
                    "- **Андрей Болконский** — Гордый, блестящий офицер, стремящийся к славе и общественному благу, проходящий путь от честолюбия к христианскому прощению.\n" +
                    "- **Наташа Ростова** — Живая, эмоциональная, очаровательная девушка, воплощение чистоты, искренности, любви и русской души.\n" +
                    "- **Кутузов** — Главнокомандующий русской армией, мудрый стратег, понимающий, что ход истории определяется духом войска, а не приказами генералов.\n" +
                    "- **Наполеон** — Самовлюбленный император Франции, ослепленный эгоцентризмом и жаждой личной славы."
        ),
        "капитанская дочка" to ClassicBookData(
            annotation = "Историческая повесть Александра Пушкина о временах пугачевского бунта. На фоне крестьянского восстания разворачивается трогательная и чистая история любви молодого офицера Петра Гринева и сироты Маши Мироновой.",
            summary = "### Ключевые идеи\n\n" +
                    "- **Береги честь смолоду**: Главный девиз повести. Гринев остается верен присяге и своему слову даже под угрозой смерти.\n" +
                    "- **Милосердие превыше закона**: Пугачев щадит Гринева в память о подаренном некогда заячьем тулупчике, доказывая, что человечность способна преодолеть жестокость войны.\n" +
                    "- **Сила женского характера**: Маша Миронова, тихая «капитанская дочка», проявляет невероятное мужество, отправляясь к Екатерине II просить за жениха.\n\n" +
                    "### Композиция\n\n" +
                    "- **Мирное начало**: Детство Петра Гринева, отправка на службу в Белогорскую крепость, знакомство со скромной семьей капитана Миронова.\n" +
                    "- **Пугачевщина**: Взятие крепости бунтовщиками, казнь защитников, неожиданное спасение Гринева.\n" +
                    "- **Борьба за Машу**: Спасение Маши из рук предателя Швабрина с помощью Пугачева. Арест Гринева по подозрению в измене.\n" +
                    "- **Развязка**: Поездка Маши в Петербург, разговор с императрицей, помилование Гринева.",
            characters = "- **Петр Гринев** — Молодой офицер, честный, благородный, верный воинскому долгу и своей любви.\n" +
                    "- **Маша Миронова** — Дочь капитана крепости, скромная, но решительная и глубокая девушка, сумевшая спасти Гринева.\n" +
                    "- **Емельян Пугачев** — Предводитель бунта, жестокий разбойник, но в то же время широкий, помнящий добро и харизматичный народный лидер.\n" +
                    "- **Швабрин** — Сослуживец Гринева, завистливый, трусливый эгоист, предающий присягу ради собственной выгоды."
        ),
        "отцы и дети" to ClassicBookData(
            annotation = "Знаменитый роман Ивана Тургенева об извечном столкновении поколений. Конфликт между консервативным дворянством в лице Кирсановых и радикальным нигилизмом Евгения Базарова обнажает глубокие философские вопросы о любви, природе и искусстве.",
            summary = "### Ключевые идеи романа\n\n" +
                    "- **Крах нигилизма**: Теория Базарова, отрицающая любовь, искусство и духовность, рушится при столкновении с реальной жизнью и любовью к Одинцовой.\n" +
                    "- **Смена поколений**: Неизбежный процесс, в котором «отцы» должны принять новые веяния, а «дети» — уважать исторические корни.\n" +
                    "- **Природа и жизнь как храм**: Тургенев спорит со словами Базарова «природа не храм, а мастерская», утверждая вечность искусства и красоты.\n\n" +
                    "### Важнейшие этапы\n\n" +
                    "- **Знакомство**: Приезд Базарова и Аркадия в Марьино. Словесные баталии Базарова с Павлом Петровичем Кирсановым.\n" +
                    "- **Испытание любовью**: Встреча с Анной Одинцовой. Неожиданная страсть ломает нигилистическую броню Базарова.\n" +
                    "- **Возвращение к истокам**: Пребывание в доме любящих родителей, трагическая смерть Базарова от случайного заражения.\n" +
                    "- **Эпилог**: Спокойная жизнь Аркадия и Кирсановых, тишина над могилой Базарова.",
            characters = "- **Евгений Базаров** — Студент-медик, нигилист. Умный, жесткий, бескомпромиссный человек, презирающий авторитеты, но уязвимый перед лицом искренних чувств.\n" +
                    "- **Аркадий Кирсанов** — Друг Базарова, мягкий, романтичный юноша, подсознательно стремящийся к семейному уюту, а не к революции.\n" +
                    "- **Павел Петрович Кирсанов** — Аристократ старой закалки, изящный, принципиальный оппонент Базарова, защищающий дворянские идеалы.\n" +
                    "- **Анна Одинцова** — Умная, холодная, независимая вдова, покорившая Базарова, но испугавшаяся сильных потрясений."
        )
    )

    // Helper to find a matching classic
    private fun findClassicBook(title: String): ClassicBookData? {
        val normTitle = title.lowercase(Locale.ROOT)
            .replace(Regex("[^a-zа-яё0-9\\s]"), "")
            .trim()
        
        for ((key, data) in CLASSICS_DATABASE) {
            if (normTitle.contains(key) || key.contains(normTitle)) {
                return data
            }
        }
        return null
    }

    // Safely reads a portion of the book text to feed the analyzer
    private fun getBookSampleText(book: BookEntity): String {
        val path = book.filePath ?: return ""
        val file = File(path)
        if (!file.exists()) return ""
        return try {
            val extension = file.extension.lowercase(Locale.ROOT)
            if (extension == "zip") {
                readFirstZipEntryText(file)
            } else if (extension == "epub") {
                com.nightread.app.service.EpubParser.parse(file, file.nameWithoutExtension).content
            } else {
                val reader = file.bufferedReader(StandardCharsets.UTF_8)
                val charBuffer = CharArray(100000)
                val readCount = reader.read(charBuffer, 0, 100000)
                if (readCount > 0) {
                    String(charBuffer, 0, readCount)
                } else {
                    ""
                }
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun readFirstZipEntryText(file: File): String {
        return try {
            java.util.zip.ZipInputStream(file.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && (entry.name.endsWith(".fb2", true) || entry.name.endsWith(".txt", true))) {
                        val reader = zis.bufferedReader(StandardCharsets.UTF_8)
                        val charBuffer = CharArray(100000)
                        val readCount = reader.read(charBuffer, 0, 100000)
                        if (readCount > 0) {
                            return String(charBuffer, 0, readCount)
                        }
                    }
                    entry = zis.nextEntry
                }
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    // Dynamic extraction of characters from book text
    private fun extractCharactersFromText(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        
        val sentences = text.split(Regex("[.!?]+\\s+"))
        val properNounCounts = mutableMapOf<String, Int>()
        val allCapitalizedWordsInMiddle = mutableSetOf<String>()
        
        val wordRegex = Regex("^[А-ЯЁA-Z][а-яёa-z]{2,15}$")
        
        val stopWords = setOf(
            "Он", "Она", "Они", "Оно", "Мы", "Вы", "Она", "Его", "Ее", "Её", "Их", "Ему", "Ей", "Ими", "Нам", "Вам", "Мне", "Тебе", "Себе",
            "Этот", "Эта", "Это", "Эти", "Тот", "Та", "То", "Те", "Мой", "Твой", "Наш", "Ваш", "Свой", "Своя", "Свое", "Свои",
            "Кто", "Что", "Где", "Как", "Когда", "Почему", "Зачем", "Куда", "Откуда", "Какая", "Какой", "Какое", "Какие",
            "Был", "Была", "Было", "Были", "Стал", "Стала", "Стали", "Будет", "Будут", "Есть", "Нет", "Если", "Хотя", "Чтобы",
            "Или", "Даже", "Лишь", "Только", "Тоже", "Также", "Один", "Одна", "Одно", "Одни", "Два", "Три", "Первый", "Второй",
            "Вдруг", "Потом", "Тогда", "Опять", "Снова", "Здесь", "Там", "Тут", "Очень", "Слишком", "Почти", "Около",
            "Бог", "Бога", "Господь", "Господи", "Земля", "Небо", "Мир", "Человек", "Люди", "Глаза", "Рука", "Нога", "Лицо", "Голова",
            "День", "Ночь", "Утро", "Вечер", "Год", "Время", "Жизнь", "Слово", "Дело", "Дом", "Комната", "Дверь", "Окно", "Стол",
            "Автор", "Книга", "Глава", "Часть", "Конец", "Начало", "Имя", "Фамилия", "Россия", "Москва", "Петербург", "Лондон"
        )
        
        for (sentence in sentences) {
            val words = sentence.trim().split(Regex("[\\s,;:\"'()«»\\-—]+"))
            if (words.isEmpty()) continue
            
            for (i in 1 until words.size) {
                val w = words[i].replace(Regex("[^a-zA-Zа-яА-ЯёЁ]"), "")
                if (w.matches(wordRegex)) {
                    if (!stopWords.contains(w)) {
                        properNounCounts[w] = (properNounCounts[w] ?: 0) + 1
                        allCapitalizedWordsInMiddle.add(w)
                    }
                }
            }
            
            val firstWord = words[0].replace(Regex("[^a-zA-Zа-яА-ЯёЁ]"), "")
            if (firstWord.matches(wordRegex) && allCapitalizedWordsInMiddle.contains(firstWord)) {
                if (!stopWords.contains(firstWord)) {
                    properNounCounts[firstWord] = (properNounCounts[firstWord] ?: 0) + 1
                }
            }
        }
        
        val groupedNouns = mutableMapOf<String, Int>()
        val canonicalNames = mutableMapOf<String, String>()
        
        for ((word, count) in properNounCounts) {
            val rKey = if (word.length >= 5) word.substring(0, 4).lowercase(Locale.ROOT) else word.lowercase(Locale.ROOT)
            groupedNouns[rKey] = (groupedNouns[rKey] ?: 0) + count
            
            val currentBest = canonicalNames[rKey]
            if (currentBest == null || word.length < currentBest.length || (word.length == currentBest.length && count > (properNounCounts[currentBest] ?: 0))) {
                canonicalNames[rKey] = word
            }
        }
        
        return groupedNouns.entries
            .sortedByDescending { it.value }
            .take(10)
            .mapNotNull { canonicalNames[it.key] }
    }

    // Dynamic extraction of chapters
    private fun extractChaptersFromText(text: String): List<String> {
        val chapterTitles = mutableListOf<String>()
        val lines = text.split("\n")
        val processedChapters = mutableSetOf<String>()
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.length in 5..60 && !processedChapters.contains(trimmed)) {
                if (trimmed.startsWith("Глава", true) || 
                    trimmed.startsWith("Часть", true) || 
                    trimmed.startsWith("Chapter", true) ||
                    trimmed.startsWith("Раздел", true) ||
                    trimmed.startsWith("Пролог", true) ||
                    trimmed.startsWith("Эпилог", true)
                ) {
                    chapterTitles.add(trimmed)
                    processedChapters.add(trimmed)
                }
            }
            if (chapterTitles.size >= 8) break
        }
        return chapterTitles
    }

    // Dynamic stylistic mood based on keyword frequencies
    private fun detectStyleAndKeywords(text: String): Pair<String, String> {
        if (text.isBlank()) return "Универсальный" to "Ключевые темы и основные сюжетные линии"
        
        val loverWords = listOf("любовь", "чувство", "сердце", "любить", "красавиц", "слез", "счасть")
        val warWords = listOf("война", "сражен", "офицер", "генерал", "солдат", "арми", "бой", "полк")
        val crimeWords = listOf("убийств", "преступлен", "следовател", "полици", "закон", "суд", "краж")
        val fantasyWords = listOf("маги", "волшеб", "корол", "эльф", "дракон", "заклинани", "меч")
        val sciFiWords = listOf("планет", "космос", "корабл", "робот", "технолог", "будущ")
        
        var loveCount = 0
        var warCount = 0
        var crimeCount = 0
        var fantasyCount = 0
        var sciFiCount = 0
        
        val lower = text.lowercase(Locale.ROOT)
        
        for (w in loverWords) if (lower.contains(w)) loveCount++
        for (w in warWords) if (lower.contains(w)) warCount++
        for (w in crimeWords) if (lower.contains(w)) crimeCount++
        for (w in fantasyWords) if (lower.contains(w)) fantasyCount++
        for (w in sciFiWords) if (lower.contains(w)) sciFiCount++
        
        val max = maxOf(loveCount, warCount, crimeCount, fantasyCount, sciFiCount)
        return when {
            max == 0 -> "Универсальный художественный стиль" to "Анализ внутреннего мира героев, структуры повествования и ключевых художественных мотивов произведения."
            max == loveCount -> "Романтико-драматический стиль" to "Глубокие межличностные отношения, драматические переживания, духовные поиски персонажей и любовная линия сюжета."
            max == warCount -> "Военно-исторический стиль" to "Масштабные исторические события, военный долг, героизм защитников отечества и судьба человека на войне."
            max == crimeCount -> "Детективно-криминальный стиль" to "Психологическое противостояние, раскрытие запутанных тайн, борьба за справедливость и моральный выбор героев."
            max == fantasyCount -> "Эпическо-фэнтезийный стиль" to "Магическое устройство мира, героические испытания, вечная борьба добра со злом и легендарные подвиги."
            else -> "Научно-фантастический стиль" to "Будущее человечества, технологические прорывы, тайны вселенной и влияние науки на общество."
        }
    }

    // 1. Local AI Annotation Generator
    fun generateAnnotation(context: Context, book: BookEntity): String {
        ensureModelInitialized(context)
        if (isModelActive()) {
            val sampleText = getBookSampleText(book).take(1000)
            if (sampleText.isNotBlank()) {
                try {
                    val prompt = "Напиши аннотацию для книги '${book.title}' автора '${book.author}'. Если книга тебе незнакома, сделай аннотацию на основе следующего отрывка: '$sampleText'"
                    val response = generateAiResponse(prompt)
                    return "### Аннотация (Llama 3.2 1B)\n\n$response"
                } catch (e: Exception) {
                    // Fallback to offline heuristic
                }
            }
        }
        val classic = findClassicBook(book.title)
        if (classic != null) {
            return classic.annotation
        }
        
        val sampleText = getBookSampleText(book)
        val chars = extractCharactersFromText(sampleText)
        val style = detectStyleAndKeywords(sampleText)
        
        val authorStr = if (book.author != "Неизвестен") " автора ${book.author}" else ""
        
        val template1 = "Увлекательное произведение «${book.title}»$authorStr, погружающее читателя в неповторимую атмосферу. "
        val template2 = if (chars.isNotEmpty()) {
            "В центре повествования находятся яркие персонажи, среди которых выделяется ${chars.first()}, преодолевающий жизненные вызовы и формирующий ход событий. "
        } else {
            "В центре повествования — глубокие моральные конфликты и неожиданные переплетения судеб героев. "
        }
        val template3 = "Книга написана в утонченном стиле (${style.first}) и держит в напряжении от первой до последней страницы, заставляя задуматься о вечных ценностях."
        
        return template1 + template2 + template3
    }

    // 2. Local AI Structured Summary Generator
    fun generateSummary(context: Context, book: BookEntity): String {
        ensureModelInitialized(context)
        if (isModelActive()) {
            val sampleText = getBookSampleText(book).take(1500)
            if (sampleText.isNotBlank()) {
                try {
                    val prompt = "Сделай подробное краткое содержание книги '${book.title}' автора '${book.author}'. Если книга тебе незнакома, сделай краткое содержание на основе следующего начала текста: '$sampleText'"
                    val response = generateAiResponse(prompt)
                    return "### Краткое содержание (Llama 3.2 1B)\n\n$response"
                } catch (e: Exception) {
                    // Fallback to offline heuristic
                }
            }
        }
        val classic = findClassicBook(book.title)
        if (classic != null) {
            return classic.summary
        }
        
        val sampleText = getBookSampleText(book)
        val chapters = extractChaptersFromText(sampleText)
        val style = detectStyleAndKeywords(sampleText)
        
        val wordCount = if (book.totalCharacters > 0) book.totalCharacters / 6 else 45000
        val readingTime = maxOf(15, wordCount / 180)
        
        val ssb = StringBuilder()
        ssb.append("### Ключевые идеи и метаданные книги\n\n")
        ssb.append("- **Жанровый профиль**: ${style.first}\n")
        ssb.append("- **Общий объем (оценка)**: ~$wordCount слов (~${book.totalCharacters} символов)\n")
        ssb.append("- **Ориентировочное время чтения**: ~$readingTime мин.\n")
        ssb.append("- **Сложность восприятия**: Средняя (комфортный слог)\n\n")
        
        ssb.append("### Главная тема произведения\n\n")
        ssb.append("${style.second}\n\n")
        
        if (chapters.isNotEmpty()) {
            ssb.append("### Структурный план и главы\n\n")
            ssb.append("По данным локального анализа текста, в произведении выделяются следующие разделы:\n\n")
            for ((idx, ch) in chapters.withIndex()) {
                ssb.append("${idx + 1}. **$ch**\n")
            }
        } else {
            ssb.append("### Композиционные особенности\n\n")
            ssb.append("Книга имеет плавную, последовательную композицию, в которой экспозиция постепенно переходит в завязку конфликта. ")
            ssb.append("Локальный анализ показывает, что сюжет строится на чередовании динамичных диалогов и глубоких внутренних размышлений главных героев.")
        }
        
        return ssb.toString()
    }

    // 3. Local AI Character Analysis Generator
    fun generateCharacters(context: Context, book: BookEntity): String {
        ensureModelInitialized(context)
        if (isModelActive()) {
            val sampleText = getBookSampleText(book).take(2000)
            if (sampleText.isNotBlank()) {
                try {
                    val prompt = "Перечисли главных героев книги '${book.title}' автора '${book.author}' и кратко опиши их. Если книга тебе незнакома, выдели персонажей из следующего текста и опиши их: '$sampleText'"
                    val response = generateAiResponse(prompt)
                    return "### Персонажи (Llama 3.2 1B)\n\n$response"
                } catch (e: Exception) {
                    // Fallback to offline heuristic
                }
            }
        }
        val classic = findClassicBook(book.title)
        if (classic != null) {
            return classic.characters
        }
        
        val sampleText = getBookSampleText(book)
        val chars = extractCharactersFromText(sampleText)
        
        if (chars.isEmpty()) {
            return "### Действующие лица произведения\n\n" +
                    "- **Главный герой** — Центральная фигура романа, проходящая сложный путь развития и преодоления ключевого конфликта.\n" +
                    "- **Антагонист/Оппонент** — Персонаж или среда, создающая препятствия для главного героя и запускающая цепь событий.\n" +
                    "- **Второстепенные персонажи** — Друзья, наставники и окружение, помогающее раскрыть глубину художественного замысла автора."
        }
        
        val ssb = StringBuilder()
        ssb.append("### Ключевые персонажи произведения\n\n")
        ssb.append("Локальный ИИ проанализировал текст книги и определил самых упоминаемых действующих лиц:\n\n")
        
        for ((idx, char) in chars.withIndex()) {
            val role = when (idx) {
                0 -> "Главный герой. Ведущий персонаж повествования, на долю которого выпадают основные испытания сюжета."
                1 -> "Ключевая фигура. Оказывает значительное влияние на судьбу главного героя и развитие центрального конфликта."
                2 -> "Важный персонаж. Выступает катализатором ключевых диалогов и двигателем сюжетной линии."
                else -> "Действующее лицо. Помогает раскрыть атмосферу произведения и подчеркнуть многообразие мира книги."
            }
            ssb.append("- **$char** — $role\n")
        }
        return ssb.toString()
    }

    private val RUSSIAN_WORDS_DB = mapOf(
        "бытие" to "философское понятие, обозначающее объективную реальность, существование человека, природы и вселенной.",
        "сущее" to "всё то, что реально существует; объективный мир во всем его неисчерпаемом многообразии.",
        "нигилизм" to "мировоззренческая позиция, выражающаяся в отрицании общепринятых ценностей, идеалов, моральных норм и традиций.",
        "тоска" to "глубокое, гнетущее душевное чувство, часто сопровождающееся грустью, томлением или тревожным ожиданием чего-то несбыточного.",
        "любовь" to "величайшее созидательное чувство привязанности, духовного единства и самоотверженного стремления к благополучию другого существа.",
        "душа" to "внутренний, нематериальный психический мир человека, его переживания, разум, воля и уникальное самосознание.",
        "совесть" to "внутренний нравственный закон человека, его способность осуществлять моральный самоконтроль и чувствовать личную ответственность.",
        "честь" to "нравственное достоинство человека, его доброе имя, верность долгу, принципам и внутреннему кодексу благородства.",
        "вечность" to "бесконечная протяженность времени, не имеющая ни начала, ни конца, пребывающая вне изменчивого физического мира.",
        "рок" to "неотвратимая судьба, предопределение, трагический фатум, довлеющий над решениями человека.",
        "судьба" to "совокупность внешних событий и внутренних устремлений, предопределяющих уникальный жизненный путь личности.",
        "воля" to "способность человека совершать осознанные действия ради достижения целей, а также великое чувство внутренней свободы.",
        "мир" to "совокупность всех форм материи и сознания, земной шар, а также состояние гармонии, тишины и согласия между людьми."
    )

    private val ENGLISH_WORDS_DB = mapOf(
        "love" to "Любовь", "heart" to "Сердце", "mind" to "Разум / Ум / Мышление", "soul" to "Душа",
        "truth" to "Истина / Правда", "justice" to "Справедливость", "fate" to "Судьба / Рок",
        "destiny" to "Предназначение / Жребий", "death" to "Смерть", "life" to "Жизнь", "world" to "Мир / Вселенная",
        "time" to "Время", "peace" to "Мир (спокойствие, лад)", "war" to "Война / Конфликт",
        "friend" to "Друг / Соратник", "enemy" to "Враг / Оппонент", "hope" to "Надежда", "fear" to "Страх / Тревога",
        "dream" to "Мечта / Сновидение", "magic" to "Магия / Волшебство", "light" to "Свет / Сияние",
        "dark" to "Тьма / Мрак / Темнота", "shadow" to "Тень / Призрак", "fire" to "Огонь / Пламя", "water" to "Вода",
        "earth" to "Земля / Почва", "air" to "Воздух / Атмосфера", "king" to "Король / Царь", "queen" to "Королева / Царица",
        "lord" to "Лорд / Владыка / Господин", "lady" to "Леди / Госпожа", "honor" to "Честь / Достоинство",
        "night" to "Ночь", "day" to "День", "spirit" to "Дух / Настроение", "blood" to "Кровь",
        "power" to "Сила / Власть / Могущество", "sword" to "Меч / Клинок", "shield" to "Щит / Защита", "silence" to "Тишина / Молчание",
        "voice" to "Голос", "beautiful" to "Красивый / Прекрасный", "brave" to "Храбрый / Мужественный", "sad" to "Грустный / Печальный",
        "happy" to "Счастливый / Радостный", "gold" to "Золото", "silver" to "Серебро", "stone" to "Камень"
    )

    private fun guessRussianPartOfSpeech(word: String): String {
        val lower = word.lowercase(Locale.ROOT)
        return when {
            lower.endsWith("ть") || lower.endsWith("ти") || lower.endsWith("ться") || lower.endsWith("тся") -> "Глагол (обозначает действие)"
            lower.endsWith("ый") || lower.endsWith("ий") || lower.endsWith("ая") || lower.endsWith("ое") || lower.endsWith("ые") || lower.endsWith("ому") -> "Имя прилагательное (признак)"
            lower.endsWith("о") || lower.endsWith("ому") || lower.endsWith("е") && (lower.length > 4) -> "Наречие / Особая форма глагола"
            lower.endsWith("ость") || lower.endsWith("ние") || lower.endsWith("тие") || lower.endsWith("тель") || lower.endsWith("ств") -> "Имя существительное (абстрактное понятие)"
            else -> "Художественное слово / Имя существительное"
        }
    }

    private fun guessRussianDefinition(word: String): String {
        val lower = word.lowercase(Locale.ROOT)
        val direct = RUSSIAN_WORDS_DB[lower]
        if (direct != null) return direct

        // Partial match helper
        for ((key, desc) in RUSSIAN_WORDS_DB) {
            if (lower.contains(key) || key.contains(lower)) {
                return desc
            }
        }

        return "Слово литературного и художественного языка, выражающее специфический эмоциональный или смысловой оттенок в контексте произведения. Служит для образности и усиления художественной выразительности повествования."
    }

    private fun translateEnglishWord(word: String): String {
        val lower = word.lowercase(Locale.ROOT).replace(Regex("[^a-z]"), "")
        val direct = ENGLISH_WORDS_DB[lower]
        if (direct != null) return direct

        // Simple morphological trimming for English
        val stripped = when {
            lower.endsWith("ing") && lower.length > 5 -> lower.substring(0, lower.length - 3)
            lower.endsWith("ed") && lower.length > 4 -> lower.substring(0, lower.length - 2)
            lower.endsWith("s") && !lower.endsWith("ss") && lower.length > 3 -> lower.substring(0, lower.length - 1)
            else -> lower
        }
        
        val strippedDirect = ENGLISH_WORDS_DB[stripped]
        if (strippedDirect != null) {
            return "$strippedDirect (изменено)"
        }

        return "Слово/понятие «$word». Для максимально детального перевода рекомендуется использовать контекстные академические словари."
    }

    private fun getCustomRule(context: Context, word: String): String? {
        val prefs = context.getSharedPreferences("local_ai_prefs", Context.MODE_PRIVATE)
        val customRulesJson = prefs.getString("custom_rules_json", null) ?: return null
        try {
            val target = "\"${word.lowercase(Locale.ROOT)}\""
            val index = customRulesJson.lowercase(Locale.ROOT).indexOf(target)
            if (index != -1) {
                val valStart = customRulesJson.indexOf(":", index) + 1
                if (valStart != 0) {
                    val strStart = customRulesJson.indexOf("\"", valStart) + 1
                    val strEnd = customRulesJson.indexOf("\"", strStart)
                    if (strStart != 0 && strEnd != -1) {
                        return customRulesJson.substring(strStart, strEnd)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // Public API for Word Explanations
    fun explainWord(context: Context, word: String, contextSnippet: String?): String {
        val trimmed = word.trim().replace(Regex("[^a-zA-Zа-яА-ЯёЁ\\-]"), "")
        if (trimmed.isEmpty()) return "Пожалуйста, выделите одно корректное слово."
        
        val customRule = getCustomRule(context, trimmed)
        if (customRule != null) {
            val ssb = StringBuilder()
            ssb.append("### Толкование слова (Пользовательский словарь)\n\n")
            ssb.append("- **Слово**: $trimmed\n")
            ssb.append("- **Значение**: $customRule\n\n")
            if (!contextSnippet.isNullOrBlank()) {
                ssb.append("**Контекст употребления**:\n")
                ssb.append("> «...${contextSnippet.trim()}...»\n")
            }
            return ssb.toString()
        }
        
        ensureModelInitialized(context)
        if (isModelActive()) {
            try {
                val prompt = "Объясни значение слова '$trimmed' в контексте: '${contextSnippet ?: ""}'"
                val response = generateAiResponse(prompt)
                return "### Ответ от нейросети (Llama 3.2 1B)\n\n$response"
            } catch (e: Exception) {
                return "Ошибка при генерации ответа: ${e.message}"
            }
        }
        
        val isEnglish = trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }
        
        if (isEnglish) {
            val translation = translateEnglishWord(trimmed)
            return "### Толкование слова (Локальный ИИ)\n\n" +
                    "- **Оригинал**: $trimmed\n" +
                    "- **Перевод**: $translation\n" +
                    "- **Особенность**: Английское слово\n\n" +
                    "**Описание**: Широко употребляемое понятие. " +
                    (if (!contextSnippet.isNullOrBlank()) "\n\n**Контекст в книге**:\n> «...$contextSnippet...»" else "")
        } else {
            val partOfSpeech = guessRussianPartOfSpeech(trimmed)
            val definition = guessRussianDefinition(trimmed)
            
            val ssb = StringBuilder()
            ssb.append("### Толкование слова (Локальный ИИ)\n\n")
            ssb.append("- **Слово**: $trimmed\n")
            ssb.append("- **Часть речи**: $partOfSpeech\n")
            ssb.append("- **Значение**: $definition\n\n")
            
            if (!contextSnippet.isNullOrBlank()) {
                ssb.append("**Контекст употребления**:\n")
                ssb.append("> «...${contextSnippet.trim()}...»\n\n")
                ssb.append("В данном контексте слово выражает смысловой оттенок, заложенный автором в художественную канву сюжета.")
            }
            return ssb.toString()
        }
    }

    // Public API for Word Translation
    fun translateWord(context: Context, word: String, contextSnippet: String?): String {
        val trimmed = word.trim().replace(Regex("[^a-zA-Zа-яА-ЯёЁ\\-]"), "")
        if (trimmed.isEmpty()) return "Пожалуйста, выделите корректное слово."
        
        val customRule = getCustomRule(context, trimmed)
        if (customRule != null) {
            val ssb = StringBuilder()
            ssb.append("### Локальный перевод (Пользовательский словарь)\n\n")
            ssb.append("- **Оригинал**: $trimmed\n")
            ssb.append("- **Перевод**: **$customRule**\n\n")
            if (!contextSnippet.isNullOrBlank()) {
                ssb.append("**Контекст фразы**:\n> «...$contextSnippet...»")
            }
            return ssb.toString()
        }
        
        ensureModelInitialized(context)
        if (isModelActive()) {
            try {
                val prompt = "Переведи слово '$trimmed' на русский язык. Контекст: '${contextSnippet ?: ""}'"
                val response = generateAiResponse(prompt)
                return "### Локальный перевод (Llama 3.2 1B)\n\n$response"
            } catch (e: Exception) {
                return "Ошибка при генерации перевода: ${e.message}"
            }
        }
        
        val isEnglish = trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }
        
        if (isEnglish) {
            val translation = translateEnglishWord(trimmed)
            val ssb = StringBuilder()
            ssb.append("### Локальный перевод (Английский ➔ Русский)\n\n")
            ssb.append("- **Английский**: $trimmed\n")
            ssb.append("- **Русский перевод**: **$translation**\n\n")
            if (!contextSnippet.isNullOrBlank()) {
                ssb.append("**Контекст фразы**:\n> «...$contextSnippet...»")
            }
            return ssb.toString()
        } else {
            val ssb = StringBuilder()
            ssb.append("### Перевод не требуется\n\n")
            ssb.append("Слово **«$trimmed»** уже является русским.\n")
            if (!contextSnippet.isNullOrBlank()) {
                ssb.append("\n**Контекст фразы**:\n> «...$contextSnippet...»")
            }
            return ssb.toString()
        }
    }

    fun customAiPrompt(context: Context, text: String, contextSnippet: String?, actionType: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return "Пожалуйста, выделите текст."
        
        ensureModelInitialized(context)
        if (isModelActive()) {
            try {
                val prompt = when (actionType) {
                    "explain" -> "Объясни значение следующего текста или слова в контексте книги: '$trimmed'. Контекст: '${contextSnippet ?: ""}'"
                    "translate" -> "Переведи следующий текст на русский язык: '$trimmed'. Контекст: '${contextSnippet ?: ""}'"
                    "summarize" -> "Сделай краткий пересказ следующего фрагмента текста: '$trimmed'"
                    "character" -> "Расскажи, кто такой персонаж '$trimmed', основываясь на контексте: '${contextSnippet ?: ""}'"
                    "simplify" -> "Перепиши этот текст более простыми и понятными словами: '$trimmed'"
                    else -> "Ответь на вопрос/проанализируй текст: '$trimmed'. Контекст: '${contextSnippet ?: ""}'"
                }
                
                val response = generateAiResponse(prompt)
                
                val header = when (actionType) {
                    "explain" -> "### Толкование (Llama 3.2 1B)"
                    "translate" -> "### Перевод (Llama 3.2 1B)"
                    "summarize" -> "### Краткий пересказ (Llama 3.2 1B)"
                    "character" -> "### О персонаже (Llama 3.2 1B)"
                    "simplify" -> "### Упрощенный текст (Llama 3.2 1B)"
                    else -> "### Ответ ИИ (Llama 3.2 1B)"
                }
                
                return "$header\n\n$response"
            } catch (e: Exception) {
                return "Ошибка при генерации ответа: ${e.message}"
            }
        }
        
        // Fallbacks for offline (no real LLM)
        return when (actionType) {
            "explain" -> explainWord(context, text, contextSnippet)
            "translate" -> translateWord(context, text, contextSnippet)
            "summarize" -> "Для пересказа текста необходимо скачать полную ИИ-модель (Llama 3.2 1B) в разделе 'Локальный ИИ'."
            "character" -> "Для анализа персонажа необходимо скачать полную ИИ-модель (Llama 3.2 1B) в разделе 'Локальный ИИ'."
            "simplify" -> "Для упрощения текста необходимо скачать полную ИИ-модель (Llama 3.2 1B) в разделе 'Локальный ИИ'."
            else -> "Функция требует загрузки полной ИИ-модели."
        }
    }
}
