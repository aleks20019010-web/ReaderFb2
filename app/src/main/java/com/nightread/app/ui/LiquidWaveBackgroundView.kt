package com.nightread.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Единые монотонные часы для всех экземпляров LiquidWaveBackgroundView,
 * чтобы при переходах между Activity/Fragment волны и цвет не прыгали в начало.
 */
private object LiquidWaveClock {
    val startNanos: Long = System.nanoTime()
    fun elapsedSeconds(): Double = (System.nanoTime() - startNanos) / 1_000_000_000.0
}

class LiquidWaveBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private data class WavePalette(
        val c1: FloatArray,
        val c2: FloatArray,
        val c3: FloatArray,
    )

    var isLightTheme: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val runtimeShader: RuntimeShader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RuntimeShader(NIGHTREAD_LIQUID_WAVE_AGSL)
    } else {
        null
    }

    private val c1Cur = FloatArray(3)
    private val c2Cur = FloatArray(3)
    private val c3Cur = FloatArray(3)

    private var isAnimating = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAnimating) return
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        setWillNotDraw(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimationState()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateAnimationState()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateAnimationState()
    }

    private fun updateAnimationState() {
        val shouldRun = isAttachedToWindow && isShown && windowVisibility == VISIBLE
        if (shouldRun) startAnimation() else stopAnimation()
    }

    private fun startAnimation() {
        if (isAnimating) return
        isAnimating = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopAnimation() {
        if (!isAnimating) return
        isAnimating = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun computeCurrentColors(timeSec: Double) {
        val palettes = if (isLightTheme) LIGHT_PALETTES else DARK_PALETTES
        val cyclePos = (timeSec / PALETTE_DURATION_SEC) % palettes.size
        val idxA = cyclePos.toInt() % palettes.size
        val idxB = (idxA + 1) % palettes.size
        val rawFrac = (cyclePos - idxA).toFloat()
        val t = (0.5 - 0.5 * cos(rawFrac * PI)).toFloat()

        val pA = palettes[idxA]
        val pB = palettes[idxB]
        for (i in 0..2) {
            c1Cur[i] = pA.c1[i] + (pB.c1[i] - pA.c1[i]) * t
            c2Cur[i] = pA.c2[i] + (pB.c2[i] - pA.c2[i]) * t
            c3Cur[i] = pA.c3[i] + (pB.c3[i] - pA.c3[i]) * t
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 4f || h <= 4f) return

        val timeSec = LiquidWaveClock.elapsedSeconds()
        computeCurrentColors(timeSec)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null) {
            val t = timeSec.toFloat()
            val flowX = (sin(timeSec * 0.065) * 0.85 + cos(timeSec * 0.037) * 0.45).toFloat()
            val flowY = (cos(timeSec * 0.055) * 0.85 + sin(timeSec * 0.031) * 0.45).toFloat()

            runtimeShader.setFloatUniform("iResolution", w, h)
            runtimeShader.setFloatUniform("iTime", t)
            runtimeShader.setFloatUniform("iFlow", flowX, flowY)
            runtimeShader.setFloatUniform("iLightMode", if (isLightTheme) 1.0f else 0.0f)
            runtimeShader.setFloatUniform("c1", c1Cur[0], c1Cur[1], c1Cur[2])
            runtimeShader.setFloatUniform("c2", c2Cur[0], c2Cur[1], c2Cur[2])
            runtimeShader.setFloatUniform("c3", c3Cur[0], c3Cur[1], c3Cur[2])

            shaderPaint.shader = runtimeShader
            canvas.drawRect(0f, 0f, w, h, shaderPaint)
        } else {
            drawFallbackCanvas(canvas, w, h, timeSec.toFloat())
        }
    }

    private fun drawFallbackCanvas(canvas: Canvas, w: Float, h: Float, timeSec: Float) {
        val baseColor = if (isLightTheme) Color.rgb(244, 241, 234) else Color.rgb(5, 7, 14)
        canvas.drawColor(baseColor)

        val phase = timeSec * 0.14f
        val maxDim = maxOf(w, h)
        val alpha1 = if (isLightTheme) 90 else 155
        val alpha2 = if (isLightTheme) 75 else 130

        val col1 = Color.argb(alpha1, (c1Cur[0] * 255).toInt(), (c1Cur[1] * 255).toInt(), (c1Cur[2] * 255).toInt())
        val col2 = Color.argb(alpha2, (c2Cur[0] * 255).toInt(), (c2Cur[1] * 255).toInt(), (c2Cur[2] * 255).toInt())

        val x1 = w * (0.50f + 0.24f * cos(phase))
        val y1 = h * (0.42f + 0.18f * sin(phase * 1.15f))
        val r1 = maxDim * 0.78f
        fallbackPaint.shader = RadialGradient(x1, y1, r1, col1, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(x1, y1, r1, fallbackPaint)

        val x2 = w * (0.48f + 0.26f * sin(phase * 0.8f + 1.4f))
        val y2 = h * (0.58f + 0.18f * cos(phase * 0.9f))
        val r2 = maxDim * 0.75f
        fallbackPaint.shader = RadialGradient(x2, y2, r2, col2, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(x2, y2, r2, fallbackPaint)
    }

    companion object {
        private const val PALETTE_DURATION_SEC = 22.0

        private fun rgb(hex: Int): FloatArray = floatArrayOf(
            ((hex shr 16) and 0xFF) / 255f,
            ((hex shr 8) and 0xFF) / 255f,
            (hex and 0xFF) / 255f,
        )

        private val DARK_PALETTES = listOf(
            WavePalette(rgb(0x00E5FF), rgb(0x7C4DFF), rgb(0x3D5AFE)), // Циан -> Фиолет -> Индиго
            WavePalette(rgb(0x9353FF), rgb(0xE040FB), rgb(0x536DFE)), // Фиолет -> Маджента -> Синий
            WavePalette(rgb(0xFF2E63), rgb(0xD500F9), rgb(0xFF6E40)), // Алый -> Пурпур -> Коралл
            WavePalette(rgb(0xFFB000), rgb(0xFF5252), rgb(0x8E24AA)), // Янтарь -> Закат -> Слива
            WavePalette(rgb(0x00F5D4), rgb(0x00B0FF), rgb(0x651FFF)), // Изумруд -> Небо -> Индиго
        )

        private val LIGHT_PALETTES = listOf(
            WavePalette(rgb(0x64B5F6), rgb(0xB39DDB), rgb(0x80DEEA)),
            WavePalette(rgb(0xCE93D8), rgb(0xF48FB1), rgb(0x9FA8DA)),
            WavePalette(rgb(0xFFCC80), rgb(0xFFAB91), rgb(0xCE93D8)),
            WavePalette(rgb(0x80CBC4), rgb(0x81D4FA), rgb(0xB39DDB)),
        )

        private const val NIGHTREAD_LIQUID_WAVE_AGSL = """
            uniform float2 iResolution;
            uniform float iTime;
            uniform float2 iFlow;
            uniform float iLightMode;
            uniform float3 c1;
            uniform float3 c2;
            uniform float3 c3;

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / max(iResolution, float2(1.0));
                float aspect = iResolution.x / max(iResolution.y, 1.0);
                // Строгий центр экрана
                float2 p = float2((uv.x - 0.5) * aspect, uv.y - 0.50);

                // Медленное медитативное течение
                float t = iTime * 0.14;

                // Искривление пространства (жидкий шёлк) по всему экрану
                float2 q = p + iFlow * 0.45;
                q.x += 0.34 * sin(q.y * 2.8 + t * 1.1) + 0.16 * cos(q.x * 2.2 - t * 0.7);
                q.y += 0.32 * cos(q.x * 2.6 - t * 0.9) + 0.14 * sin(q.y * 2.5 + t * 0.8);

                float2 r = q;
                r.x += 0.24 * sin(q.y * 4.0 - t * 1.2 + 1.2);
                r.y += 0.24 * cos(q.x * 3.8 + t * 0.95 + 0.6);

                // Перетекание трёх цветов текущей палитры
                float m1 = 0.5 + 0.5 * sin(r.x * 2.1 + r.y * 1.7 + t);
                float m2 = 0.5 + 0.5 * cos(r.x * 1.8 - r.y * 2.2 - t * 0.8);
                float3 baseCol = mix(c1, c2, m1);
                baseCol = mix(baseCol, c3, m2 * 0.55);

                // Тонкие светящиеся «жилки» каустики
                float w1 = abs(sin(r.x * 3.2 + r.y * 2.5 + t * 0.85));
                float w2 = abs(sin(r.x * 2.2 - r.y * 3.4 - t * 1.0 + 1.7));
                float ridge1 = pow(clamp(1.0 - w1, 0.0, 1.0), 6.5);
                float ridge2 = pow(clamp(1.0 - w2, 0.0, 1.0), 7.5);
                float veins = (ridge1 * 0.58 + ridge2 * 0.40);

                // Мягкие лучи из центра экрана
                float angle = atan(p.y, p.x);
                float rays = pow(0.5 + 0.5 * sin(angle * 5.0 + t * 0.5), 4.0) * 0.14;

                // Полное заполнение экрана с мягкой круговой виньеткой от центра
                float dist = length(p);
                float vignette = 1.0 - smoothstep(0.32, 1.35, dist) * 0.38;

                float3 darkResult = (baseCol * (0.48 + rays) + mix(baseCol, float3(1.0), 0.52) * veins * 0.78) * vignette;
                float3 lightBase = float3(0.957, 0.945, 0.918);
                float3 lightResult = mix(lightBase, baseCol * 0.95 + float3(0.12) * veins, 0.36 * vignette);

                float3 color = mix(darkResult, lightResult, iLightMode);
                return half4(clamp(color, 0.0, 1.0), 1.0);
            }
        """
    }
}