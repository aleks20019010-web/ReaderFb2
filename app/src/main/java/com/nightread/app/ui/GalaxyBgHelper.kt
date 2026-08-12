package com.nightread.app.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.nightread.app.R
import com.nightread.app.data.FileStorageHelper

/**
 * Класс, отвечающий за загрузку и отображение фона приложения (layout_galaxy_bg.xml).
 * Если пользователь выбрал собственную картинку (сохранённую в /files/user_bg.jpg), отображает её.
 * При отсутствии пользовательской картинки загружает:
 * - Для тёмной темы: тёмный космический фон (bg_dark_cosmic / #1A0B2E).
 * - Для светлой темы: светлый градиент "рассвет" (light_bg / #F5F0EB).
 */
object GalaxyBgHelper {

    private const val TAG = "GalaxyBgHelper"
    const val DARK_BG_COLOR = "#1A0B2E"
    const val LIGHT_BG_COLOR = "#F5F0EB"

    /**
     * Ищет ImageView с ID R.id.ivCustomLibraryBg в предоставленном rootView и применяет к нему фон.
     */
    fun applyBackground(rootView: View) {
        val ivBg = rootView.findViewById<ImageView>(R.id.ivCustomLibraryBg)
        if (ivBg != null) {
            applyBackground(rootView.context, ivBg)
        }
    }

    /**
     * Применяет пользовательский фон или фоновое изображение в зависимости от выбранной темы.
     */
    fun applyBackground(context: Context, imageView: ImageView) {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isNightMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES || com.nightread.app.data.ThemeHelper.shouldBeNightMode(context)

        val parentView = imageView.parent as? android.view.ViewGroup
        val starryView = parentView?.findViewById<View>(R.id.starryOverlay)
        val sunbeamView = parentView?.findViewById<View>(R.id.sunbeamOverlay)

        (starryView as? StarryNightView)?.transparentBackground = true
        (sunbeamView as? SunbeamParticlesView)?.transparentBackground = true

        val bgFile = FileStorageHelper.getUserBackgroundFile(context, isNightMode)

        // Helper to update overlay visibility
        fun updateOverlayVisibility() {
            if (isNightMode) {
                starryView?.visibility = View.VISIBLE
                sunbeamView?.visibility = View.GONE
            } else {
                starryView?.visibility = View.GONE
                sunbeamView?.visibility = View.VISIBLE
            }
        }

        // 1. Приоритет: пользовательское фоновое изображение из галереи (user_bg_dark.jpg / user_bg_light.jpg)
        if (bgFile.exists() && bgFile.length() > 0) {
            try {
                val bitmap = BitmapFactory.decodeFile(bgFile.absolutePath)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    imageView.setBackgroundColor(Color.TRANSPARENT)
                    updateOverlayVisibility()
                    return
                } else {
                    Log.e(TAG, "BitmapFactory decoded null bitmap for background file: ${bgFile.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding custom background file, falling back to theme background", e)
            }
        }

        // 2. Тематический фон по умолчанию (Тёмный космос, Дали или Светлый рассвет)
        imageView.setImageDrawable(null)
        val appTheme = com.nightread.app.data.SettingsManager.getTheme(context)
        if (isNightMode) {
            val darkDrawable = ContextCompat.getDrawable(context, R.drawable.bg_dark_cosmic)
            if (darkDrawable != null) {
                imageView.setImageDrawable(darkDrawable)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                imageView.setBackgroundColor(Color.parseColor(DARK_BG_COLOR))
            }
        } else if (appTheme == "dali") {
            val daliDrawable = ContextCompat.getDrawable(context, R.drawable.bg_dali_desert)
            if (daliDrawable != null) {
                imageView.setImageDrawable(daliDrawable)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                imageView.setBackgroundColor(Color.parseColor("#F4E8D1"))
            }
            sunbeamView?.visibility = View.VISIBLE
            starryView?.visibility = View.GONE
            return
        } else {
            val lightDrawable = ContextCompat.getDrawable(context, R.drawable.light_bg)
            if (lightDrawable != null) {
                imageView.setImageDrawable(lightDrawable)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                imageView.setBackgroundColor(Color.parseColor(LIGHT_BG_COLOR))
            }
        }
        updateOverlayVisibility()
    }
}
