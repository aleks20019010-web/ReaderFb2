package com.nightread.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.nightread.app.R
import com.nightread.app.data.SettingsManager

/**
 * Helper class implementing the Salvador Dalí Surrealism design system:
 * - Fluid, asymmetric shapes & glassmorphism
 * - Palette: Warm parchment (#F4E8D1), Melted gold (#E6A100), Crimson (#C53030), Emerald (#2F855A), Azure (#3182CE), Ink navy (#1A1829)
 * - Surreal distortions, melting effects, and status drop indicators.
 */
object DaliThemeHelper {

    const val COLOR_PARCHMENT = "#F4E8D1"
    const val COLOR_GOLD = "#E6A100"
    const val COLOR_CRIMSON = "#C53030"
    const val COLOR_EMERALD = "#2F855A"
    const val COLOR_AZURE = "#3182CE"
    const val COLOR_INK = "#1A1829"

    fun isDaliActive(context: Context): Boolean {
        return SettingsManager.getTheme(context) == "dali"
    }

    /**
     * Applies Dalí surrealism styling to a book card view (asymmetric corners, glassmorphism, melting border).
     */
    fun styleBookCard(cardView: MaterialCardView, isDali: Boolean) {
        if (isDali) {
            cardView.setCardBackgroundColor(Color.parseColor("#EEDDB8"))
            cardView.strokeColor = Color.parseColor("#E6A100")
            cardView.strokeWidth = 2
            // Asymmetric corners: Top-Left 24dp, Top-Right 8dp, Bottom-Left 6dp, Bottom-Right 20dp
            cardView.shapeAppearanceModel = cardView.shapeAppearanceModel.toBuilder()
                .setTopLeftCornerSize(28f)
                .setTopRightCornerSize(10f)
                .setBottomLeftCornerSize(8f)
                .setBottomRightCornerSize(24f)
                .build()
            cardView.cardElevation = 6f
        } else {
            // Default styling handled by theme
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
     * Returns color for book status drop indicator:
     * - Reading ("читаю") -> Melted Gold (#E6A100)
     * - Read ("прочитано") -> Emerald (#2F855A)
     * - Want to read ("хочу прочитать") -> Azure (#3182CE)
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
     * Applies Dalí surrealism background or tint to root layouts.
     */
    fun applyDaliBackground(rootView: View) {
        val context = rootView.context
        if (isDaliActive(context)) {
            rootView.setBackgroundColor(Color.parseColor(COLOR_PARCHMENT))
            GalaxyBgHelper.applyBackground(rootView)
        }
    }
}
