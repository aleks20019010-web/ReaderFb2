package com.nightread.app.ui

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.nightread.app.data.SettingsManager

/**
 * Базовый класс для всех Activity, который автоматически устанавливает звездный фон.
 */
abstract class BaseActivity : AppCompatActivity() {

    private var currentLanguage: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(SettingsManager.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentLanguage = SettingsManager.getLanguage(this)
        // Устанавливаем звездный фон на уровне окна
        window.setBackgroundDrawable(StarryNightDrawable())
    }

    override fun onResume() {
        super.onResume()
        val lang = SettingsManager.getLanguage(this)
        if (lang != currentLanguage) {
            currentLanguage = lang
            recreate()
        }
    }
}
