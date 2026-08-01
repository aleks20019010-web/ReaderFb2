package com.nightread.app.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.nightread.app.data.SettingsManager

/**
 * Базовый класс для всех Activity, который автоматически устанавливает звездный фон
 * и не дает экрану блокироваться в течение 10 минут после последнего взаимодействия.
 */
abstract class BaseActivity : AppCompatActivity() {

    private var currentLanguage: String? = null
    private var currentNightMode: Boolean = false
    private val screenKeepAwakeHandler = Handler(Looper.getMainLooper())
    private val clearKeepScreenOnRunnable = Runnable {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(SettingsManager.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentLanguage = SettingsManager.getLanguage(this)
        currentNightMode = com.nightread.app.data.ThemeManager.shouldBeNightMode(this)
        // Устанавливаем звездный фон на уровне окна
        window.setBackgroundDrawable(StarryNightDrawable())
        if (currentNightMode) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
        } else {
            window.statusBarColor = android.graphics.Color.parseColor(GalaxyBgHelper.LIGHT_BG_COLOR)
        }
    }

    override fun onResume() {
        super.onResume()
        resetScreenKeepAwakeTimer()
        findViewById<android.view.View>(android.R.id.content)?.let {
            GalaxyBgHelper.applyBackground(it)
        }
        val lang = SettingsManager.getLanguage(this)
        if (lang != currentLanguage) {
            currentLanguage = lang
            recreate()
        }
        val nightMode = com.nightread.app.data.ThemeManager.shouldBeNightMode(this)
        if (nightMode != currentNightMode) {
            currentNightMode = nightMode
            recreate()
        }
    }

    override fun onPause() {
        super.onPause()
        screenKeepAwakeHandler.removeCallbacks(clearKeepScreenOnRunnable)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        resetScreenKeepAwakeTimer()
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Продлевает активное состояние экрана еще на 10 минут при каждом взаимодействии с приложением.
     */
    fun resetScreenKeepAwakeTimer() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        screenKeepAwakeHandler.removeCallbacks(clearKeepScreenOnRunnable)
        // 10 минут = 10 * 60 * 1000L = 600000L мс
        screenKeepAwakeHandler.postDelayed(clearKeepScreenOnRunnable, 10 * 60 * 1000L)
    }
}
