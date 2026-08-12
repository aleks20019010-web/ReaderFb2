package com.nightread.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.nightread.app.R
import com.nightread.app.data.SettingsManager

/**
 * Helper class implementing the Salvador Dalí Surrealism design system:
 * - Fluid, asymmetric shapes & glassmorphism
 * - Palette: Warm parchment (#F4E8D1), Melted gold (#E6A100), Crimson (#C53030), Emerald (#2F855A), Azure (#3182CE), Ink navy (#1A1829)
 * - Surreal distortions, melting effects, parchment scrolls, top melting clocks, and status drop indicators.
 */
object DaliThemeHelper {

    const val COLOR_PARCHMENT = "#F4E8D1"
    const val COLOR_GOLD = "#E6A100"
    const val COLOR_CRIMSON = "#C53030"
    const val COLOR_EMERALD = "#2F855A"
    const val COLOR_AZURE = "#3182CE"
    const val COLOR_INK = "#2C1E12"

    fun isDaliActive(context: Context): Boolean {
        val theme = SettingsManager.getTheme(context)
        if (theme == "dali") return true
        if (theme == "light") return true
        if (!com.nightread.app.data.ThemeHelper.shouldBeNightMode(context) && theme != "dark") return true
        return false
    }

    /**
     * Styles the library header bar to match the surreal Dali parchment scroll & eye/sun/moon icons.
     */
    fun styleLibraryHeader(
        context: Context,
        headerCard: MaterialCardView?,
        ivTopClock: ImageView?,
        tvTitle: TextView?,
        tvBookCount: TextView?,
        btnMenu: View?,
        btnSearchToggle: View?,
        btnSort: View?,
        btnToggleTheme: View?
    ) {
        val active = isDaliActive(context)
        if (active) {
            ivTopClock?.visibility = View.VISIBLE
            ivTopClock?.setImageResource(R.drawable.ic_dali_melting_clock_top)

            if (headerCard != null) {
                headerCard.background = ContextCompat.getDrawable(context, R.drawable.bg_dali_header_scroll)
                headerCard.setCardBackgroundColor(Color.TRANSPARENT)
                headerCard.strokeWidth = 0
                headerCard.cardElevation = 6f
            }

            tvTitle?.setTextColor(Color.parseColor("#2C1E12"))
            tvBookCount?.setTextColor(Color.parseColor("#6B4A2B"))

            if (btnMenu is ImageButton) {
                btnMenu.setImageResource(R.drawable.ic_dali_menu)
                btnMenu.imageTintList = null
            }

            (btnSearchToggle as? MaterialButton)?.let {
                it.setIconResource(R.drawable.ic_dali_eye_search)
                it.iconTint = null
            }

            (btnSort as? MaterialButton)?.let {
                it.setIconResource(R.drawable.ic_dali_gear)
                it.iconTint = null
            }

            (btnToggleTheme as? MaterialButton)?.let {
                it.setIconResource(R.drawable.ic_dali_sun_moon)
                it.iconTint = null
            }
        } else {
            ivTopClock?.visibility = View.GONE
        }
    }

    /**
     * Styles a book grid item (cover frame + parchment scroll label + ink text).
     */
    fun styleGridItem(
        outerCard: MaterialCardView,
        cvBookCover: MaterialCardView,
        textContainer: View,
        tvTitle: TextView,
        tvAuthor: TextView,
        tvSeries: TextView?
    ) {
        val context = outerCard.context
        if (isDaliActive(context)) {
            outerCard.setCardBackgroundColor(Color.parseColor("#251433"))
            outerCard.strokeColor = Color.parseColor("#8B6508")
            outerCard.strokeWidth = 2

            cvBookCover.setCardBackgroundColor(Color.parseColor("#150D20"))
            cvBookCover.strokeColor = Color.parseColor("#D4AF37")
            cvBookCover.strokeWidth = 2
            cvBookCover.radius = 10f * context.resources.displayMetrics.density

            textContainer.background = ContextCompat.getDrawable(context, R.drawable.bg_dali_book_scroll)
            tvTitle.setTextColor(Color.parseColor("#2C1E12"))
            tvAuthor.setTextColor(Color.parseColor("#5C3A21"))
            tvSeries?.setTextColor(Color.parseColor("#7A4B24"))
        }
    }

