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

    /**
     * Если true, фон не закрашивается темным градиентом, сохраняя подложенный пользовательский векторный фон.
     */
    var transparentBackground: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

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
                    targetTiltX = event.values[1] * 120f
                    targetTiltY = event.values[0] * 120f
                }
                Sensor.TYPE_GYROSCOPE -> {
                    targetTiltX = (targetTiltX + event.values[1] * 2f).coerceIn(-90f, 90f)
                    targetTiltY = (targetTiltY + event.values[0] * 2f).coerceIn(-90f, 90f)
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    targetTiltX = -event.values[0] * 10f
                    targetTiltY = (event.values[1] - 5f) * 10f
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
            // Игнорируем отсутствие сенсоров
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
        if (transparentBackground) {
            return false
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
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

            // 1. Космический глубокий градиент (только если не прозрачный оверлей)
            if (!transparentBackground) {
                val gradient = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    Color.parseColor("#06030F"),
                    Color.parseColor("#0B132B"),
                    Shader.TileMode.CLAMP
                )
                bgPaint.shader = gradient
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
            }

            // 2. Плавная фильтрация смещений (Инерция)
            val lerpFactor = 0.08f
            currentTiltX += lerpFactor * (targetTiltX - currentTiltX)
            currentTiltY += lerpFactor * (targetTiltY - currentTiltY)

            touchOffsetX *= 0.96f
            touchOffsetY *= 0.96f

            // 3. Отрисовка звездного неба с учетом параллакса и мерцания
            for (star in stars) {
                star.twinklePhase += star.twinkleSpeed
                val twinkleMult = 0.4f + 0.6f * Math.sin(star.twinklePhase.toDouble()).toFloat()
                val currentAlpha = (star.baseAlpha * twinkleMult).toInt().coerceIn(10, 255)

                val xOffset = (currentTiltX + touchOffsetX) * star.depth - (drawerSlideOffset * w * 0.35f * star.depth)
                val yOffset = (currentTiltY + touchOffsetY) * star.depth

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

            if (animating) {
                postInvalidateOnAnimation()
            }
        } catch (e: Exception) {
            canvas.drawColor(Color.parseColor("#06030F"))
        }
    }

    private fun generateStars(w: Int, h: Int) {
        stars.clear()
        val count = 160
        for (i in 0 until count) {
            val x = random.nextFloat() * w
            val y = random.nextFloat() * h
            
            val depthSelector = random.nextFloat()
            val depth = when {
                depthSelector < 0.5f -> 0.2f
                depthSelector < 0.85f -> 0.5f
                else -> 1.0f
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

            val colorSelector = random.nextFloat()
            val color = when {
                colorSelector > 0.92f -> Color.rgb(173, 216, 230)
                colorSelector > 0.85f -> Color.rgb(255, 235, 205)
                else -> Color.WHITE
            }

            val twinkleSpeed = 0.01f + random.nextFloat() * 0.04f
            val twinklePhase = random.nextFloat() * (Math.PI * 2).toFloat()

            stars.add(Star(x, y, radius, baseAlpha, color, depth, twinkleSpeed, twinklePhase))
        }
    }

    private fun updateAndDrawShootingStars(canvas: Canvas, w: Int, h: Int) {
        if (random.nextFloat() < 0.007f && shootingStars.size < 3) {
            val startX = random.nextFloat() * w
            val startY = random.nextFloat() * (h * 0.65f)
            val speed = 16f + random.nextFloat() * 22f
            
            val isLeftToRight = random.nextBoolean()
            val angleDeg = 25 + random.nextInt(35)
            val angle = angleDeg * Math.PI / 180.0
            
            val speedX = (if (isLeftToRight) speed * Math.cos(angle) else -speed * Math.cos(angle)).toFloat()
            val speedY = (speed * Math.sin(angle)).toFloat()
            
            val length = 55f + random.nextFloat() * 75f
            val width = 0.9f + random.nextFloat() * 1.3f
            
            val color = when (random.nextInt(3)) {
                0 -> Color.WHITE
                1 -> Color.rgb(255, 238, 195)
                else -> Color.rgb(225, 240, 255)
            }
            shootingStars.add(ShootingStar(startX, startY, speedX, speedY, length, width, color))
        }

        val iterator = shootingStars.iterator()
        while (iterator.hasNext()) {
            val s = iterator.next()
            s.x += s.speedX
            s.y += s.speedY
            s.alpha -= 0.022f

            if (s.alpha <= 0f || s.x < -150f || s.x > w + 150f || s.y > h + 150f) {
                iterator.remove()
                continue
            }

            val hyp = Math.hypot(s.speedX.toDouble(), s.speedY.toDouble()).toFloat()
            if (hyp <= 0f) continue
            val trailX = s.x - (s.speedX / hyp) * s.length
            val trailY = s.y - (s.speedY / hyp) * s.length

            val glowShader = LinearGradient(
                s.x, s.y, trailX, trailY,
                s.color, Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            shootingStarPaint.shader = glowShader
            shootingStarPaint.strokeWidth = s.width
            shootingStarPaint.alpha = (s.alpha * 255).toInt().coerceIn(0, 255)

            canvas.drawLine(s.x, s.y, trailX, trailY, shootingStarPaint)

            starPaint.color = s.color
            starPaint.alpha = (s.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(s.x, s.y, s.width * 1.3f, starPaint)
        }
        shootingStarPaint.shader = null
    }
}
