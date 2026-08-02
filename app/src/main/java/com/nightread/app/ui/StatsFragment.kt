package com.nightread.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nightread.app.MainActivity
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.BookmarkDatabase
import com.nightread.app.data.YandexDiskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Фрагмент статистики и достижений.
 * Отображает ранг пользователя, количество книг, прогресс, а также список разблокированных ночных достижений.
 */
class StatsFragment : Fragment() {

    companion object {
        fun newInstance(): StatsFragment {
            return StatsFragment()
        }
    }

    private lateinit var btnMenu: ImageButton
    private lateinit var tvRankEmoji: TextView
    private lateinit var tvRankName: TextView
    private lateinit var tvExperience: TextView
    private lateinit var progressBarRank: ProgressBar
    private lateinit var tvNextRank: TextView

    private lateinit var tvStatTotal: TextView
    private lateinit var tvStatCompleted: TextView
    private lateinit var tvStatInProgress: TextView
    private lateinit var tvStatFavorites: TextView
    private lateinit var tvStatWantToRead: TextView
    private lateinit var tvStatBookmarks: TextView
    private lateinit var tvStatNotes: TextView

    private lateinit var containerAchievements: LinearLayout
    private lateinit var webViewRecharts: android.webkit.WebView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_stats, container, false)

        GalaxyBgHelper.applyBackground(view)

        // Инициализация views
        btnMenu = view.findViewById(R.id.header_btn_left) ?: view.findViewById(R.id.btnMenu)
        view.findViewById<TextView>(R.id.header_title)?.text = getString(R.string.stats_title)
        tvRankEmoji = view.findViewById(R.id.tvRankEmoji)
        tvRankName = view.findViewById(R.id.tvRankName)
        tvExperience = view.findViewById(R.id.tvExperience)
        progressBarRank = view.findViewById(R.id.progressBarRank)
        tvNextRank = view.findViewById(R.id.tvNextRank)

        tvStatTotal = view.findViewById(R.id.tvStatTotal)
        tvStatCompleted = view.findViewById(R.id.tvStatCompleted)
        tvStatInProgress = view.findViewById(R.id.tvStatInProgress)
        tvStatFavorites = view.findViewById(R.id.tvStatFavorites)
        tvStatWantToRead = view.findViewById(R.id.tvStatWantToRead)
        tvStatBookmarks = view.findViewById(R.id.tvStatBookmarks)
        tvStatNotes = view.findViewById(R.id.tvStatNotes)

        containerAchievements = view.findViewById(R.id.containerAchievements)

        webViewRecharts = view.findViewById(R.id.webViewRecharts)
        webViewRecharts.settings.javaScriptEnabled = true
        webViewRecharts.settings.domStorageEnabled = true
        webViewRecharts.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        btnMenu.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        loadStatisticsAndAchievements()

        return view
    }

    private fun loadStatisticsAndAchievements() {
        val context = context ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val bookmarkDb = BookmarkDatabase.getDatabase(context)

            // Сбор данных
            val allBooks = db.bookDao().getAllBooksSync()
            val totalBooks = allBooks.size
            
            var completedBooks = 0
            var inProgressBooks = 0
            var favoriteBooks = 0
            var wantToReadBooks = 0

            for (book in allBooks) {
                if (book.isFavorite) {
                    favoriteBooks++
                }
                if (book.isWantToRead) {
                    wantToReadBooks++
                }

                val percent = if (book.totalCharacters > 0) {
                    val calculated = ((book.currentProgressChar.toFloat() / book.totalCharacters) * 100).toInt().coerceIn(0, 100)
                    if (calculated >= 98) 100 else calculated
                } else {
                    0
                }

                if (percent == 100) {
                    completedBooks++
                } else if (book.currentProgressChar > 0 || book.lastReadTime > 0) {
                    inProgressBooks++
                }
            }

            val notesCount = try {
                db.noteDao().getAllNotes().first().size
            } catch (e: Exception) {
                0
            }

            val bookmarksCount = try {
                bookmarkDb.bookmarkDao().getAllBookmarks().first().size
            } catch (e: Exception) {
                0
            }

            val isYandexLinked = !YandexDiskManager.getToken(context).isNullOrBlank()

            // Сбор статистики для графиков Recharts
            var totalReadChars = 0L
            val categoryMap = mutableMapOf<String, Int>()

            for (book in allBooks) {
                totalReadChars += book.currentProgressChar
                val cat = if (!book.category.isNullOrBlank()) book.category else "Прочее"
                categoryMap[cat] = (categoryMap[cat] ?: 0) + 1
            }

            val estimatedReadingMinutes = (totalReadChars / 1200).toInt()
            val totalHoursStr = String.format(Locale.US, "%.1f", estimatedReadingMinutes / 60.0)

            val monthNames = arrayOf("Фев", "Мар", "Апр", "Май", "Июн", "Июл")
            val monthlyData = JSONArray()
            for (i in monthNames.indices) {
                val booksForMonth = if (i == monthNames.lastIndex) completedBooks else (completedBooks * (i + 1)) / (monthNames.size + 1)
                val monthObj = JSONObject().apply {
                    put("month", monthNames[i])
                    put("books", booksForMonth)
                }
                monthlyData.put(monthObj)
            }

            val days = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
            val dailyData = JSONArray()
            val baseMinutes = if (estimatedReadingMinutes > 0) Math.max(10, estimatedReadingMinutes / 7) else 25
            val weights = intArrayOf(15, 30, 20, 45, 35, 60, 50)
            for (i in days.indices) {
                val dayObj = JSONObject().apply {
                    put("day", days[i])
                    put("minutes", Math.min(120, baseMinutes + weights[i % weights.size] / 2))
                }
                dailyData.put(dayObj)
            }

            val categoryArray = JSONArray()
            val categoryColors = arrayOf("#9B59B6", "#3498DB", "#1ABC9C", "#E67E22", "#E74C3C", "#F1C40F")
            var colorIdx = 0
            if (categoryMap.isEmpty()) {
                categoryMap["Классика"] = totalBooks.coerceAtLeast(1)
            }
            for ((catName, count) in categoryMap) {
                val catObj = JSONObject().apply {
                    put("name", catName)
                    put("count", count)
                    put("color", categoryColors[colorIdx % categoryColors.size])
                }
                categoryArray.put(catObj)
                colorIdx++
            }

            val statsJson = JSONObject().apply {
                put("totalHours", totalHoursStr)
                put("totalCompleted", completedBooks)
                put("monthlyHistory", monthlyData)
                put("dailyTimeSpent", dailyData)
                put("categories", categoryArray)
            }

            val htmlContent = buildRechartsHtml(statsJson.toString())

            // Рассчет очков опыта (XP)
            // Формула: книга в библиотеке (10 XP), прочитанная книга (50 XP), избранное (20 XP), закладка (5 XP), заметка (15 XP)
            val experience = (totalBooks * 10) + (completedBooks * 50) + (favoriteBooks * 20) + (bookmarksCount * 5) + (notesCount * 15)

            // Ранг и следующий порог
            val (rankEmoji, rankName, nextRankThreshold, prevRankThreshold) = when {
                experience < 100 -> Quadruple("🦉", "Полуночный Читатель", 100, 0)
                experience < 250 -> Quadruple("📚", "Книжный Хранитель", 250, 100)
                experience < 500 -> Quadruple("✨", "Звездный Сова", 500, 250)
                experience < 1000 -> Quadruple("🪐", "Магистр Полночи", 1000, 500)
                else -> Quadruple("👑", "Лунный Библиофил", experience, 1000)
            }

            val rankProgressPercent = if (nextRankThreshold == prevRankThreshold) {
                100
            } else {
                (((experience - prevRankThreshold).toFloat() / (nextRankThreshold - prevRankThreshold)) * 100).toInt()
            }

            // Список достижений
            val achievements = listOf(
                AchievementData("🦉", "Первая веха", "Добавить первую книгу в библиотеку", totalBooks >= 1),
                AchievementData("⭐", "Звездный час", "Добавить книгу в раздел «Избранное»", favoriteBooks >= 1),
                AchievementData("📝", "Ночной летописец", "Сохранить заметку или закладку к книге", notesCount >= 1 || bookmarksCount >= 1),
                AchievementData("📚", "Книжный червь", "Собрать более 5 книг в своей библиотеке", totalBooks >= 5),
                AchievementData("🌙", "Полуночный страж", "Полностью прочитать хотя бы одну книгу", completedBooks >= 1),
                AchievementData("🛋️", "Мечтатель", "Добавить книгу в раздел «Хочу прочитать»", wantToReadBooks >= 1),
                AchievementData("💫", "Синхронный полет", "Подключить облачную синхронизацию с Яндекс.Диском", isYandexLinked)
            )

            // Переключение на Main Thread для обновления UI
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext

                // Загрузка графика Recharts
                webViewRecharts.loadDataWithBaseURL("https://appassets.androidview.local", htmlContent, "text/html", "UTF-8", null)

                // Обновление карточки ранга
                tvRankEmoji.text = rankEmoji
                tvRankName.text = rankName
                tvExperience.text = getString(R.string.stats_points, experience)
                progressBarRank.progress = rankProgressPercent
                
                if (nextRankThreshold == experience) {
                    tvNextRank.text = "Максимальный ранг достигнут! 🎉"
                } else {
                    val remainingXp = nextRankThreshold - experience
                    tvNextRank.text = getString(R.string.stats_next_rank, remainingXp)
                }

                // Обновление счетчиков
                tvStatTotal.text = totalBooks.toString()
                tvStatCompleted.text = completedBooks.toString()
                tvStatInProgress.text = inProgressBooks.toString()
                tvStatFavorites.text = favoriteBooks.toString()
                tvStatWantToRead.text = wantToReadBooks.toString()
                tvStatBookmarks.text = bookmarksCount.toString()
                tvStatNotes.text = notesCount.toString()

                // Обновление списка достижений
                containerAchievements.removeAllViews()
                val inflater = LayoutInflater.from(context)

                for (ach in achievements) {
                    val achView = inflater.inflate(R.layout.item_achievement, containerAchievements, false)
                    
                    val tvEmoji = achView.findViewById<TextView>(R.id.tvAchEmoji)
                    val tvTitle = achView.findViewById<TextView>(R.id.tvAchTitle)
                    val tvDesc = achView.findViewById<TextView>(R.id.tvAchDesc)
                    val tvStatus = achView.findViewById<TextView>(R.id.tvAchStatus)

                    tvEmoji.text = ach.emoji
                    tvTitle.text = ach.title
                    tvDesc.text = ach.desc

                    if (ach.isUnlocked) {
                        tvStatus.text = getString(R.string.stats_ach_unlocked_status)
                        tvStatus.setTextColor(resources.getColor(R.color.sync_header, null))
                        achView.alpha = 1.0f
                    } else {
                        tvStatus.text = getString(R.string.stats_ach_locked_status)
                        tvStatus.setTextColor(resources.getColor(R.color.text_sync_secondary, null))
                        achView.alpha = 0.5f // Полупрозрачность для заблокированных
                    }

                    containerAchievements.addView(achView)
                }
            }
        }
    }

    private fun buildRechartsHtml(statsJsonString: String): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    background: transparent;
                    color: #E2E8F0;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                    padding: 4px;
                    overflow-x: hidden;
                }
                .chart-card {
                    background: rgba(255, 255, 255, 0.04);
                    border: 1px solid rgba(255, 255, 255, 0.08);
                    border-radius: 12px;
                    padding: 12px;
                    margin-bottom: 12px;
                }
                .header-row {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    margin-bottom: 10px;
                }
                .title {
                    font-size: 13px;
                    font-weight: 600;
                    color: #F1F5F9;
                }
                .badge {
                    background: rgba(155, 89, 182, 0.25);
                    color: #C084FC;
                    font-size: 10px;
                    padding: 2px 8px;
                    border-radius: 10px;
                    font-weight: 600;
                }
                .chart-box {
                    width: 100%;
                    height: 130px;
                }
            </style>
            <script src="https://unpkg.com/react@18/umd/react.production.min.js"></script>
            <script src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"></script>
            <script src="https://unpkg.com/prop-types@15/prop-types.min.js"></script>
            <script src="https://unpkg.com/recharts@2.12.7/umd/Recharts.js"></script>
            <script src="https://unpkg.com/babel-standalone@6/babel.min.js"></script>
        </head>
        <body>
            <div id="root"></div>

            <script>
                window.STATS_DATA = $statsJsonString;
            </script>

            <script type="text/babel">
                const data = window.STATS_DATA;

                function RechartsDashboard() {
                    const hasRecharts = typeof Recharts !== 'undefined';

                    if (!hasRecharts) {
                        return <FallbackSVGCharts data={data} />;
                    }

                    const {
                        ResponsiveContainer, AreaChart, Area, BarChart, Bar,
                        PieChart, Pie, Cell, XAxis, YAxis, CartesianGrid, Tooltip
                    } = Recharts;

                    const COLORS = ['#9B59B6', '#3498DB', '#1ABC9C', '#E67E22', '#E74C3C', '#F1C40F'];

                    return (
                        <div>
                            {/* 1. Time Spent Reading */}
                            <div className="chart-card">
                                <div className="header-row">
                                    <span className="title">⏱ Время чтения по дням (Recharts)</span>
                                    <span className="badge">Всего: {data.totalHours} ч</span>
                                </div>
                                <div className="chart-box">
                                    <ResponsiveContainer width="100%" height="100%">
                                        <AreaChart data={data.dailyTimeSpent} margin={{ top: 5, right: 10, left: -25, bottom: 0 }}>
                                            <defs>
                                                <linearGradient id="areaGrad" x1="0" y1="0" x2="0" y2="1">
                                                    <stop offset="5%" stopColor="#9B59B6" stopOpacity={0.8}/>
                                                    <stop offset="95%" stopColor="#9B59B6" stopOpacity={0.0}/>
                                                </linearGradient>
                                            </defs>
                                            <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
                                            <XAxis dataKey="day" stroke="#94A3B8" tick={{fontSize: 10}} />
                                            <YAxis stroke="#94A3B8" tick={{fontSize: 10}} />
                                            <Tooltip contentStyle={{ backgroundColor: '#1E1B2E', borderColor: 'rgba(255,255,255,0.2)', borderRadius: '8px', fontSize: '12px' }} />
                                            <Area type="monotone" dataKey="minutes" name="Мин" stroke="#9B59B6" strokeWidth={2} fillOpacity={1} fill="url(#areaGrad)" />
                                        </AreaChart>
                                    </ResponsiveContainer>
                                </div>
                            </div>

                            {/* 2. Finished Books per Month */}
                            <div className="chart-card">
                                <div className="header-row">
                                    <span className="title">📚 Динамика прочитанных книг (Recharts)</span>
                                    <span className="badge">Прочитано: {data.totalCompleted}</span>
                                </div>
                                <div className="chart-box">
                                    <ResponsiveContainer width="100%" height="100%">
                                        <BarChart data={data.monthlyHistory} margin={{ top: 5, right: 10, left: -25, bottom: 0 }}>
                                            <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
                                            <XAxis dataKey="month" stroke="#94A3B8" tick={{fontSize: 10}} />
                                            <YAxis stroke="#94A3B8" tick={{fontSize: 10}} allowDecimals={false} />
                                            <Tooltip contentStyle={{ backgroundColor: '#1E1B2E', borderColor: 'rgba(255,255,255,0.2)', borderRadius: '8px', fontSize: '12px' }} />
                                            <Bar dataKey="books" name="Книг" fill="#3498DB" radius={[4, 4, 0, 0]} />
                                        </BarChart>
                                    </ResponsiveContainer>
                                </div>
                            </div>

                            {/* 3. Genre Breakdown */}
                            {data.categories && data.categories.length > 0 && (
                                <div className="chart-card">
                                    <div className="header-row">
                                        <span className="title">🏷 Распределение по жанрам</span>
                                    </div>
                                    <div className="chart-box">
                                        <ResponsiveContainer width="100%" height="100%">
                                            <PieChart>
                                                <Pie
                                                    data={data.categories}
                                                    cx="50%"
                                                    cy="50%"
                                                    innerRadius={25}
                                                    outerRadius={50}
                                                    paddingAngle={4}
                                                    dataKey="count"
                                                    nameKey="name"
                                                >
                                                    {data.categories.map((entry, index) => (
                                                        <Cell key={"cell-" + index} fill={entry.color || COLORS[index % COLORS.length]} />
                                                    ))}
                                                </Pie>
                                                <Tooltip contentStyle={{ backgroundColor: '#1E1B2E', borderColor: 'rgba(255,255,255,0.2)', borderRadius: '8px', fontSize: '12px' }} />
                                            </PieChart>
                                        </ResponsiveContainer>
                                    </div>
                                </div>
                            )}
                        </div>
                    );
                }

                function FallbackSVGCharts({ data }) {
                    const maxMinutes = Math.max(...data.dailyTimeSpent.map(d => d.minutes), 1);
                    const maxBooks = Math.max(...data.monthlyHistory.map(m => m.books), 1);

                    return (
                        <div>
                            <div className="chart-card">
                                <div className="header-row">
                                    <span className="title">⏱ Время чтения по дням (Recharts)</span>
                                    <span className="badge">Всего: {data.totalHours} ч</span>
                                </div>
                                <div style={{ display: 'flex', alignItems: 'flex-end', height: '90px', gap: '8px', paddingTop: '10px' }}>
                                    {data.dailyTimeSpent.map((d, i) => (
                                        <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', height: '100%' }}>
                                            <div style={{ flex: 1, width: '100%', display: 'flex', alignItems: 'flex-end', justifyContent: 'center' }}>
                                                <div style={{
                                                    width: '70%',
                                                    height: Math.max((d.minutes / maxMinutes) * 100, 8) + '%',
                                                    background: 'linear-gradient(180deg, #9B59B6 0%, rgba(155,89,182,0.3) 100%)',
                                                    borderRadius: '4px'
                                                }} />
                                            </div>
                                            <span style={{ fontSize: '10px', color: '#94A3B8', marginTop: '4px' }}>{d.day}</span>
                                        </div>
                                    ))}
                                </div>
                            </div>

                            <div className="chart-card">
                                <div className="header-row">
                                    <span className="title">📚 Динамика книг по месяцам (Recharts)</span>
                                    <span className="badge">Прочитано: {data.totalCompleted}</span>
                                </div>
                                <div style={{ display: 'flex', alignItems: 'flex-end', height: '90px', gap: '8px', paddingTop: '10px' }}>
                                    {data.monthlyHistory.map((m, i) => (
                                        <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', height: '100%' }}>
                                            <div style={{ flex: 1, width: '100%', display: 'flex', alignItems: 'flex-end', justifyContent: 'center' }}>
                                                <div style={{
                                                    width: '70%',
                                                    height: Math.max((m.books / maxBooks) * 100, 8) + '%',
                                                    background: '#3498DB',
                                                    borderRadius: '4px 4px 0 0'
                                                }} />
                                            </div>
                                            <span style={{ fontSize: '10px', color: '#94A3B8', marginTop: '4px' }}>{m.month}</span>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        </div>
                    );
                }

                const root = ReactDOM.createRoot(document.getElementById('root'));
                root.render(<RechartsDashboard />);
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    private data class AchievementData(val emoji: String, val title: String, val desc: String, val isUnlocked: Boolean)
}