    /**
     * Applies Dalí surrealism styling to a book card view.
     */
    fun styleBookCard(cardView: MaterialCardView, isDali: Boolean) {
        if (isDali) {
            cardView.setCardBackgroundColor(Color.parseColor("#EEDDB8"))
            cardView.strokeColor = Color.parseColor("#E6A100")
            cardView.strokeWidth = 2
            cardView.shapeAppearanceModel = cardView.shapeAppearanceModel.toBuilder()
                .setTopLeftCornerSize(28f)
                .setTopRightCornerSize(10f)
                .setBottomLeftCornerSize(8f)
                .setBottomRightCornerSize(24f)
                .build()
            cardView.cardElevation = 6f
        }
    }

    /**
     * Applies Dalí surrealist cover frame and melting paint effect simulation.
     */
    fun styleBookCover(coverView: View, isDali: Boolean) {
        if (isDali) {
            coverView.background = GradientDrawable().apply {
                setColor(Color.parseColor("#D4AF37"))
                cornerRadius = 16f
            }
        }
    }

    /**
     * Returns color for book status drop indicator.
     */
    fun getStatusDropColor(status: String?): Int {
        return when (status?.lowercase()) {
            "reading", "читаю" -> Color.parseColor(COLOR_GOLD)
            "read", "прочитано", "completed" -> Color.parseColor(COLOR_EMERALD)
            "want", "want_to_read", "хочу прочитать" -> Color.parseColor(COLOR_AZURE)
            else -> Color.parseColor(COLOR_CRIMSON)
        }
    }

    /**
     * Applies Dalí surrealism background to root layouts.
     */
    fun applyDaliBackground(rootView: View) {
        val context = rootView.context
        if (isDaliActive(context)) {
            rootView.setBackgroundColor(Color.parseColor(COLOR_PARCHMENT))
            GalaxyBgHelper.applyBackground(rootView)
        }
    }

    /**
     * Styles the Book Detail screen to match the surreal Salvador Dalí screenshot.
     */
    fun styleBookDetail(
        context: Context,
        toolbar: androidx.appcompat.widget.Toolbar?,
        btnReadToolbar: TextView?,
        contentContainer: View?,
        coverContainer: View?,
        tvTitle: TextView?,
        tvAuthor: TextView?,
        tvSeries: TextView?,
        annotationCard: View?,
        tvAnnotation: TextView?,
        tvAnnotationHeader: TextView?
    ) {
        if (!isDaliActive(context)) return

        toolbar?.setNavigationIcon(R.drawable.ic_dali_snake_back)

        btnReadToolbar?.apply {
            setBackgroundResource(R.drawable.ic_dali_read_slab)
            setTextColor(Color.parseColor("#3A2510"))
            text = "ЧИТАТЬ"
            setPadding(16, 4, 16, 4)
        }

        contentContainer?.setBackgroundResource(R.drawable.bg_dali_book_scroll)

        coverContainer?.let {
            it.background = ContextCompat.getDrawable(context, R.drawable.bg_card_glass)
            it.setPadding(16, 16, 16, 16)
        }

        tvTitle?.setTextColor(Color.parseColor("#2C1E12"))
        tvAuthor?.setTextColor(Color.parseColor("#5C3A21"))
        tvSeries?.setTextColor(Color.parseColor("#7A4B24"))

        annotationCard?.background = ContextCompat.getDrawable(context, R.drawable.bg_dali_annotation_watch)
        tvAnnotationHeader?.setTextColor(Color.parseColor("#5C3A21"))
        tvAnnotation?.setTextColor(Color.parseColor("#2C1E12"))
    }
}
