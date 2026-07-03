package com.example.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    val repository = BookRepository(database.bookDao(), database.noteDao())
    val syncManager = SyncManager(application)

    // Observables from Database
    val allBooks: StateFlow<List<BookEntity>> = repository.allBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNotes: StateFlow<List<NoteEntity>> = repository.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Navigation & Preferences State
    var currentTab by mutableStateOf(0) // 0 = Shelf, 1 = Reader, 2 = Notes, 3 = Sync/Settings
    var selectedBook by mutableStateOf<BookEntity?>(null)

    private val prefs = application.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)

    private var _readerFontSize = mutableStateOf(prefs.getFloat("reader_font_size", 18f))
    var readerFontSize: Float
        get() = _readerFontSize.value
        set(value) {
            _readerFontSize.value = value
            prefs.edit().putFloat("reader_font_size", value).apply()
        }

    private var _readerTheme = mutableStateOf(prefs.getString("reader_theme", "light") ?: "light")
    var readerTheme: String
        get() = _readerTheme.value
        set(value) {
            _readerTheme.value = value
            prefs.edit().putString("reader_theme", value).apply()
        }

    private var _appThemeMode = mutableStateOf(prefs.getString("app_theme_mode", "system") ?: "system")
    var appThemeMode: String
        get() = _appThemeMode.value
        set(value) {
            _appThemeMode.value = value
            prefs.edit().putString("app_theme_mode", value).apply()
        }

    var readerPage by mutableStateOf(0) // Page size is 1000 characters

    // Filter/Search States
    var bookSearchQuery by mutableStateOf("")
    var selectedCategory by mutableStateOf("Все")
    var noteSearchQuery by mutableStateOf("")

    // Gemini API States
    var aiLoading by mutableStateOf(false)
    var aiResult by mutableStateOf("")
    var aiError by mutableStateOf<String?>(null)

    // Sync States
    var isServerRunning by mutableStateOf(false)
    var syncServerLogs = mutableStateListOf<String>()
    var localIpAddress by mutableStateOf("127.0.0.1")
    var remoteServerIp by mutableStateOf("")
    var cloudSyncUrl by mutableStateOf("")
    var exportJsonString by mutableStateOf("")
    var isSyncLoading by mutableStateOf(false)

    init {
        // Prepare initial content and IP info
        checkAndInsertDefaultBooks()
        refreshIpAddress()
    }

    private fun checkAndInsertDefaultBooks() {
        viewModelScope.launch {
            allBooks.collectLatest { books ->
                if (books.isEmpty()) {
                    insertDefaultClassics()
                }
            }
        }
    }

    private suspend fun insertDefaultClassics() {
        val defaultBooks = listOf(
            BookEntity(
                title = "Преступление и наказание",
                author = "Фёдор Достоевский",
                content = """Глава I

В начале июля, в чрезвычайно жаркое время, под вечер, один молодой человек вышел из своей каморки, которую нанимал от жильцов в С — м переулке, на улицу и медленно, как бы в нерешимости, отправился к К — ну мосту.

Он благополучно избегнул встречи с своею хозяйкой на лестнице. Каморка его приходилась под самою крышей высокого пятиэтажного дома и походила более на шкаф, чем на квартиру. Квартирная же хозяйка его, у которой он нанимал эту каморку с обедом и прислугой, помещалась одним ярусом ниже, в отдельной квартире, и каждый раз, при выходе на улицу, ему непременно надо было проходить мимо хозяйкиной кухни, почти всегда настежь отворенной на лестницу. И каждый раз молодой человек, проходя мимо, чувствовал какое-то болезненное и трусливое ощущение, которого стыдился и от которого морщился. Он был должен кругом хозяйке и боялся с нею встретиться.

Не то чтоб он был так труслив и забит, совсем даже напротив; но с некоторого времени он был в раздражительном и напряженном состоянии, похожем на ипохондрию. Он до того углубился в себя и уединился от всех, что боялся даже всякой встречи, не только встречи с хозяйкой. Он был задавлен бедностью; но даже стесненное положение перестало в последнее время тяготить его. Насущными делами своими он совсем перестал и не хотел заниматься. Никакой хозяйки, в сущности, он не боялся, что бы та ни замышляла против него. Но останавливаться на лестнице, слушать всякий вздор про всю эту обыденную дребедень, до которой ему нет никакого дела, все эти приставания о платеже, угрозы, жалобы, и при этом самому изворачиваться, извиняться, лгать, — нет, уж лучше проскользнуть как-нибудь кошкой по лестнице и ушмыгнуть, чтобы никто не видал.

Глава II

Раскольников вышел на улицу в полном смятении. Страшная жара, духота, толкотня, всюду известка, леса, кирпич, пыль и та особенная летняя вонь, столь известная каждому петербуржцу, не имеющему возможности нанять дачу, — всё это разом неприятно потрясло и без того уже расстроенные нервы юноши. Нестерпимая же вонь из распивочных, которых в этой части города особенное множество, и пьяные, поминутно попадавшиеся, несмотря на буднее время, довершили грустный и чистый колорит картины. Чувство глубочайшего омерзения мелькнуло на миг в тонких чертах молодого человека. Кстати, он был замечательно хорош собою, с прекрасными темными глазами, темно-рус, выше среднего роста, тонок и строен. Но скоро он впал как бы в глубокую задумчивость, даже, вернее сказать, как бы в какое-то забытье, и пошел, уже не замечая окружающего, да и не желая его замечать.""",
                coverGradientStart = "#E94560",
                coverGradientEnd = "#1A1A2E",
                category = "Классика",
                totalCharacters = 2174
            ),
            BookEntity(
                title = "Герой нашего времени",
                author = "Михаил Лермонтов",
                content = """Бэла

Я ехал на перекладных из Тифлиса. Вся кладь моей тележки состояла из одного небольшого чемодана, который до половины был набит путевыми записками о Грузии. Большая часть из них, к счастью для вас, потеряна, а чемодан с остальными вещами, к счастью для меня, остался цел.

Солнце начинало прятаться за снеговой хребет, когда я въехал в Койшаурскую долину. Осетин-извозчик неутомимо погонял лошадей, чтоб успеть до ночи взобраться на Койшаурскую гору, и во весь голос пел песни. Славное место эта долина! Со всех сторон горы неприступные, красноватые скалы, обвешанные зеленым плющом и увенчанные купами чинар, желтые обрывы, промоины, а там высоко-высоко золотая бахрома снегов, а внизу Арагва, обнявшись с другой безымянной речкой, шумно вырывающейся из черного, полного мглы ущелья, тянется серебряною нитью и сверкает, как змея своей чешуей.

Подъехав к подошве Койшаурской горы, мы остановились у духана. Тут толпилось десятка два грузин и горцев; поблизости караван верблюдов остановился для ночлега. Мне пришлось нанять быков, чтоб втащить мою тележку на эту проклятую гору, потому что была уже осень и гололедица, а эта гора имеет около двух верст длины. Делать нечего, я нанял шесть быков и нескольких осетин. Один из них взял мой чемодан на плечи, другие стали помогать быкам только криками. За моею тележкою четверка быков тащила другую, как ни в чем не бывало, несмотря на то, что она была накладена доверху.

Мне было любопытно узнать причину такой разницы. Обернувшись, я увидел человека в офицерском сюртуке без эполет и в мохнатой черкесской шапке. Он казался лет пятидесяти; смуглый цвет лица его показывал, что оно давно знакомо с закавказским солнцем, и преждевременно поседевшие усы не соответствовали его твердой походке и бодрому виду. Я подошел к нему и поклонился: он молча ответил мне на поклон и пустил огромный клуб дыма.

— Мы с вами попутчики, кажется?

Он молча опять поклонился.

— Вы, верно, едете в Ставрополь?

— Так-с точно... с казенными вещами.

— Скажите, пожалуйста, отчего это эту тяжелую коляску четыре быка везут шутя, а мою повозку шесть быков еле тащат с помощью этих осетин?

Он загадочно улыбнулся и значительно взглянул на меня.

— Вы, верно, недавно на Кавказе?

— Около года, — отвечал я.

Он улыбнулся во второй раз.""",
                coverGradientStart = "#0F3460",
                coverGradientEnd = "#16213E",
                category = "Классика",
                totalCharacters = 2243
            ),
            BookEntity(
                title = "Евгений Онегин",
                author = "Александр Пушкин",
                content = """Глава Первая

«Мой дядя самых честных правил,
Когда не в шутку занемог,
Он уважать себя заставил
И лучше выдумать не мог.
Его пример другим наука;
Но, боже мой, какая скука
С больным сидеть и день и ночь,
Не отходя ни шагу прочь!
Какое низкое коварство
Полуживого забавлять,
Ему подушки поправлять,
Печально подносить лекарство,
Вздыхать и думать про себя:
Когда же черт возьмет тебя!»

Так думал молодой повеса,
Летя в пыли на почтовых,
Всевышней волею Зевеса
Наследник всех своих родных. —
Друзья Людмилы и Руслана!
С героем моего романа
Без предисловий, сей же час
Позвольте познакомить вас:
Онегин, добрый мой приятель,
Родился на брегах Невы,
Где, может быть, родились вы
Или блистали, мой читатель;
Там некогда гулял и я:
Но вреден север для меня.

Судьба Онегина хранила:
Сперва Madame за ним ходила,
Потом Monsieur ее сменил;
Ребенок был резов, но мил.
Monsieur l'Abbé, француз убогой,
Чтоб не измучилось дитя,
Учил его всему шутя,
Не докучал моралью строгой,
Слегка за шалости бранил
И в Летний сад гулять водил.

Когда же юности мятежной
Пришла Онегину пора,
Пора надежд и грусти нежной,
Monsieur прогнали со двора.
Вот мой Онегин на свободе;
Острижен по последней моде;
Как dandy лондонский одет —
И наконец увидел свет.
Он по-французски совершенно
Мог изъясняться и писал;
Легко мазурку танцевал
И кланялся непринужденно;
Чего ж вам больше? Свет решил,
Что он умен и очень мил.""",
                coverGradientStart = "#8A307F",
                coverGradientEnd = "#79A7D3",
                category = "Поэзия",
                totalCharacters = 1558
            ),
            BookEntity(
                title = "Палата №6",
                author = "Антон Чехов",
                content = """Глава I

В больничном дворе стоит небольшой флигель, окруженный целым лесом крапивы, чертополоха и дикой конопли. Крыша на нем ржавая, труба наполовину обвалилась, крыльцо у входа сгнило и поросло травой, а от штукатурки остались только следы. Передним фасадом обращен он к больнице, задом — к полю, от которого отделяет его серый больничный забор с гвоздями. Эти гвозди, обращенные остриями кверху, и забор, и сам флигель имеют тот особый унылый, окаянный вид, который у нас бывает только у больничных и тюремных построек.

Если вы не боитесь ожечься о крапиву, то пойдемте по узкой тропинке, ведущей к флигелю, и посмотрим, что делается внутри. Отворив первую дверь, мы входим в сени. Здесь у стены и около печки навалены целые горы больничного хлама. Матрацы, старые драные халаты, брюки, синие в полоску рубахи, никуда не годная обувь — вся эта рвань свалена в кучи, слежалась, гниет и издает удушливый запах.

На хламе всегда с трубкой в зубах лежит сторож Никита, старый отставной солдат с изношенными нашивками. У него суровое, пропитое лицо, нависшие брови, придающие ему сходство с овчаркой, и красный нос; он мал ростом, на вид сухощав и жилист, но осанка у него внушительная и кулаки здоровенные. Он принадлежит к числу тех нерассуждающих, исполнительных и тупых людей, которые больше всего на свете любят порядок и потому убеждены, что их надо бить. И Никита бьет. Он бьет по лицу, по груди, по спине, по чем попало, и убежден, что без этого не было бы порядка в палате.

Далее вы входите в большую, просторную комнату, занимающую весь флигель, если не считать сеней. Стены здесь вымазаны грязно-голубой краской, потолок закопчен, как в курной избе; ясно, что здесь зимой печи дымят и угарно. Окна изнутри изуродованы железными решетками. Пол серый и занозистый. Пахнет кислым капустным супом, вонючей гарью, клопами и аммиаком, и этот запах в первую минуту производит на вас впечатление, будто вы входите в зверинец.""",
                coverGradientStart = "#3A6073",
                coverGradientEnd = "#3A6073",
                category = "Проза",
                totalCharacters = 1918
            )
        )
        repository.insertBooks(defaultBooks)
    }

    // Book Interactions
    fun openBook(book: BookEntity) {
        selectedBook = book
        readerPage = book.currentProgressChar / 1000
        currentTab = 1 // Switch to Reader Tab
        viewModelScope.launch {
            repository.updateProgress(book.id, book.currentProgressChar)
        }
    }

    fun updateReadingProgress(charOffset: Int) {
        val book = selectedBook ?: return
        val clampedOffset = charOffset.coerceIn(0, book.totalCharacters)
        selectedBook = book.copy(currentProgressChar = clampedOffset)
        readerPage = clampedOffset / 1000
        viewModelScope.launch {
            repository.updateProgress(book.id, clampedOffset)
        }
    }

    fun nextPage() {
        val book = selectedBook ?: return
        val nextPageChar = (readerPage + 1) * 1000
        if (nextPageChar < book.totalCharacters) {
            updateReadingProgress(nextPageChar)
        }
    }

    fun previousPage() {
        if (readerPage > 0) {
            updateReadingProgress((readerPage - 1) * 1000)
        }
    }

    fun deleteBook(bookId: Int) {
        viewModelScope.launch {
            repository.deleteBookById(bookId)
            if (selectedBook?.id == bookId) {
                selectedBook = null
            }
        }
    }

    fun addNewBook(title: String, author: String, content: String, category: String) {
        viewModelScope.launch {
            val totalChars = content.length
            val newBook = BookEntity(
                title = title,
                author = author,
                content = content,
                category = category.ifEmpty { "Классика" },
                totalCharacters = totalChars,
                coverGradientStart = getRandomGradientStartColor(),
                coverGradientEnd = getRandomGradientEndColor()
            )
            repository.insertBook(newBook)
        }
    }

    private fun getRandomGradientStartColor(): String {
        val colors = listOf("#FF6B6B", "#4D96FF", "#6BCB77", "#FFD93D", "#9B5DE5", "#00F5D4")
        return colors.random()
    }

    private fun getRandomGradientEndColor(): String {
        val colors = listOf("#2B2E4A", "#1A1A2E", "#0F3460", "#2D4263", "#3F3B6C", "#1E5128")
        return colors.random()
    }

    // Note Interactions
    fun createNote(selectedText: String, noteText: String) {
        val book = selectedBook ?: return
        val offset = readerPage * 1000
        viewModelScope.launch {
            val note = NoteEntity(
                bookId = book.id,
                bookTitle = book.title,
                selectedText = selectedText,
                noteText = noteText,
                charOffset = offset
            )
            repository.insertNote(note)
        }
    }

    fun deleteNote(noteId: Int) {
        viewModelScope.launch {
            repository.deleteNoteById(noteId)
        }
    }

    // Sync Commands
    fun refreshIpAddress() {
        localIpAddress = syncManager.getLocalIpAddress()
    }

    fun startSyncServer() {
        viewModelScope.launch(Dispatchers.IO) {
            isServerRunning = true
            syncServerLogs.clear()
            syncServerLogs.add("Запуск сервера...")
            syncManager.startSyncServer(
                repository = repository,
                port = 8080,
                onLog = { logMsg ->
                    viewModelScope.launch(Dispatchers.Main) {
                        syncServerLogs.add(logMsg)
                    }
                }
            )
        }
    }

    fun stopSyncServer() {
        syncManager.stopSyncServer()
        isServerRunning = false
        syncServerLogs.add("Сервер остановлен.")
    }

    fun startClientSync() {
        val ip = remoteServerIp.trim()
        if (ip.isEmpty()) {
            syncServerLogs.add("Ошибка: Не указан IP адрес сервера.")
            return
        }
        isSyncLoading = true
        viewModelScope.launch {
            val success = syncManager.clientSync(
                repository = repository,
                ipAddress = ip,
                port = 8080,
                onLog = { logMsg ->
                    viewModelScope.launch(Dispatchers.Main) {
                        syncServerLogs.add(logMsg)
                    }
                }
            )
            isSyncLoading = false
        }
    }

    fun performCloudSync() {
        val url = cloudSyncUrl.trim()
        if (url.isEmpty()) {
            syncServerLogs.add("Ошибка: Не указана ссылка на облачный сервер.")
            return
        }
        isSyncLoading = true
        viewModelScope.launch {
            syncManager.syncWithCloud(
                repository = repository,
                url = url,
                onLog = { logMsg ->
                    viewModelScope.launch(Dispatchers.Main) {
                        syncServerLogs.add(logMsg)
                    }
                }
            )
            isSyncLoading = false
        }
    }

    fun exportDatabaseState() {
        viewModelScope.launch {
            exportJsonString = syncManager.exportToJson(repository)
            syncServerLogs.add("Данные успешно экспортированы в текстовый буфер.")
        }
    }

    fun importDatabaseState(json: String) {
        viewModelScope.launch {
            isSyncLoading = true
            val success = syncManager.importFromJson(repository, json)
            isSyncLoading = false
            if (success) {
                syncServerLogs.add("Импорт завершен успешно! База данных обновлена.")
            } else {
                syncServerLogs.add("Ошибка: Не удалось распознать формат JSON.")
            }
        }
    }

    // Gemini API integration
    fun askGemini(prompt: String) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            aiError = "Ключ API Gemini не настроен. Настройте его через панель Secrets в AI Studio, чтобы использовать ИИ-ассистента."
            aiResult = ""
            return
        }

        aiLoading = true
        aiError = null
        aiResult = "ИИ думает..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val finalPrompt = "Вы — профессиональный литературный критик и ассистент по чтению. Ответьте на вопрос по книге или заметке кратко и ёмко на русском языке.\n\n$prompt"
                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(GeminiPart(text = finalPrompt))
                        )
                    )
                )
                val response = GeminiClient.service.generateContent(apiKey, request)
                val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

                viewModelScope.launch(Dispatchers.Main) {
                    aiLoading = false
                    if (textResponse != null) {
                        aiResult = textResponse
                    } else {
                        aiError = "Не удалось получить ответ от ИИ."
                    }
                }
            } catch (e: Exception) {
                viewModelScope.launch(Dispatchers.Main) {
                    aiLoading = false
                    aiError = "Ошибка соединения: ${e.localizedMessage}"
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        syncManager.stopSyncServer()
    }
}
