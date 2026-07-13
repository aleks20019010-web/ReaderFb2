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
 * Интерактивный динамический фон «Живое ночное небо» (Parallax Starfield).
 * Особенности:
 * 1. Реалистичный эффект параллакса на основе гироскопа/акселерометра.
 * 2. Многоуровневая глубина звезд (далекие двигаются медленнее, близкие — быстрее).
 * 3. Динамическое независимое мерцание (Twinkle) звезд по синусоидальному закону.
 * 4. Случайное появление падающих звезд (Shooting Stars) с плавным затуханием и градиентным хвостом.
 * 5. Интерактивная реакция на свайпы пальцем и плавное смещение при открытии бокового меню (Drawer).
 */
class StarryNightView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shootingStarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }
    private val random = Random()
    private val stars = mutableListOf<Star>()
    private val shootingStars = mutableListOf<ShootingStar>()

    private var lastW = 0
    private var lastH = 0
    private var animating = false

    // Параметры гироскопа и фильтрации наклонов
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    
    private var targetTiltX = 0f
    private var targetTiltY = 0f
    private var currentTiltX = 0f
    private var currentTiltY = 0f

    // Параметры сенсорного управления
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchOffsetX = 0f
    private var touchOffsetY = 0f

    // Смещение при открытии шторки
    private var drawerSlideOffset = 0f

    // Структура звезды
    private data class Star(
        val x: Float,
        val y: Float,
        val radius: Float,
        val baseAlpha: Int,
        val color: Int,
        val depth: Float,        // Параллакс глубина (0.2 - медленно, 1.0 - быстро)
        val twinkleSpeed: Float, // Скорость мерцания
        var twinklePhase: Float  // Начальная фаза мерцания
    )

    // Структура падающей звезды
    private data class ShootingStar(
        var x: Float,
        var y: Float,
        val speedX: Float,
        val speedY: Float,
        val length: Float,
        val width: Float,
        val color: Int,
        var alpha: Float = 1.0f
    )

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    // event.values[1] — тангаж, event.values[0] — крен
                    targetTiltX = event.values[1] * 120f
                    targetTiltY = event.values[0] * 120f
                }
                Sensor.TYPE_GYROSCOPE -> {
                    targetTiltX = (targetTiltX + event.values[1] * 2f).coerceIn(-90f, 90f)
                    targetTiltY = (targetTiltY + event.values[0] * 2f).coerceIn(-90f, 90f)
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    // Косвенный наклон по гравитационному вектору
                    targetTiltX = -event.values[0] * 10f
                    targetTiltY = (event.values[1] - 5f) * 10f
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    init {
        // Получаем менеджер сенсоров
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        } catch (e: Exception) {
            // Игнорируем отсутствие сенсоров на некоторых устройствах или эмуляторах
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
        if (event == null) return super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                // Сдвигаем звездное полотно за пальцем
                touchOffsetX += dx * 0.15f
                touchOffsetY += dy * 0.15f
                lastTouchX = event.x
                lastTouchY = event.y
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        try {
            val w = width
            val h = height
            if (w <= 0 || h <= 0) return

            if (w != lastW || h != lastH) {
                generateStars(w, h)
                lastW = w
                lastH = h
            }

            // 1. Космический глубокий градиент
            val gradient = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                Color.parseColor("#06030F"), // Глубокий ультра-темный фиолетовый
                Color.parseColor("#0B132B"), // Полночный синий
                Shader.TileMode.CLAMP
            )
            bgPaint.shader = gradient
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

            // 2. Плавная фильтрация смещений (Инерция)
            val lerpFactor = 0.08f // Плавность полета звезд
            currentTiltX += lerpFactor * (targetTiltX - currentTiltX)
            currentTiltY += lerpFactor * (targetTiltY - currentTiltY)

            // Деградация сенсорного смещения к центру
            touchOffsetX *= 0.96f
            touchOffsetY *= 0.96f

            // 3. Отрисовка звездного неба с учетом параллакса и мерцания
            for (star in stars) {
                // Рассчитываем индивидуальную фазу мерцания
                star.twinklePhase += star.twinkleSpeed
                val twinkleMult = 0.4f + 0.6f * Math.sin(star.twinklePhase.toDouble()).toFloat()
                val currentAlpha = (star.baseAlpha * twinkleMult).toInt().coerceIn(10, 255)

                // Рассчет смещения параллакса для конкретного уровня глубины звезды
                val xOffset = (currentTiltX + touchOffsetX) * star.depth - (drawerSlideOffset * w * 0.35f * star.depth)
                val yOffset = (currentTiltY + touchOffsetY) * star.depth

                // Корректируем координаты по границам экрана с закольцовыванием (Wrap around)
                var finalX = star.x + xOffset
                var finalY = star.y + yOffset

                if (finalX < 0) finalX += w
                else if (finalX > w) finalX -= w

                if (finalY < 0) finalY += h
                else if (finalY > h) finalY -= h

                starPaint.color = star.color
                starPaint.alpha = currentAlpha
                canvas.drawCircle(finalX, finalY, star.radius, starPaint)
            }

            // 4. Логика и отрисовка падающих звезд
            updateAndDrawShootingStars(canvas, w, h)

            // Запрашиваем следующий кадр анимации
            if (animating) {
                postInvalidateOnAnimation()
            }
        } catch (e: Exception) {
            canvas.drawColor(Color.parseColor("#06030F"))
        }
    }

    private fun generateStars(w: Int, h: Int) {
        stars.clear()
        val count = 160 // Баланс производительности и красоты
        for (i in 0 until count) {
            val x = random.nextFloat() * w
            val y = random.nextFloat() * h
            
            // Распределяем звезды по трем уровням глубины
            val depthSelector = random.nextFloat()
            val depth = when {
                depthSelector < 0.5f -> 0.2f  // 50% супердалеких, неподвижных звезд
                depthSelector < 0.85f -> 0.5f // 35% среднего уровня
                else -> 1.0f                  // 15% ближних крупных звезд
            }

            val radius = when (depth) {
                0.2f -> 0.4f + random.nextFloat() * 0.5f
                0.5f -> 0.8f + random.nextFloat() * 0.6f
                else -> 1.4f + random.nextFloat() * 1.2f
            }

            val baseAlpha = when (depth) {
                0.2f -> 50 + random.nextInt(80)
                0.5f -> 120 + random.nextInt(80)
                else -> 180 + random.nextInt(75)
            }

            // Цветовые акценты: белый, звездный голубой, теплый оранжевый
            val colorSelector = random.nextFloat()
            val color = when {
                colorSelector > 0.92f -> Color.rgb(173, 216, 230) // Ледяной голубой
                colorSelector > 0.85f -> Color.rgb(255, 235, 205) // Мягкий бежевый
                else -> Color.WHITE
            }

            val twinkleSpeed = 0.01f + random.nextFloat() * 0.04f
            val twinklePhase = random.nextFloat() * (Math.PI * 2).toFloat()

            stars.add(Star(x, y, radius, baseAlpha, color, depth, twinkleSpeed, twinklePhase))
        }
    }

    private fun updateAndDrawShootingStars(canvas: Canvas, w: Int, h: Int) {
        // Вероятность появления новой падающей звезды (0.4% в каждом кадре)
        if (random.nextFloat() < 0.004f && shootingStars.size < 2) {
            val startX = random.nextFloat() * w
            val startY = random.nextFloat() * (h * 0.5f) // Появляются только в верхней половине
            val speed = 20f + random.nextFloat() * 25f
            val angle = (35 + random.nextInt(25)) * Math.PI / 180.0 // Угол вниз-влево
            
            val speedX = (-speed * Math.cos(angle)).toFloat()
            val speedY = (speed * Math.sin(angle)).toFloat()
            val length = 90f + random.nextFloat() * 140f
            val width = 1.2f + random.nextFloat() * 1.8f
            
            val color = if (random.nextFloat() > 0.6f) Color.rgb(255, 240, 220) else Color.WHITE
            shootingStars.add(ShootingStar(startX, startY, speedX, speedY, length, width, color))
        }

        // Обновляем и рисуем активные падающие звезды
        val iterator = shootingStars.iterator()
        while (iterator.hasNext()) {
            val s = iterator.next()
            s.x += s.speedX
            s.y += s.speedY
            s.alpha -= 0.025f // Быстрое затухание следа

            if (s.alpha <= 0f || s.x < -150f || s.x > w + 150f || s.y > h + 150f) {
                iterator.remove()
                continue
            }

            // Считаем координаты хвоста звезды
            val hyp = Math.hypot(s.speedX.toDouble(), s.speedY.toDouble()).toFloat()
            if (hyp <= 0f) continue
            val trailX = s.x - (s.speedX / hyp) * s.length
            val trailY = s.y - (s.speedY / hyp) * s.length

            // Отрисовка падающей звезды с красивым градиентным хвостом
            val glowShader = LinearGradient(
                s.x, s.y, trailX, trailY,
                s.color, Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            shootingStarPaint.shader = glowShader
            shootingStarPaint.strokeWidth = s.width
            shootingStarPaint.alpha = (s.alpha * 255).toInt().coerceIn(0, 255)

            canvas.drawLine(s.x, s.y, trailX, trailY, shootingStarPaint)
        }
        shootingStarPaint.shader = null // Очищаем шейдер для переиспользования
    }
}

