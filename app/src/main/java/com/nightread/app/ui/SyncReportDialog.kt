package com.nightread.app.ui

import android.content.Context
import android.graphics.Typeface
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import com.nightread.app.R
import com.nightread.app.data.SyncReport
import java.io.File

/**
 * Кастомный диалог отчета синхронизации.
 * Отображает статистику файлов, списки книг для скачивания/загрузки и кнопку подтверждения.
 */
class SyncReportDialog(
    private val context: Context,
    private val report: SyncReport,
    private val onConfirm: () -> Unit
) {

    private var dialog: AlertDialog? = null

    fun show() {
        val density = context.resources.displayMetrics.density
        val padding16 = (16 * density).toInt()
        val padding8 = (8 * density).toInt()
        val padding4 = (4 * density).toInt()

        // Главный контейнер
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding16, padding16, padding16, padding16)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        // Заголовок
        val txtTitle = TextView(context).apply {
            text = "Сводка синхронизации"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(context.resources.getColor(R.color.accent, null))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, padding16)
        }
        rootLayout.addView(txtTitle)

        // Скроллер для содержимого
        val scrollView = NestedScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Статистика (Карточки / блоки)
        val statsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding8, padding8, padding8, padding8)
            background = context.resources.getDrawable(R.drawable.bg_forest_gradient, null) // Используем красивый фон или цвет
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = padding16
            }
            layoutParams = params
        }

        // Внутренний контейнер для карточки с прозрачным фоном
        val innerStatsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding16, padding16, padding16, padding16)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        val stats = listOf(
            "📂 Книг на Диске:" to "${report.booksOnDisk}",
            "📖 Книг в Библиотеке:" to "${report.booksLocal}",
            "🔄 Уже синхронизировано:" to "${report.duplicatesCount} (пропущены)",
            "⬇️ Будет скачано:" to "${report.toDownload.size}",
            "⬆️ Будет загружено:" to "${report.toUpload.size}"
        )

        for ((label, value) in stats) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, padding4, 0, padding4)
            }
            val txtLabel = TextView(context).apply {
                text = label
                textSize = 14f
                setTextColor(context.resources.getColor(R.color.text_secondary, null))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val txtValue = TextView(context).apply {
                text = value
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(context.resources.getColor(R.color.text_primary, null))
                gravity = Gravity.END
            }
            row.addView(txtLabel)
            row.addView(txtValue)
            innerStatsLayout.addView(row)
        }
        statsContainer.addView(innerStatsLayout)
        contentLayout.addView(statsContainer)

        // Книги для скачивания (Список)
        if (report.toDownload.isNotEmpty()) {
            val sectionTitle = TextView(context).apply {
                text = "⬇️ Будут скачаны с диска (${report.toDownload.size}):"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(context.resources.getColor(R.color.accent, null))
                setPadding(0, padding8, 0, padding8)
            }
            contentLayout.addView(sectionTitle)

            val downloadListLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding8, padding4, padding8, padding8)
                setBackgroundColor(context.resources.getColor(R.color.bg_panel, null))
            }

            // Покажем первые 5 книг, остальные спрячем с пометкой
            val displayCount = minOf(5, report.toDownload.size)
            for (i in 0 until displayCount) {
                val cloudItem = report.toDownload[i]
                val fileName = File(cloudItem.path).name
                val sizeStr = Formatter.formatFileSize(context, cloudItem.size)
                
                val txtItem = TextView(context).apply {
                    text = "• $fileName ($sizeStr)"
                    textSize = 13f
                    setTextColor(context.resources.getColor(R.color.text_primary, null))
                    setPadding(0, padding4, 0, padding4)
                }
                downloadListLayout.addView(txtItem)
            }

            if (report.toDownload.size > 5) {
                val txtMore = TextView(context).apply {
                    text = "... и еще ${report.toDownload.size - 5} книг(и)"
                    textSize = 13f
                    setTypeface(null, Typeface.ITALIC)
                    setTextColor(context.resources.getColor(R.color.text_secondary, null))
                    setPadding(0, padding4, 0, padding4)
                }
                downloadListLayout.addView(txtMore)
            }
            contentLayout.addView(downloadListLayout)
        }

        // Книги для загрузки (Список)
        if (report.toUpload.isNotEmpty()) {
            val sectionTitle = TextView(context).apply {
                text = "⬆️ Будут загружены в облако (${report.toUpload.size}):"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(context.resources.getColor(R.color.accent, null))
                setPadding(0, padding16, 0, padding8)
            }
            contentLayout.addView(sectionTitle)

            val uploadListLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding8, padding4, padding8, padding8)
                setBackgroundColor(context.resources.getColor(R.color.bg_panel, null))
            }

            // Покажем первые 5 книг, остальные спрячем с пометкой
            val displayCount = minOf(5, report.toUpload.size)
            for (i in 0 until displayCount) {
                val book = report.toUpload[i]
                val authorStr = if (book.author.isNullOrEmpty() || book.author == "Неизвестен") "" else " — ${book.author}"
                val sizeStr = if (book.fileSize > 0) " (${Formatter.formatFileSize(context, book.fileSize)})" else ""
                
                val txtItem = TextView(context).apply {
                    text = "• ${book.title}$authorStr$sizeStr"
                    textSize = 13f
                    setTextColor(context.resources.getColor(R.color.text_primary, null))
                    setPadding(0, padding4, 0, padding4)
                }
                uploadListLayout.addView(txtItem)
            }

            if (report.toUpload.size > 5) {
                val txtMore = TextView(context).apply {
                    text = "... и еще ${report.toUpload.size - 5} книг(и)"
                    textSize = 13f
                    setTypeface(null, Typeface.ITALIC)
                    setTextColor(context.resources.getColor(R.color.text_secondary, null))
                    setPadding(0, padding4, 0, padding4)
                }
                uploadListLayout.addView(txtMore)
            }
            contentLayout.addView(uploadListLayout)
        }

        // Пустое состояние (синхронизировать нечего)
        if (report.toDownload.isEmpty() && report.toUpload.isEmpty()) {
            val txtEmpty = TextView(context).apply {
                text = "🎉 Все ваши книги синхронизированы! Локальная библиотека и папка на Яндекс Диске полностью идентичны."
                textSize = 14f
                setTypeface(null, Typeface.ITALIC)
                setTextColor(context.resources.getColor(R.color.text_primary, null))
                gravity = Gravity.CENTER
                setPadding(padding16, padding16, padding16, padding16)
            }
            contentLayout.addView(txtEmpty)
        }

        scrollView.addView(contentLayout)
        rootLayout.addView(scrollView)

        // Контейнер кнопок действия
        val buttonsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, padding16, 0, 0)
        }

        val btnCancel = Button(context, null, 0, com.google.android.material.R.style.Widget_Material3_Button_TextButton).apply {
            text = "Отмена"
            setTextColor(context.resources.getColor(R.color.text_secondary, null))
            setOnClickListener {
                dialog?.dismiss()
            }
        }

        val btnStart = Button(context, null, 0, com.google.android.material.R.style.Widget_Material3_Button).apply {
            text = if (report.toDownload.isEmpty() && report.toUpload.isEmpty()) "Понятно" else "Начать"
            setBackgroundColor(context.resources.getColor(R.color.accent, null))
            setTextColor(context.resources.getColor(R.color.btn_text, null))
            
            // Зададим rounded corners кнопке
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = padding8
            }
            layoutParams = params

            setOnClickListener {
                dialog?.dismiss()
                if (report.toDownload.isNotEmpty() || report.toUpload.isNotEmpty()) {
                    onConfirm()
                }
            }
        }

        buttonsLayout.addView(btnCancel)
        buttonsLayout.addView(btnStart)
        rootLayout.addView(buttonsLayout)

        dialog = AlertDialog.Builder(context)
            .setView(rootLayout)
            .setCancelable(true)
            .create()

        dialog?.show()
        dialog?.applyStarryBackground()
        dialog?.window?.apply {
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
}
