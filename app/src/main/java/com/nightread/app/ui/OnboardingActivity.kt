package com.nightread.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.nightread.app.MainActivity
import com.nightread.app.R
import com.nightread.app.data.SettingsManager

class OnboardingActivity : BaseActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnNext: MaterialButton
    private lateinit var indicatorLayout: LinearLayout

    private val onboardingItems = listOf(
        OnboardingItem(
            "Добро пожаловать в NightRead!",
            "Удобная и минималистичная читалка для ваших любимых книг."
        ),
        OnboardingItem(
            "Библиотека",
            "• Нажмите \"Сканировать\", чтобы автоматически найти книги на устройстве.\n\n• Используйте \"Импорт\" для добавления конкретных файлов.\n\n• Иконка лупы поможет быстро найти нужную книгу."
        ),
        OnboardingItem(
            "Экран чтения",
            "• Тап по центру — показать/скрыть меню.\n\n• Тап в левом верхнем углу — ночная тема.\n\n• Свайп по правому краю — яркость.\n\n• Шестерёнка — настройки текста.\n\n• Кнопки громкости — перелистывание."
        ),
        OnboardingItem(
            "Синхронизация",
            "NightRead умеет сохранять прогресс в облаке.\n\n• Перейдите в раздел \"Синхронизация\", чтобы подключить Яндекс Диск.\n\n• Включите авто-синхронизацию для удобства."
        ),
        OnboardingItem(
            "Готово",
            "Теперь вы готовы читать!\n\nВсе функции разблокированы. Приятного чтения!"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        viewPager = findViewById(R.id.viewPager)
        btnNext = findViewById(R.id.btnNext)
        indicatorLayout = findViewById(R.id.indicatorLayout)

        val adapter = OnboardingAdapter(onboardingItems)
        viewPager.adapter = adapter
        
        // Add a simple fade transition
        viewPager.setPageTransformer(FadePageTransformer())

        setupIndicators()
        setCurrentIndicator(0)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                setCurrentIndicator(position)
                if (position == onboardingItems.size - 1) {
                    btnNext.text = "Начать чтение"
                } else {
                    btnNext.text = "Далее"
                }
            }
        })

        btnNext.setOnClickListener {
            if (viewPager.currentItem + 1 < onboardingItems.size) {
                viewPager.currentItem += 1
            } else {
                completeOnboarding()
            }
        }
    }

    private fun setupIndicators() {
        val indicators = arrayOfNulls<ImageView>(onboardingItems.size)
        val layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(8, 0, 8, 0)

        for (i in indicators.indices) {
            indicators[i] = ImageView(this)
            indicators[i]?.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.indicator_inactive
                )
            )
            indicators[i]?.layoutParams = layoutParams
            indicatorLayout.addView(indicators[i])
        }
    }

    private fun setCurrentIndicator(index: Int) {
        val childCount = indicatorLayout.childCount
        for (i in 0 until childCount) {
            val imageView = indicatorLayout.getChildAt(i) as ImageView
            if (i == index) {
                imageView.setImageDrawable(
                    ContextCompat.getDrawable(
                        this,
                        R.drawable.indicator_active
                    )
                )
            } else {
                imageView.setImageDrawable(
                    ContextCompat.getDrawable(
                        this,
                        R.drawable.indicator_inactive
                    )
                )
            }
        }
    }

    private fun completeOnboarding() {
        SettingsManager.setOnboardingCompleted(applicationContext, true)
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}

data class OnboardingItem(val title: String, val description: String)

class OnboardingAdapter(private val items: List<OnboardingItem>) :
    RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

    inner class OnboardingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        private val tvDescription = view.findViewById<TextView>(R.id.tvDescription)

        fun bind(item: OnboardingItem) {
            tvTitle.text = item.title
            tvDescription.text = item.description
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        return OnboardingViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_onboarding, parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size
}
