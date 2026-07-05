package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.BookEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("MainApplication", "MainApplication onCreate: Initializing app.")
        
        // Handle first launch and demo books insertion
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("first_launch", true)
        
        if (isFirstLaunch) {
            Log.d("MainApplication", "First launch detected, preparing demo books.")
            val database = AppDatabase.getDatabase(this)
            val bookDao = database.bookDao()
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val demoBooks = listOf(
                        BookEntity(
                            sha1 = "dostoevsky_crime_punishment_sha1",
                            title = "Преступление и наказание",
                            author = "Фёдор Достоевский",
                            content = """Глава I

В начале июля, в чрезвычайно жаркое время, под вечер, один молодой человек вышел из своей каморки, которую нанимал от жильцов в С — м переулке, на улицу и медленно, как бы в нерешимости, отправился к К — ну мосту.

Он благополучно избегнул встречи с своею хозяйкой на лестнице. Каморка его приходилась под самою крышей высокого пятиэтажного дома и походила более на шкаф, чем на квартиру. Квартирная же хозяйка его, у которой он нанимал эту каморку с обедом и прислугой, помещалась одним ярусом ниже, в отдельной квартире, и каждый раз, при выходе на улицу, ему непременно надо было проходить мимо хозяйкиной кухни, почти всегда настежь отворенной на лестницу. И каждый раз молодой человек, проходя мимо, чувствовал какое-то болезненное и трусливое ощущение, которого стыдился и от которого морщился. Он был должен кругом хозяйке и боялся с нею встретиться.

Не то чтоб он был так труслив и забит, совсем даже напротив; но с некоторого времени он был в раздражительном и напряженном состоянии, похожем на ипохондрию. Он до того углубился в себя и уединился от всех, что боялся даже всякой встречи, не только встречи с хозяйкой. Он был задавлен бедностью; но даже стесненное положение перестало в последнее время тяготить его.""",
                            coverGradientStart = "#E94560",
                            coverGradientEnd = "#1A1A2E",
                            category = "Классика",
                            totalCharacters = 1380
                        ),
                        BookEntity(
                            sha1 = "lermontov_hero_of_our_time_sha1",
                            title = "Герой нашего времени",
                            author = "Михаил Лермонтов",
                            content = """Бэла

Я ехал на перекладных из Тифлиса. Вся кладь моей тележки состояла из одного небольшого чемодана, который до половины был набит путевыми записками о Грузии. Большая часть из них, к счастью для вас, потеряна, а чемодан с остальными вещами, к счастью для меня, остался цел.

Солнце начинало прятаться за снеговой хребет, когда я въехал в Койшаурскую долину. Осетин-извозчик неутомимо погонял лошадей, чтоб успеть до ночи взобраться на Койшаурскую гору, и во весь голос пел песни. Славное место эта долина! Со всех сторон горы неприступные, красноватые скалы, обвешанные зеленым плющом и увенчанные купами чинар, желтые обрывы, промоины, а там высоко-высоко золотая бахрома снегов, а внизу Арагва, обнявшись с другой безымянной речкой, шумно вырывающейся из черного, полного мглы ущелья, тянется серебряною нитью и сверкает, как змея своей чешуей.""",
                            coverGradientStart = "#0F3460",
                            coverGradientEnd = "#16213E",
                            category = "Классика",
                            totalCharacters = 952
                        ),
                        BookEntity(
                            sha1 = "pushkin_eugene_onegin_sha1",
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
Когда же черт возьмет тебя!»""",
                            coverGradientStart = "#8A307F",
                            coverGradientEnd = "#79A7D3",
                            category = "Поэзия",
                            totalCharacters = 450
                        ),
                        BookEntity(
                            sha1 = "chekhov_ward_number_6_sha1",
                            title = "Палата №6",
                            author = "Антон Чехов",
                            content = """Глава I

В больничном дворе стоит небольшой флигель, окруженный целым лесом крапивы, чертополоха и дикой конопли. Крыша на нем ржавая, труба наполовину обвалилась, крыльцо у входа сгнило и поросло травой, а от штукатурки остались только следы. Передним фасадом обращен он к больнице, задом — к полю, от которого отделяет его серый больничный забор с гвоздями. Эти гвозди, обращенные остриями кверху, и забор, и сам флигель имеют тот особый унылый, окаянный вид, который у нас бывает только у больничных и тюремных построек.""",
                            coverGradientStart = "#3A6073",
                            coverGradientEnd = "#3A6073",
                            category = "Проза",
                            totalCharacters = 530
                        )
                    )
                    
                    bookDao.insertBooks(demoBooks)
                    prefs.edit().putBoolean("first_launch", false).apply()
                    Log.d("MainApplication", "Successfully inserted 4 demo books and marked first_launch as false.")
                } catch (e: Exception) {
                    Log.e("MainApplication", "Failed to insert demo books on first launch", e)
                }
            }
        }
    }
}
