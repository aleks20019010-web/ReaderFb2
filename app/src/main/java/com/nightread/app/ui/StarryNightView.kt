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
    private val fireflyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random()
    private val stars = mutableListOf<Star>()
    private val shootingStars = mutableListOf<ShootingStar>()
    private val fireflies = mutableListOf<Firefly>()

    private var lastW = 0
    private var lastH = 0
    private var animating = false

    // Структура светлячка
    private data class Firefly(
        var x: Float,
        var y: Float,
        val baseRadius: Float,
        val speedX: Float,
        val speedY: Float,
        val color: Int,
        val pulseSpeed: Float,
        var pulsePhase: Float,
        val depth: Float,
        var driftOffsetX: Float = 0f,
        var driftOffsetY: Float = 0f
    )

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

    private fun pushFireflies(tx: Float, ty: Float) {
        val density = resources.displayMetrics.density
        val pushRadius = 150f * density // Around 150dp radius of influence
        for (f in fireflies) {
            val dx = f.x - tx
            val dy = f.y - ty
            val distSq = dx * dx + dy * dy
            if (distSq < pushRadius * pushRadius) {
                val dist = Math.sqrt(distSq.toDouble()).toFloat()
                if (dist > 0.1f) {
                    val force = (pushRadius - dist) / pushRadius
                    // Push vector
                    val pushX = (dx / dist) * force * 15f * density
                    val pushY = (dy / dist) * force * 15f * density
                    
                    f.driftOffsetX += pushX
                    f.driftOffsetY += pushY
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                pushFireflies(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                // Сдвигаем звездное полотно за пальцем
                touchOffsetX += dx * 0.15f
                touchOffsetY += dy * 0.15f
                lastTouchX = event.x
                lastTouchY = event.y
                pushFireflies(event.x, event.y)
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

            // 3b. Отрисовка интерактивных светлячков (Ambient Fireflies)
            for (f in fireflies) {
                // Update pulsation
                f.pulsePhase += f.pulseSpeed
                val sinVal = Math.sin(f.pulsePhase.toDouble()).toFloat()
                
                // Pulsate alpha (0.2 to 0.7) and radius (0.85x to 1.15x)
                val alphaMult = 0.25f + 0.45f * (sinVal + 1f) / 2f
                val currentRadius = f.baseRadius * (0.85f + 0.15f * sinVal)
                
                // Slowly drift naturally
                f.x += f.speedX + f.driftOffsetX
                f.y += f.speedY + f.driftOffsetY
                
                // Decay the touch-push offsets
                f.driftOffsetX *= 0.92f
                f.driftOffsetY *= 0.92f
                
                // Wrap around edges
                if (f.x < -50f) f.x += w + 100f
                else if (f.x > w + 50f) f.x -= w + 100f
                
                if (f.y < -50f) f.y += h + 100f
                else if (f.y > h + 50f) f.y -= h + 100f
                
                // Parallax shift based on depth and tilts
                val xOffset = (currentTiltX + touchOffsetX) * f.depth - (drawerSlideOffset * w * 0.4f * f.depth)
                val yOffset = (currentTiltY + touchOffsetY) * f.depth
                
                var drawX = f.x + xOffset
                var drawY = f.y + yOffset
                
                // Wrap around for parallax draw position as well to keep them on screen
                if (drawX < 0) drawX += w
                else if (drawX > w) drawX -= w
                
                if (drawY < 0) drawY += h
                else if (drawY > h) drawY -= h
                
                // Draw a soft glowing firefly (dual layers for neon blur)
                // Outer glow: very low alpha, larger radius
                fireflyPaint.color = f.color
                fireflyPaint.alpha = (alphaMult * 0.15f * 255).toInt().coerceIn(0, 255)
                canvas.drawCircle(drawX, drawY, currentRadius * 2.2f, fireflyPaint)
                
                // Inner core: higher alpha, standard radius
                fireflyPaint.alpha = (alphaMult * 0.85f * 255).toInt().coerceIn(0, 255)
                canvas.drawCircle(drawX, drawY, currentRadius, fireflyPaint)
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
        generateFireflies(w, h)
    }

    private fun generateFireflies(w: Int, h: Int) {
        fireflies.clear()
        val count = 28 // Balanced number for premium aesthetic without over-crowding
        val density = resources.displayMetrics.density
        for (i in 0 until count) {
            val x = random.nextFloat() * w
            val y = random.nextFloat() * h
            
            // Random slow speeds
            val speedX = (random.nextFloat() * 0.4f - 0.2f) * density
            val speedY = (random.nextFloat() * 0.4f - 0.2f) * density
            
            // Large, soft radius (3dp to 6dp)
            val baseRadius = (3f + random.nextFloat() * 3f) * density
            
            // Colors: magical warm golden/amber (#FFD700, #FFAB40, #FFE082, #FF9100)
            val colorSelector = random.nextFloat()
            val color = when {
                colorSelector > 0.7f -> Color.rgb(255, 171, 64)  // Warm Amber
                colorSelector > 0.4f -> Color.rgb(255, 224, 130) // Soft light yellow
                else -> Color.rgb(255, 215, 0)                   // Gold
            }
            
            val pulseSpeed = 0.015f + random.nextFloat() * 0.025f
            val pulsePhase = random.nextFloat() * (Math.PI * 2).toFloat()
            
            // Floating foreground layer has depth 1.6 to 2.4
            val depth = 1.6f + random.nextFloat() * 0.8f
            
            fireflies.add(Firefly(x, y, baseRadius, speedX, speedY, color, pulseSpeed, pulsePhase, depth))
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

