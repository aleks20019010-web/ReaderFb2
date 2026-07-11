package com.nightread.app.ui

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

/**
 * Базовый класс для всех Activity, который автоматически устанавливает звездный фон.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Устанавливаем звездный фон на уровне окна
        window.setBackgroundDrawable(StarryNightDrawable())
    }
}
