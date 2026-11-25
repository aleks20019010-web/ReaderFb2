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

object GalaxyBgHelper {

    private const val TAG = "GalaxyBgHelper"
    const val DARK_BG_COLOR = "#000000"
    const val LIGHT_BG_COLOR = "#F4F1EA"

    fun applyBackground(rootView: View) {
        val ivBg = rootView.findViewById<ImageView>(R.id.ivCustomLibraryBg)
        if (ivBg != null) {
            applyBackground(rootView.context, ivBg)
        }
    }

    fun applyBackground(context: Context, imageView: ImageView) {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isNightMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES ||
            com.nightread.app.data.ThemeHelper.shouldBeNightMode(context)

        val parentView = imageView.parent as? android.view.ViewGroup
        val liquidWaveView = parentView?.findViewById<LiquidWaveBackgroundView>(R.id.liquidWaveBg)
        val starryView = parentView?.findViewById<View>(R.id.starryOverlay)
        val sunbeamView = parentView?.findViewById<View>(R.id.sunbeamOverlay)

        (starryView as? StarryNightView)?.transparentBackground = true
        (sunbeamView as? SunbeamParticlesView)?.transparentBackground = true

        val bgFile = FileStorageHelper.getUserBackgroundFile(context, isNightMode)

        // 1. Пользовательское фото из галереи (если выбрано вручную)
        if (bgFile.exists() && bgFile.length() > 0) {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(bgFile.absolutePath, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    val dm = context.resources.displayMetrics
                    var inSampleSize = 1
                    while (options.outHeight / inSampleSize >= dm.heightPixels &&
                        options.outWidth / inSampleSize >= dm.widthPixels
                    ) {
                        inSampleSize *= 2
                    }
                    options.inJustDecodeBounds = false
                    options.inSampleSize = inSampleSize
                    options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                    val bitmap = BitmapFactory.decodeFile(bgFile.absolutePath, options)
                    if (bitmap != null) {
                        liquidWaveView?.visibility = View.GONE
                        imageView.visibility = View.VISIBLE
                        imageView.setImageBitmap(bitmap)
                        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                        imageView.setBackgroundColor(Color.TRANSPARENT)
                        starryView?.visibility = if (isNightMode) View.VISIBLE else View.GONE
                        sunbeamView?.visibility = if (isNightMode) View.GONE else View.VISIBLE
                        return
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error decoding custom background file", e)
            }
        }

        // 2. Специальная тема «Дали» (если выбрана в светлом режиме)
        val appTheme = com.nightread.app.data.SettingsManager.getTheme(context)
        if (!isNightMode && appTheme == "dali") {
            liquidWaveView?.visibility = View.GONE
            imageView.visibility = View.VISIBLE
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
        }

        // 3. Основной живой переливающийся AGSL-фон (по умолчанию на всех экранах)
        imageView.setImageDrawable(null)
        imageView.visibility = View.GONE
        starryView?.visibility = View.GONE
        sunbeamView?.visibility = View.GONE

        liquidWaveView?.isLightTheme = !isNightMode
        liquidWaveView?.visibility = View.VISIBLE
    }
}