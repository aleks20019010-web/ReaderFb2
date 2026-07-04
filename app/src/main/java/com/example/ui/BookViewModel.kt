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
    var detailedBook by mutableStateOf<BookEntity?>(null)

    // Scanning Device for Local Books
    var isScanning by mutableStateOf(false)
    var scanProgressText by mutableStateOf("")

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
        
        // Initialize and observe background scanning state
        com.example.service.BookScannerState.initialize(application)
        viewModelScope.launch {
            com.example.service.BookScannerState.isScanning.collect { active ->
                isScanning = active
            }
        }
        viewModelScope.launch {
            com.example.service.BookScannerState.scanProgressText.collect { text ->
                scanProgressText = text
            }
        }
    }

    private fun checkAndInsertDefaultBooks() {
        viewModelScope.launch {
            val books = repository.allBooks.first()
            if (books.isEmpty()) {
                insertDefaultClassics()
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
        viewModelScope.launch {
            repository.updateProgress(book.id, book.currentProgressChar)
        }
        
        val intent = android.content.Intent(getApplication(), ReaderActivity::class.java).apply {
            putExtra("BOOK_ID", book.id)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
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

    fun importBookFromUri(uri: android.net.Uri, context: android.content.Context, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                var fileName = "imported_book.fb2"
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = it.getString(nameIndex)
                        }
                    }
                }
                
                val ext = fileName.substringAfterLast(".", "").lowercase()
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Не удалось прочитать файл (файл пуст).")
                    }
                    return@launch
                }
                
                val digest = java.security.MessageDigest.getInstance("SHA-1")
                digest.update(bytes)
                val computedSha1 = digest.digest().joinToString("") { String.format("%02x", it) }
                
                val duplicate = repository.allBooks.first().find { it.sha1 == computedSha1 }
                if (duplicate != null) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Эта книга уже импортирована: \"${duplicate.title}\".")
                    }
                    return@launch
                }
                
                val importedFolder = java.io.File(context.filesDir, "imported_books")
                if (!importedFolder.exists()) {
                    importedFolder.mkdirs()
                }
                val localFile = java.io.File(importedFolder, "$computedSha1.$ext")
                localFile.writeBytes(bytes)
                
                var parsedTitle = fileName.substringBeforeLast(".")
                var parsedAuthor = "Неизвестен"
                var parsedContent = ""
                var parsedSeries: String? = null
                var parsedSeriesIndex: Int? = null
                var parsedLanguage: String? = "ru"
                
                if (ext == "fb2") {
                    val rawText = decodeBytesToString(bytes)
                    val parsed = parseFb2DetailedText(rawText, parsedTitle)
                    parsedTitle = parsed.title
                    parsedAuthor = parsed.author
                    parsedContent = parsed.content
                    parsedSeries = parsed.series
                    parsedSeriesIndex = parsed.seriesIndex
                    parsedLanguage = parsed.language
                } else if (ext == "zip") {
                    java.io.ByteArrayInputStream(bytes).use { bais ->
                        java.util.zip.ZipInputStream(bais).use { zis ->
                            var entry = zis.nextEntry
                            var foundFb2 = false
                            while (entry != null) {
                                val entryName = entry.name.lowercase()
                                if (!entry.isDirectory && entryName.endsWith(".fb2")) {
                                    foundFb2 = true
                                    val entryBytes = zis.readBytes()
                                    val rawText = decodeBytesToString(entryBytes)
                                    val parsed = parseFb2DetailedText(rawText, entryName.removeSuffix(".fb2"))
                                    parsedTitle = parsed.title
                                    parsedAuthor = parsed.author
                                    parsedContent = parsed.content
                                    parsedSeries = parsed.series
                                    parsedSeriesIndex = parsed.seriesIndex
                                    parsedLanguage = parsed.language
                                    break
                                }
                                entry = zis.nextEntry
                            }
                            if (!foundFb2) {
                                throw java.io.IOException("Внутри ZIP-архива не найден файл .fb2")
                            }
                        }
                    }
                } else if (ext == "epub") {
                    val (title, content) = parseEpub(localFile)
                    parsedTitle = title
                    parsedAuthor = "Локальный EPUB"
                    parsedContent = content
                } else {
                    parsedContent = decodeBytesToString(bytes)
                    parsedAuthor = "Локальный TXT"
                }
                
                if (parsedContent.isBlank()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Файл не содержит читаемого текста.")
                    }
                    return@launch
                }
                
                val coverPath = extractAndSaveCover(localFile, computedSha1)
                
                val newBook = BookEntity(
                    title = parsedTitle,
                    author = parsedAuthor,
                    content = parsedContent,
                    category = "Локальные",
                    totalCharacters = parsedContent.length,
                    coverGradientStart = getRandomGradientStartColor(),
                    coverGradientEnd = getRandomGradientEndColor(),
                    filePath = localFile.absolutePath,
                    sha1 = computedSha1,
                    series = parsedSeries,
                    language = parsedLanguage,
                    fileSize = bytes.size.toLong(),
                    coverPath = coverPath,
                    seriesIndex = parsedSeriesIndex
                )
                
                repository.insertBook(newBook)
                
                withContext(Dispatchers.Main) {
                    onResult(true, "Книга \"$parsedTitle\" успешно импортирована!")
                }
            } catch (e: Exception) {
                android.util.Log.e("BookScanner", "Error importing from SAF: ", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Ошибка импорта: ${e.localizedMessage}")
                }
            }
        }
    }

    fun toggleFavorite(bookId: Int) {
        viewModelScope.launch {
            val book = allBooks.value.find { it.id == bookId }
            if (book != null) {
                val updated = book.copy(isFavorite = !book.isFavorite)
                repository.updateBook(updated)
                if (selectedBook?.id == bookId) {
                    selectedBook = updated
                }
                if (detailedBook?.id == bookId) {
                    detailedBook = updated
                }
            }
        }
    }

    fun updateBookReview(bookId: Int, reviewText: String) {
        viewModelScope.launch {
            val book = allBooks.value.find { it.id == bookId }
            if (book != null) {
                val updated = book.copy(review = reviewText)
                repository.updateBook(updated)
                if (selectedBook?.id == bookId) {
                    selectedBook = updated
                }
                if (detailedBook?.id == bookId) {
                    detailedBook = updated
                }
            }
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

    fun startLocalBookScan(rootPath: String = "/storage/emulated/0") {
        if (isScanning) return
        val context = getApplication<Application>()
        val intent = android.content.Intent(context, com.example.service.BookScannerService::class.java).apply {
            putExtra("ROOT_PATH", rootPath)
        }
        context.startService(intent)
    }

    private suspend fun scanDirectoryForBooks(rootPath: String) {
        withContext(Dispatchers.Main) {
            isScanning = true
            scanProgressText = "Запуск сканирования..."
        }
        
        try {
            withContext(Dispatchers.IO) {
                val existingSha1s = try {
                    repository.allBooks.first().mapNotNull { it.sha1 }.toMutableSet()
                } catch (e: Exception) {
                    android.util.Log.e("BookScanner", "Failed to load existing SHA1 list", e)
                    mutableSetOf<String>()
                }

                val existingTitles = try {
                    repository.allBooks.first().map { it.title.lowercase() }.toMutableSet()
                } catch (e: Exception) {
                    android.util.Log.e("BookScanner", "Failed to load existing titles", e)
                    mutableSetOf<String>()
                }
                
                val rootDir = java.io.File(rootPath)
                if (!rootDir.exists() || !rootDir.isDirectory) {
                    withContext(Dispatchers.Main) {
                        scanProgressText = "Папка не найдена или недоступна: $rootPath"
                    }
                    return@withContext
                }
                
                val filesToProcess = mutableListOf<java.io.File>()
                
                fun traverse(dir: java.io.File) {
                    try {
                        val list = dir.listFiles() ?: return
                        for (file in list) {
                            if (file.isDirectory) {
                                val name = file.name.lowercase()
                                if (name.startsWith(".") || name == "android" || name == "cache" || name == "temp" || name == "tmp" || name == "thumbnails" || name == "thumbnail") {
                                    continue
                                }
                                traverse(file)
                            } else {
                                val ext = file.extension.lowercase()
                                if (ext == "txt" || ext == "fb2" || ext == "epub" || ext == "zip") {
                                    // Exclude files >= 30MB to prevent memory crashes
                                    if (file.length() < 30 * 1024 * 1024 && file.length() > 0) {
                                        filesToProcess.add(file)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BookScanner", "Error traversing directory: ${dir.absolutePath}", e)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    scanProgressText = "Поиск файлов в $rootPath..."
                }
                traverse(rootDir)
                
                if (filesToProcess.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        scanProgressText = "Книг (*.txt, *.fb2, *.epub, *.zip) не найдено в $rootPath"
                    }
                    return@withContext
                }

                var importedCount = 0
                for ((index, file) in filesToProcess.withIndex()) {
                    val progressText = "Чтение (${index + 1}/${filesToProcess.size}): ${file.name}"
                    withContext(Dispatchers.Main) {
                        scanProgressText = progressText
                    }
                    
                    kotlin.runCatching {
                        val ext = file.extension.lowercase()
                        val computedSha1 = calculateSha1(file.absolutePath)
                        
                        // Check duplicates by SHA1
                        if (existingSha1s.contains(computedSha1)) {
                            return@runCatching
                        }

                        var parsedTitle = file.nameWithoutExtension
                        var parsedAuthor = "Неизвестен"
                        var parsedContent = ""
                        var parsedSeries: String? = null
                        var parsedLanguage: String? = "ru"
                        
                        if (ext == "fb2") {
                            val parsed = parseFb2Detailed(file)
                            parsedTitle = parsed.title
                            parsedAuthor = parsed.author
                            parsedContent = parsed.content
                            parsedSeries = parsed.series
                            parsedLanguage = parsed.language
                        } else if (ext == "zip") {
                            val parsed = parseFb2FromZip(file)
                            parsedTitle = parsed.title
                            parsedAuthor = parsed.author
                            parsedContent = parsed.content
                            parsedSeries = parsed.series
                            parsedLanguage = parsed.language
                        } else if (ext == "epub") {
                            val (title, content) = parseEpub(file)
                            parsedTitle = title
                            parsedAuthor = "Локальный EPUB"
                            parsedContent = content
                        } else {
                            parsedContent = readTextFile(file)
                            parsedAuthor = "Локальный TXT"
                        }
                        
                        if (existingTitles.contains(parsedTitle.lowercase())) {
                            return@runCatching
                        }

                        if (parsedContent.isNotBlank()) {
                            // Extract cover
                            val coverPath = extractAndSaveCover(file, computedSha1)
                            
                            val newBook = BookEntity(
                                title = parsedTitle,
                                author = parsedAuthor,
                                content = parsedContent,
                                category = "Локальные",
                                totalCharacters = parsedContent.length,
                                coverGradientStart = getRandomGradientStartColor(),
                                coverGradientEnd = getRandomGradientEndColor(),
                                filePath = file.absolutePath,
                                sha1 = computedSha1,
                                series = parsedSeries,
                                language = parsedLanguage,
                                fileSize = file.length(),
                                coverPath = coverPath
                            )
                            repository.insertBook(newBook)
                            existingSha1s.add(computedSha1)
                            existingTitles.add(parsedTitle.lowercase())
                            importedCount++
                        }
                    }.onFailure { t ->
                        android.util.Log.e("BookScanner", "Failed to import book: ${file.name}", t)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (importedCount > 0) {
                        scanProgressText = "Успешно импортировано новых книг: $importedCount"
                    } else {
                        scanProgressText = "Все найденные книги уже есть в библиотеке (${filesToProcess.size} файлов)"
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BookScanner", "Critical error in scanDirectoryForBooks", e)
            withContext(Dispatchers.Main) {
                scanProgressText = "Ошибка сканирования: ${e.localizedMessage}"
            }
        } finally {
            withContext(Dispatchers.Main) {
                isScanning = false
            }
        }
    }

    suspend fun calculateSha1(filePath: String): String = withContext(Dispatchers.IO) {
        val TAG = "SHA1Calculator"
        android.util.Log.d(TAG, "Starting SHA-1 calculation for path: $filePath")
        
        val file = java.io.File(filePath)
        
        // Check if file exists and is readable
        if (!file.exists()) {
            android.util.Log.e(TAG, "Error: File does not exist at path: $filePath")
            return@withContext generateFallbackId(filePath, null)
        }
        if (!file.canRead()) {
            android.util.Log.e(TAG, "Error: File is not readable (insufficient permissions): $filePath")
            return@withContext generateFallbackId(filePath, file)
        }
        
        val fileSize = file.length()
        if (fileSize == 0L) {
            android.util.Log.e(TAG, "Error: File is empty (size is 0 bytes): $filePath")
            return@withContext generateFallbackId(filePath, file)
        }

        kotlin.runCatching {
            val ext = file.extension.lowercase()
            android.util.Log.d(TAG, "Detected file extension: $ext")
            
            when (ext) {
                "fb2" -> {
                    android.util.Log.d(TAG, "Reading directly from FB2 file")
                    java.io.FileInputStream(file).use { fis ->
                        computeSha1FromStream(fis)
                    }
                }
                "zip" -> {
                    android.util.Log.d(TAG, "Decompressing ZIP to find FB2 contents")
                    var fb2Found = false
                    var sha1Result: String? = null
                    
                    // Attempt to parse/decompress ZIP
                    java.io.FileInputStream(file).use { fis ->
                        java.util.zip.ZipInputStream(fis).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                val entryName = entry.name.lowercase()
                                if (!entry.isDirectory && (entryName.endsWith(".fb2") || entryName.endsWith(".fb2.xml"))) {
                                    android.util.Log.d(TAG, "Found FB2 entry in ZIP: ${entry.name} (size: ${entry.size})")
                                    fb2Found = true
                                    
                                    // Make sure entry is not empty or invalid
                                    if (entry.size == 0L) {
                                        android.util.Log.e(TAG, "FB2 entry inside ZIP is empty: ${entry.name}")
                                    } else {
                                        sha1Result = computeSha1FromStream(zis)
                                        android.util.Log.d(TAG, "Successfully computed SHA-1 for ZIP entry: $sha1Result")
                                    }
                                    break // Grab only the first one as requested
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }
                    
                    if (!fb2Found) {
                        android.util.Log.e(TAG, "Error: No FB2 file found inside the ZIP archive: $filePath")
                        generateFallbackId(filePath, file)
                    } else if (sha1Result == null) {
                        android.util.Log.e(TAG, "Error: Failed to compute SHA-1 for the first FB2 entry inside the ZIP archive: $filePath")
                        generateFallbackId(filePath, file)
                    } else {
                        sha1Result!!
                    }
                }
                else -> {
                    // Treat other files (epub, txt, etc.) or unknown types by reading directly
                    android.util.Log.d(TAG, "Reading directly from file format: $ext")
                    java.io.FileInputStream(file).use { fis ->
                        computeSha1FromStream(fis)
                    }
                }
            }
        }.getOrElse { throwable ->
            android.util.Log.e(TAG, "Exception caught during SHA-1 computation for: $filePath", throwable)
            generateFallbackId(filePath, file)
        }
    }

    private fun generateFallbackId(filePath: String, file: java.io.File?): String {
        val size = file?.length() ?: 0L
        val lastModified = file?.lastModified() ?: System.currentTimeMillis()
        val uniqueString = "${filePath}_${size}_${lastModified}"
        val fallback = "fallback_" + Math.abs(uniqueString.hashCode().toLong()).toString(16)
        android.util.Log.w("SHA1Calculator", "Generated fallback ID for $filePath: $fallback")
        return fallback
    }

    private fun computeSha1FromStream(inputStream: java.io.InputStream): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { String.format("%02x", it) }
    }

    private fun readTextFile(file: java.io.File): String {
        val bytes = file.readBytes()
        return decodeBytesToString(bytes)
    }

    private fun decodeBytesToString(bytes: ByteArray): String {
        try {
            // Safe detection from XML prolog first
            val headerSize = if (bytes.size > 1024) 1024 else bytes.size
            val header = String(bytes, 0, headerSize, java.nio.charset.StandardCharsets.ISO_8859_1)
            val match = """encoding=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE).find(header)
            if (match != null) {
                val encName = match.groupValues[1].trim()
                try {
                    return String(bytes, java.nio.charset.Charset.forName(encName))
                } catch (e: Exception) {
                    // fall back if charset name is invalid or unsupported
                }
            }
        } catch (e: Exception) {
            // ignore and fallback
        }

        try {
            val utf8Decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
            utf8Decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            val charBuffer = utf8Decoder.decode(java.nio.ByteBuffer.wrap(bytes))
            return charBuffer.toString()
        } catch (e: Exception) {
            try {
                return String(bytes, java.nio.charset.Charset.forName("Windows-1251"))
            } catch (e2: Exception) {
                return String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
            }
        }
    }

    private fun parseFb2FromZip(file: java.io.File): ParsedBook {
        java.io.FileInputStream(file).use { fis ->
            java.util.zip.ZipInputStream(fis).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name.lowercase()
                    if (!entry.isDirectory && entryName.endsWith(".fb2")) {
                        val bytes = zis.readBytes()
                        val rawText = decodeBytesToString(bytes)
                        return parseFb2DetailedText(rawText, entryName.removeSuffix(".fb2"))
                    }
                    entry = zis.nextEntry
                }
            }
        }
        throw java.io.IOException("No fb2 file found inside zip")
    }

    data class ParsedBook(
        val title: String,
        val author: String,
        val content: String,
        val series: String? = null,
        val language: String? = "ru",
        val seriesIndex: Int? = null
    )

    private fun parseFb2DetailedText(rawText: String, fallbackName: String): ParsedBook {
        val parsed = com.example.service.Fb2Parser.parse(rawText, fallbackName)
        return ParsedBook(
            title = parsed.title,
            author = parsed.author,
            content = parsed.content,
            series = parsed.series,
            language = parsed.language,
            seriesIndex = parsed.seriesIndex
        )
    }

    private fun parseFb2Detailed(file: java.io.File): ParsedBook {
        val rawText = readTextFile(file)
        return parseFb2DetailedText(rawText, file.nameWithoutExtension)
    }

    private fun parseEpub(file: java.io.File): Pair<String, String> {
        val content = readEpubText(file)
        val title = file.nameWithoutExtension
        return Pair(title, content)
    }

    private fun readEpubText(file: java.io.File): String {
        val sb = java.lang.StringBuilder()
        try {
            java.io.FileInputStream(file).use { fis ->
                java.util.zip.ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".html") || entry.name.endsWith(".xhtml")) {
                            val content = zis.bufferedReader(java.nio.charset.StandardCharsets.UTF_8).readText()
                            val textOnly = content.replace("<[^>]*>".toRegex(), " ")
                            sb.append(textOnly).append("\n\n")
                        }
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sb.toString().replace("\\s+".toRegex(), " ").trim()
    }

    fun extractCoverFromFb2(fb2Content: String): android.graphics.Bitmap? {
        try {
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(java.io.StringReader(fb2Content))

            var eventType = parser.eventType
            var coverId: String? = null
            val binaryDataMap = mutableMapOf<String, String>()

            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name?.lowercase()
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    if (tagName == "image") {
                        for (i in 0 until parser.attributeCount) {
                            val attrName = parser.getAttributeName(i).lowercase()
                            if (attrName == "href" || attrName.endsWith("href")) {
                                val href = parser.getAttributeValue(i)
                                if (coverId == null) {
                                    coverId = href.removePrefix("#")
                                }
                            }
                        }
                    } else if (tagName == "binary") {
                        var id: String? = null
                        for (i in 0 until parser.attributeCount) {
                            if (parser.getAttributeName(i).lowercase() == "id") {
                                id = parser.getAttributeValue(i)
                            }
                        }
                        if (id != null) {
                            val base64Text = parser.nextText()
                            binaryDataMap[id] = base64Text
                        }
                    }
                }
                eventType = parser.next()
            }

            val targetId = coverId ?: "cover"
            var base64Data = binaryDataMap[targetId] ?: binaryDataMap[coverId]
            
            if (base64Data == null) {
                val key = binaryDataMap.keys.find { it.lowercase().contains("cover") }
                if (key != null) {
                    base64Data = binaryDataMap[key]
                }
            }
            
            if (base64Data == null && binaryDataMap.isNotEmpty()) {
                base64Data = binaryDataMap.values.first()
            }

            if (base64Data != null) {
                val cleanBase64 = base64Data.replace("\\s".toRegex(), "")
                val decodedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                return android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            }
        } catch (e: Exception) {
            android.util.Log.e("BookScanner", "Error parsing FB2 cover with XmlPullParser", e)
        }
        return null
    }

    fun extractCoverUsingRegex(fb2Content: String): android.graphics.Bitmap? {
        try {
            // 1. Parse all binaries. Robust to attribute order, spaces, single/double quotes, and namespace tags
            val binaryDataMap = mutableMapOf<String, String>()
            val binaryBlockRegex = """<binary([^>]*)>([\s\S]*?)</binary>""".toRegex(RegexOption.IGNORE_CASE)
            for (match in binaryBlockRegex.findAll(fb2Content)) {
                val attrs = match.groups[1]?.value ?: ""
                val base64 = match.groups[2]?.value ?: ""
                val idMatch = """\bid\s*=\s*["']?([^"'\s>]+)["']?""".toRegex(RegexOption.IGNORE_CASE).find(attrs)
                val id = idMatch?.groups[1]?.value
                if (id != null) {
                    binaryDataMap[id] = base64
                    binaryDataMap[id.lowercase()] = base64
                }
            }

            // 2. Find the coverpage image ID
            // First try specifically within <coverpage>...</coverpage>
            val coverpageRegex = """<coverpage>([\s\S]*?)</coverpage>""".toRegex(RegexOption.IGNORE_CASE)
            val coverpageMatch = coverpageRegex.find(fb2Content)
            var coverId: String? = null
            if (coverpageMatch != null) {
                val coverpageContent = coverpageMatch.groups[1]?.value ?: ""
                val imageRegex = """<image[^>]*(?:href|l:href)\s*=\s*["']?#?([^"'\s>]+)["']?""".toRegex(RegexOption.IGNORE_CASE)
                val imageMatch = imageRegex.find(coverpageContent)
                coverId = imageMatch?.groups[1]?.value
            }

            // Fallback: look for the first <image> tag in the document
            if (coverId == null) {
                val imageRegex = """<image[^>]*(?:href|l:href)\s*=\s*["']?#?([^"'\s>]+)["']?""".toRegex(RegexOption.IGNORE_CASE)
                val imageMatch = imageRegex.find(fb2Content)
                coverId = imageMatch?.groups[1]?.value
            }

            // 3. Retrieve base64 data
            var base64Data: String? = null
            if (coverId != null) {
                base64Data = binaryDataMap[coverId] ?: binaryDataMap[coverId.lowercase()] ?: binaryDataMap[coverId.removePrefix("#")] ?: binaryDataMap[coverId.removePrefix("#").lowercase()]
            }

            // Fallback: look for keys containing "cover" or "front"
            if (base64Data == null) {
                val coverKey = binaryDataMap.keys.find { it.lowercase().contains("cover") || it.lowercase().contains("front") }
                if (coverKey != null) {
                    base64Data = binaryDataMap[coverKey]
                }
            }

            // Fallback 2: first binary if it looks like an image
            if (base64Data == null && binaryDataMap.isNotEmpty()) {
                base64Data = binaryDataMap.values.first()
            }

            if (base64Data != null) {
                val cleanBase64 = base64Data.replace("\\s".toRegex(), "")
                val decodedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                return android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            }
        } catch (e: Exception) {
            android.util.Log.e("BookScanner", "Error in regex cover extraction", e)
        }
        return null
    }

    fun extractCoverFromEpub(epubFile: java.io.File): android.graphics.Bitmap? {
        try {
            java.io.FileInputStream(epubFile).use { fis ->
                java.util.zip.ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name.lowercase()
                        if (!entry.isDirectory && (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))) {
                            if (name.contains("cover")) {
                                val bytes = zis.readBytes()
                                if (bytes.isNotEmpty()) {
                                    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BookScanner", "Error extracting EPUB cover", e)
        }
        return null
    }

    fun saveCoverToCache(context: android.content.Context, sha1: String, bitmap: android.graphics.Bitmap): String? {
        return try {
            val cacheDir = java.io.File(context.cacheDir, "book_covers")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val file = java.io.File(cacheDir, "$sha1.jpg")
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("BookScanner", "Failed to save cover to cache", e)
            null
        }
    }

    fun extractAndSaveCover(file: java.io.File, sha1: String): String? {
        val ext = file.extension.lowercase()
        var bitmap: android.graphics.Bitmap? = null
        try {
            if (ext == "fb2") {
                val fb2Content = readTextFile(file)
                bitmap = extractCoverFromFb2(fb2Content) ?: extractCoverUsingRegex(fb2Content)
            } else if (ext == "zip") {
                java.io.FileInputStream(file).use { fis ->
                    java.util.zip.ZipInputStream(fis).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val entryName = entry.name.lowercase()
                            if (!entry.isDirectory && entryName.endsWith(".fb2")) {
                                val bytes = zis.readBytes()
                                val fb2Content = decodeBytesToString(bytes)
                                bitmap = extractCoverFromFb2(fb2Content) ?: extractCoverUsingRegex(fb2Content)
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
            } else if (ext == "epub") {
                bitmap = extractCoverFromEpub(file)
            }
            
            if (bitmap != null) {
                return saveCoverToCache(getApplication<Application>(), sha1, bitmap)
            }
        } catch (e: Exception) {
            android.util.Log.e("BookScanner", "Error in extractAndSaveCover for file ${file.name}", e)
        }
        return null
    }

    /**
     * Determines the encoding of the FB2 file from the XML prolog,
     * reads the file, and extracts the contents of the <annotation> tag in the correct encoding.
     */
    suspend fun detectAndReadFile(filePath: String): String? = withContext(Dispatchers.IO) {
        val TAG = "FB2Annotation"
        android.util.Log.d(TAG, "Detecting encoding and extracting annotation for file: $filePath")
        
        val bytes = readFirstBytesOfFb2(filePath)
        if (bytes == null || bytes.isEmpty()) {
            android.util.Log.e(TAG, "Failed to read bytes from file: $filePath")
            return@withContext null
        }
        
        // 1. Detect encoding from XML prolog
        val detectedEncoding = parseEncodingFromProlog(bytes)
        val charsetsToTry = mutableListOf<String>()
        
        if (detectedEncoding != null) {
            charsetsToTry.add(detectedEncoding)
        }
        
        // Add fallback candidate charsets
        val fallbacks = listOf("UTF-8", "windows-1251", "KOI8-R", "ISO-8859-1")
        for (fallback in fallbacks) {
            if (!charsetsToTry.contains(fallback)) {
                charsetsToTry.add(fallback)
            }
        }
        
        android.util.Log.d(TAG, "Will attempt charsets in order: $charsetsToTry")
        
        for (charset in charsetsToTry) {
            try {
                if (!java.nio.charset.Charset.isSupported(charset)) {
                    android.util.Log.w(TAG, "Charset not supported by JVM: $charset")
                    continue
                }
                
                android.util.Log.d(TAG, "Trying to extract annotation using charset: $charset")
                val bais = java.io.ByteArrayInputStream(bytes)
                val annotation = extractAnnotationFromStream(bais, charset)
                
                if (annotation != null) {
                    // Check if the decoded text looks malformed (e.g., replacement character or only unreadable characters)
                    if (charset.lowercase() == "utf-8" && hasMalformedCharacters(annotation)) {
                        android.util.Log.w(TAG, "Parsed UTF-8 string contains malformed characters, trying next charset.")
                        continue
                    }
                    
                    android.util.Log.i(TAG, "Successfully extracted annotation in encoding: $charset")
                    return@withContext annotation
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error extracting annotation with charset $charset", e)
            }
        }
        
        android.util.Log.w(TAG, "Failed to extract annotation with any of the candidate charsets")
        return@withContext null
    }

    /**
     * Helper to read the first 256KB of the FB2 file (from a direct file or a ZIP).
     * This holds enough content to cover the XML prolog and the <annotation> block.
     */
    private fun readFirstBytesOfFb2(filePath: String, limit: Int = 256 * 1024): ByteArray? {
        val file = java.io.File(filePath)
        if (!file.exists()) return null
        
        val ext = file.extension.lowercase()
        if (ext == "zip") {
            try {
                java.io.FileInputStream(file).use { fis ->
                    java.util.zip.ZipInputStream(fis).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val entryName = entry.name.lowercase()
                            if (!entry.isDirectory && (entryName.endsWith(".fb2") || entryName.endsWith(".fb2.xml"))) {
                                val bos = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(4096)
                                var totalRead = 0
                                var read: Int = 0
                                while (totalRead < limit && zis.read(buffer).also { read = it } != -1) {
                                    val toWrite = minOf(read, limit - totalRead)
                                    bos.write(buffer, 0, toWrite)
                                    totalRead += toWrite
                                }
                                return bos.toByteArray()
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FB2Annotation", "Error reading ZIP file: $filePath", e)
            }
        } else {
            try {
                java.io.FileInputStream(file).use { fis ->
                    val bos = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    var totalRead = 0
                    var read: Int = 0
                    while (totalRead < limit && fis.read(buffer).also { read = it } != -1) {
                        val toWrite = minOf(read, limit - totalRead)
                        bos.write(buffer, 0, toWrite)
                        totalRead += toWrite
                    }
                    return bos.toByteArray()
                }
            } catch (e: Exception) {
                android.util.Log.e("FB2Annotation", "Error reading FB2 file: $filePath", e)
            }
        }
        return null
    }

    /**
     * Looks at the XML prolog in the header bytes to find the encoding attribute.
     */
    private fun parseEncodingFromProlog(bytes: ByteArray): String? {
        try {
            val size = minOf(bytes.size, 1024)
            val header = String(bytes, 0, size, java.nio.charset.StandardCharsets.ISO_8859_1)
            val match = """<\?xml[^>]*encoding=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE).find(header)
            if (match != null) {
                val enc = match.groupValues[1].trim()
                android.util.Log.d("FB2Annotation", "Parsed encoding from XML prolog: $enc")
                return enc
            }
        } catch (e: Exception) {
            android.util.Log.e("FB2Annotation", "Error parsing prolog encoding", e)
        }
        return null
    }

    /**
     * Parses the annotation from an input stream using the specified charset.
     */
    private fun extractAnnotationFromStream(inputStream: java.io.InputStream, charsetName: String): String? {
        try {
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            
            val reader = java.io.InputStreamReader(inputStream, charsetName)
            parser.setInput(reader)
            
            var eventType = parser.eventType
            var inAnnotation = false
            val annotationText = java.lang.StringBuilder()
            
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name?.lowercase()
                
                when (eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        if (tagName == "annotation" || tagName == "description") {
                            // If we enter annotation or description (some files use description directly)
                            inAnnotation = true
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> {
                        if (inAnnotation) {
                            val txt = parser.text
                            if (txt != null) {
                                annotationText.append(txt)
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        if (tagName == "p" && inAnnotation) {
                            annotationText.append("\n")
                        } else if (tagName == "annotation" || tagName == "description") {
                            inAnnotation = false
                            break
                        }
                    }
                }
                eventType = parser.next()
            }
            
            val result = annotationText.toString().trim()
            return if (result.isNotEmpty()) result else null
        } catch (e: Exception) {
            android.util.Log.e("FB2Annotation", "Failed to parse XML using charset $charsetName", e)
            return null
        }
    }

    /**
     * Validates whether a string has been incorrectly decoded (i.e. contains replacement characters).
     */
    private fun hasMalformedCharacters(str: String): Boolean {
        return str.contains('\uFFFD')
    }

    override fun onCleared() {
        super.onCleared()
        syncManager.stopSyncServer()
    }
}
