package com.callonlines.softphone

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class OrbitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
    private val planetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val planetGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(28f, BlurMaskFilter.Blur.NORMAL)
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private var bgPaint: Paint? = null

    private data class OrbitDef(
        val rxFactor: Float,
        val ryFactor: Float,
        val tilt: Float,
        val angularSpeed: Float,
        val phase: Float,
        val planetColor: Int,
        val planetRadius: Float,
        val glowRadius: Float
    )

    private data class Star(val x: Float, val y: Float, val radius: Float, val blinkOffset: Float)

    private val orbits = listOf(
        OrbitDef(0.40f, 0.13f,  -8f, 0.55f, 0.0f,  0xFF22D3EE.toInt(), 7f, 22f),
        OrbitDef(0.55f, 0.18f,   6f, -0.38f, 1.6f, 0xFFA855F7.toInt(), 5f, 18f),
        OrbitDef(0.70f, 0.23f, -12f, 0.22f, 3.4f,  0xFF67E8F9.toInt(), 4f, 14f),
        OrbitDef(0.85f, 0.28f,   4f, -0.14f, 5.0f, 0xFFFBBF24.toInt(), 3f, 10f)
    )

    private val stars = mutableListOf<Star>()
    private var lastFrameNs = 0L
    private var t = 0f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        stars.clear()
        val rnd = Random(13)
        repeat(80) {
            stars += Star(
                x = rnd.nextFloat() * w,
                y = rnd.nextFloat() * h,
                radius = 0.4f + rnd.nextFloat() * 1.6f,
                blinkOffset = rnd.nextFloat() * 6.28f
            )
        }
        bgPaint = Paint().apply {
            shader = RadialGradient(
                w * 0.5f, h * 0.35f,
                maxOf(w, h) * 0.95f,
                intArrayOf(0xFF132041.toInt(), 0xFF09111F.toInt(), 0xFF050912.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastFrameNs == 0L) 1f / 60f else ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrameNs = now
        t += dt

        val cx = width / 2f
        val cy = height * 0.42f

        bgPaint?.let { canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), it) }

        for (s in stars) {
            val alpha = (90 + (sin(t * 2f + s.blinkOffset) + 1f) * 70f).toInt().coerceIn(0, 255)
            starPaint.alpha = alpha
            canvas.drawCircle(s.x, s.y, s.radius, starPaint)
        }

        for (o in orbits) {
            val rx = width * o.rxFactor
            val ry = height * o.ryFactor

            canvas.save()
            canvas.rotate(o.tilt, cx, cy)
            orbitPaint.color = (o.planetColor and 0x00FFFFFF) or 0x33000000
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, orbitPaint)

            val angle = t * o.angularSpeed + o.phase
            val px = cx + rx * cos(angle)
            val py = cy + ry * sin(angle)

            planetGlowPaint.color = (o.planetColor and 0x00FFFFFF) or 0x66000000
            canvas.drawCircle(px, py, o.glowRadius, planetGlowPaint)

            planetPaint.color = o.planetColor
            canvas.drawCircle(px, py, o.planetRadius, planetPaint)
            canvas.restore()
        }

        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lastFrameNs = 0L
    }
}
