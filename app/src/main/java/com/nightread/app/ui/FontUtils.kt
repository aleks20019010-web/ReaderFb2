package com.nightread.app.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import com.nightread.app.data.SettingsManager

object FontUtils {
    fun createTypeface(family: String, numericWeight: Int): Typeface {
        val baseTypeface = when (family) {
            "Roboto" -> Typeface.SANS_SERIF
            "Times New Roman", "Georgia", "Merriweather" -> Typeface.create("serif", Typeface.NORMAL)
            "OpenDyslexic" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            "Monospace" -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(baseTypeface, numericWeight, false)
        } else {
            val style = if (numericWeight >= 600) Typeface.BOLD else Typeface.NORMAL
            Typeface.create(baseTypeface, style)
        }
    }

    /**
     * Создает шрифт с оптическим балансом и динамической адаптацией веса (Variable Typography).
     * 1. На темной теме буквы визуально "расплываются" (эффект ореола) и кажутся толще. Мы уменьшаем вес.
     * 2. В темноте (низкий lux) снижаем вес для более мягкого свечения букв.
     * 3. На ярком солнце (высокий lux) увеличиваем вес для лучшей контрастности и читаемости.
     */
    fun createTypefaceWithOpticalBalance(context: Context, family: String, numericWeight: Int): Typeface {
        val themeName = SettingsManager.getReadingTheme(context)
        val lux = SettingsManager.getAmbientLux()
        
        var adjustedWeight = numericWeight
        
        // 1. Компенсация "растекания" букв (Иррадиация) на темных фонах
        if (themeName == "dark" || themeName == "contrast") {
            adjustedWeight -= 35 // На темной теме делаем текст тоньше
        } else if (themeName == "sepia" || themeName == "sepia_contrast" || themeName == "beige") {
            adjustedWeight -= 10 // Мягкая адаптация для средних контрастов
        }
        
        // 2. Тонкая подстройка под уровень освещения (Ambient Light)
        if (lux < 10f) {
            adjustedWeight -= 20 // В темноте делаем еще мягче и тоньше
        } else if (lux > 250f) {
            adjustedWeight += 35 // На солнце уплотняем шрифт
        }
        
        // Ограничиваем вес допустимыми рамками в Android [100..1000]
        val finalWeight = adjustedWeight.coerceIn(100, 1000)
        
        return createTypeface(family, finalWeight)
    }
}
