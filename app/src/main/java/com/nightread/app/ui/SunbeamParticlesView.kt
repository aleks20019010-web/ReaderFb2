package com.nightread.app.ui

import android.content.Context
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.Random

/**
 * Анимированный слой «Солнечные пылинки» (Sunbeam Particles) для светлой темы.
 * Служит антагонистом ночному звёздному небу (StarryNightView).
 *
 * Особенности:
 * 1. Мелкие, полупрозрачные золотистые и кремовые частицы (радиус 1–4 dp).
 * 2. Медленное естественное парение пылинок в лучах света с плавным появлением и угасанием (fade in / fade out).
 * 3. Параллакс-эффект при наклоне устройства (гироскоп / акселерометр).
 * 4. Реакция на сдвиг боковой шторки (drawer slide offset).
 */
class SunbeamParticlesView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random()
    private val particles = mutableListOf<SunbeamParticle>()

    private var lastW = 0
    private var lastH = 0
    private var animating = false

    // Сенсоры гироскопа и фильтрация наклонов для параллакса
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null

    private var targetTiltX = 0f
    private var targetTiltY = 0f
    private var currentTiltX = 0f
    private var currentTiltY = 0f

    // Смещение при открытии шторки
    private var drawerSlideOffset = 0f

    /**
     * Если true (по умолчанию), фон не закрашивается градиентом, сохраняя подложенный рисунок/светлый фон.
     */
    var transparentBackground: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    // Структура солнечной пылинки
    private data class SunbeamParticle(
        var x: Float,
        var y: Float,
        val radius: Float,           // Радиус частицы (в px, ~1-4dp)
        val speedX: Float,           // Горизонтальный дрейф (-0.3f .. 0.3f)
        val speedY: Float,           // Вертикальный дрейф (-0.5f .. -0.1f)
        val baseAlpha: Int,          // Базовая прозрачность (50..150 -> 20%-60%)
        val color: Int,              // Тёплый золотой / кремовый цвет
        val depth: Float,            // Параллакс-глубина (0.2 - 1.0)
        val pulseSpeed: Float,       // Скорость мерцания / пульсации прозрачности
        var pulsePhase: Float        // Текущая фаза пульсации
    )

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    targetTiltX = event.values[1] * 100f
                    targetTiltY = event.values[0] * 100f
                }
                Sensor.TYPE_GYROSCOPE -> {
                    targetTiltX = (targetTiltX + event.values[1] * 2f).coerceIn(-80f, 80f)
                    targetTiltY = (targetTiltY + event.values[0] * 2f).coerceIn(-80f, 80f)
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    targetTiltX = -event.values[0] * 8f
                    targetTiltY = (event.values[1] - 5f) * 8f
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    init {
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        } catch (e: Exception) {
            // Игнорируем отсутствие датчиков
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animating = true
        rotationSensor?.let {
            sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animating = false
        try {
            sensorManager?.unregisterListener(sensorListener)
        } catch (e: Exception) {}
    }

    fun setDrawerSlideOffset(offset: Float) {
        this.drawerSlideOffset = offset
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (transparentBackground) {
            return false
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        try {
            val w = width
            val h = height
            if (w <= 0 || h <= 0) return

            if (w != lastW || h != lastH) {
                generateParticles(w, h)
                lastW = w
                lastH = h
            }

            // 1. Отрисовка фона (только если прозрачность выключена)
            if (!transparentBackground) {
                val gradient = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    Color.parseColor("#FDFBF7"),
                    Color.parseColor("#F5F0EB"),
                    Shader.TileMode.CLAMP
                )
                bgPaint.shader = gradient
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
            }

            // 2. Плавный инерционный расчет смещения параллакса
            val lerpFactor = 0.08f
            currentTiltX += lerpFactor * (targetTiltX - currentTiltX)
            currentTiltY += lerpFactor * (targetTiltY - currentTiltY)

            // 3. Отрисовка и обновление частичек солнечной пыли
            for (p in particles) {
                // Плавная пульсация прозрачности (fade in / fade out по синусу)
                p.pulsePhase += p.pulseSpeed
                val alphaFactor = 0.3f + 0.7f * (0.5f + 0.5f * Math.sin(p.pulsePhase.toDouble()).toFloat())
                val currentAlpha = (p.baseAlpha * alphaFactor).toInt().coerceIn(15, 255)

                // Дрейф частицы в воздухе
                p.x += p.speedX
                p.y += p.speedY

                // Закольцовывание при выходе за границы экрана
                if (p.x < -30f) p.x += w + 60f
                else if (p.x > w + 30f) p.x -= w + 60f

                if (p.y < -30f) p.y += h + 60f
                else if (p.y > h + 30f) p.y -= h + 60f

                // Параллакс-смещение с учетом глубины частицы и шторки меню
                val xOffset = currentTiltX * p.depth - (drawerSlideOffset * w * 0.25f * p.depth)
                val yOffset = currentTiltY * p.depth

                var drawX = p.x + xOffset
                var drawY = p.y + yOffset

                if (drawX < 0) drawX += w
                else if (drawX > w) drawX -= w

                if (drawY < 0) drawY += h
                else if (drawY > h) drawY -= h

                // Отрисовка размытого ореола (свечения) для крупных пылинок
                if (p.radius > 2.5f * density()) {
                    glowPaint.color = p.color
                    glowPaint.alpha = (currentAlpha * 0.35f).toInt()
                    canvas.drawCircle(drawX, drawY, p.radius * 2.2f, glowPaint)
                }

                // Отрисовка самого тела частицы
                particlePaint.color = p.color
                particlePaint.alpha = currentAlpha
                canvas.drawCircle(drawX, drawY, p.radius, particlePaint)
            }

            if (animating) {
                postInvalidateOnAnimation()
            }
        } catch (e: Exception) {
            // Безопасный фоллбэк при ошибках отрисовки
        }
    }

    private fun density(): Float {
        return context.resources.displayMetrics.density
    }

    private fun generateParticles(w: Int, h: Int) {
        particles.clear()
        val density = density()
        val count = 90 // Оптимальный баланс визуальной красоты и производительности

        // Палитра теплых солнечных и кремовых оттенков (20-60% прозрачность в макете)
        val colors = intArrayOf(
            Color.parseColor("#F5D76E"), // Золотистый солнечный
            Color.parseColor("#FFD700"), // Чистое золото
            Color.parseColor("#FFE3A8"), // Кремово-золотой
            Color.parseColor("#F4D03F"), // Теплый амбер
            Color.parseColor("#E6C280")  // Мягкий беж
        )

        for (i in 0 until count) {
            val x = random.nextFloat() * w
            val y = random.nextFloat() * h
            // Радиус от 1dp до 4dp (переведенный в пиксели)
            val radiusDp = 1.0f + random.nextFloat() * 3.0f
            val radiusPx = radiusDp * density

            // Скорость парения пыли в солнечных лучах (вверх/наискосок)
            val speedX = (random.nextFloat() - 0.48f) * 0.5f * density
            val speedY = (-0.15f - random.nextFloat() * 0.45f) * density

            // Базовая прозрачность от 50 до 150 (20% - 60% от 255)
            val baseAlpha = 50 + random.nextInt(101)
            val color = colors[random.nextInt(colors.size)]
            val depth = 0.2f + random.nextFloat() * 0.8f
            val pulseSpeed = 0.015f + random.nextFloat() * 0.035f
            val pulsePhase = random.nextFloat() * (Math.PI.toFloat() * 2f)

            particles.add(
                SunbeamParticle(
                    x = x,
                    y = y,
                    radius = radiusPx,
                    speedX = speedX,
                    speedY = speedY,
                    baseAlpha = baseAlpha,
                    color = color,
                    depth = depth,
                    pulseSpeed = pulseSpeed,
                    pulsePhase = pulsePhase
                )
            )
        }
    }
}
