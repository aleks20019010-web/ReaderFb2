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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_stats, container, false)

        // Инициализация views
        btnMenu = view.findViewById(R.id.btnMenu)
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
                    ((book.currentProgressChar.toFloat() / book.totalCharacters) * 100).toInt()
                } else {
                    0
                }

                if (percent >= 98 || percent == 100) {
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
                        tvStatus.setTextColor(resources.getColor(R.color.accent, null))
                        achView.alpha = 1.0f
                    } else {
                        tvStatus.text = getString(R.string.stats_ach_locked_status)
                        tvStatus.setTextColor(resources.getColor(R.color.text_secondary, null))
                        achView.alpha = 0.5f // Полупрозрачность для заблокированных
                    }

                    containerAchievements.addView(achView)
                }
            }
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    private data class AchievementData(val emoji: String, val title: String, val desc: String, val isUnlocked: Boolean)
}
