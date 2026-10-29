package com.boazjonker.unbound.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The title, on a gold Poké Ball medallion: "POKÉMON" in gold above a large "UNBOUND" with a gold outline and a slowly drifting galaxy inside its letters.
 * The title lies across the medallion's band. Sparkles twinkle around it, the dotted outer ring turns, and a band of light sweeps across the gold every
 * few seconds.
 */
class LogoView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val startTime = SystemClock.uptimeMillis()

    private val topText = "POKÉMON"
    private val mainText = "UNBOUND"

    private val dottedRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x59F2C14E }
    private val insidePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val goldRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val thinRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x99F2C14E.toInt() }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.INK }
    private val bandEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCF2C14E.toInt() }
    private val buttonRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val buttonInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Palette.GOLD_LIGHT }
    private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.GOLD_LIGHT }

    private val topFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Palette.bold
        textAlign = Paint.Align.CENTER
        color = Palette.GOLD
        letterSpacing = 0.32f
    }
    private val topShadow = Paint(topFill).apply { color = 0xFF5E3B08.toInt() }

    private val galaxyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Palette.bold
        textAlign = Paint.Align.CENTER
    }
    private val goldOutline = Paint(galaxyFill).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val goldGlow = Paint(goldOutline)
    private val darkEdge = Paint(goldOutline).apply { color = 0x993A2406.toInt() }
    private val shimmer = Paint(goldOutline)
    private val galaxyMatrix = Matrix()
    private val shimmerMatrix = Matrix()
    private var galaxy: Bitmap? = null

    private val sparkle = Path()
    private var mainSize = 0f
    private var radius = 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, resolveSize((width * 0.85f).toInt(), heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        val cx = w / 2f
        val cy = h / 2f
        radius = min(w * 0.36f, h * 0.42f)

        dottedRing.strokeWidth = 1f * d
        dottedRing.pathEffect = DashPathEffect(floatArrayOf(3f * d, 7f * d), 0f)
        insidePaint.shader = RadialGradient(cx, cy - radius * 0.2f, radius * 1.2f, 0xD93A2690.toInt(), 0xD9120A35.toInt(), Shader.TileMode.CLAMP)
        val ringGradient = LinearGradient(0f, cy - radius, 0f, cy + radius, intArrayOf(Palette.GOLD_LIGHT, Palette.GOLD, Palette.GOLD_DARK), null, Shader.TileMode.CLAMP)
        goldRing.shader = ringGradient
        goldRing.strokeWidth = radius * 0.057f
        thinRing.strokeWidth = 1.5f * d
        buttonRing.shader = ringGradient
        buttonRing.strokeWidth = radius * 0.036f
        buttonInner.strokeWidth = 2f * d

        // Fit UNBOUND across the medallion, a little wider than it
        galaxyFill.textSize = 100f
        mainSize = min(w * 0.9f / galaxyFill.measureText(mainText) * 100f, 96f * d)
        listOf(galaxyFill, goldOutline, goldGlow, darkEdge, shimmer).forEach { it.textSize = mainSize }
        goldOutline.strokeWidth = mainSize * 0.095f
        goldGlow.strokeWidth = mainSize * 0.095f
        goldGlow.color = Palette.GOLD
        goldGlow.setShadowLayer(mainSize * 0.18f, 0f, 0f, 0xA6F2C14E.toInt())
        darkEdge.strokeWidth = 1f * d
        shimmer.strokeWidth = mainSize * 0.095f

        val textTop = cy - mainSize * 0.5f
        goldOutline.shader = LinearGradient(
            0f, textTop - mainSize * 0.1f, 0f, textTop + mainSize * 0.85f,
            intArrayOf(0xFFFFF6D0.toInt(), Palette.GOLD, 0xFFA86F12.toInt(), 0xFFFFE39A.toInt()),
            floatArrayOf(0f, 0.42f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
        shimmer.shader = LinearGradient(
            0f, 0f, mainSize * 1.2f, 0f,
            intArrayOf(0x00FFFFFF, 0xDDFFFFFF.toInt(), 0x00FFFFFF),
            null,
            Shader.TileMode.CLAMP,
        )

        topFill.textSize = mainSize * 0.27f
        topShadow.textSize = topFill.textSize

        galaxy?.recycle()
        val galaxyBitmap = drawGalaxy((w * 0.9f).toInt().coerceAtLeast(1), mainSize.toInt().coerceAtLeast(1))
        galaxy = galaxyBitmap
        galaxyFill.shader = BitmapShader(galaxyBitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)

        val s = radius * 0.05f
        sparkle.reset()
        sparkle.moveTo(0f, -s)
        sparkle.lineTo(s * 0.28f, -s * 0.28f)
        sparkle.lineTo(s, 0f)
        sparkle.lineTo(s * 0.28f, s * 0.28f)
        sparkle.lineTo(0f, s)
        sparkle.lineTo(-s * 0.28f, s * 0.28f)
        sparkle.lineTo(-s, 0f)
        sparkle.lineTo(-s * 0.28f, -s * 0.28f)
        sparkle.close()
    }

    /**
     * Deep space with purple, teal and pink nebulae and a scattering of stars.
     */
    private fun drawGalaxy(w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF120A35.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val nebulae = listOf(
            Triple(0.22f, 0.42f, 0xF2B26BFF.toInt()),
            Triple(0.6f, 0.68f, 0xBF46F0C8.toInt()),
            Triple(0.87f, 0.3f, 0xD9FF6FD8.toInt()),
        )
        for ((x, y, colour) in nebulae) {
            paint.shader = RadialGradient(w * x, h * y, w * 0.24f, colour, colour and 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
        paint.shader = null
        paint.color = Palette.WHITE
        val random = Random(3)
        repeat(w * h / 900) {
            paint.alpha = 120 + random.nextInt(136)
            canvas.drawCircle(random.nextFloat() * w, random.nextFloat() * h, (0.4f + random.nextFloat() * 1.1f) * d * 0.6f, paint)
        }
        return bitmap
    }

    override fun onDraw(canvas: Canvas) {
        val t = (SystemClock.uptimeMillis() - startTime) / 1000f
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = radius

        drawMedallion(canvas, cx, cy, r, t)

        canvas.save()
        canvas.translate(0f, sin(t * 1.4f) * 2.5f * d)

        val topBaseline = cy - r * 0.42f
        canvas.drawText(topText, cx + topFill.textSize * 0.16f, topBaseline + 2f * d, topShadow)
        canvas.drawText(topText, cx + topFill.textSize * 0.16f, topBaseline, topFill)

        val baseline = cy + mainSize * 0.36f
        // The galaxy drifts slowly inside the letters
        val textLeft = cx - galaxyFill.measureText(mainText) / 2f
        galaxyMatrix.setTranslate(textLeft - t * 6f * d, baseline - mainSize * 0.8f + sin(t * 0.3f) * 6f * d)
        galaxyFill.shader.setLocalMatrix(galaxyMatrix)

        canvas.drawText(mainText, cx, baseline, goldGlow)
        canvas.drawText(mainText, cx, baseline, goldOutline)
        canvas.drawText(mainText, cx, baseline, galaxyFill)
        canvas.drawText(mainText, cx, baseline, darkEdge)

        // A band of light runs along the gold outline for 1.2 seconds, every 4 seconds
        val sweep = (t % 4f) / 1.2f
        if (sweep <= 1f) {
            val textWidth = galaxyFill.measureText(mainText)
            shimmerMatrix.setTranslate(cx - textWidth / 2f - mainSize * 1.2f + (textWidth + mainSize * 1.2f) * sweep, 0f)
            shimmer.shader.setLocalMatrix(shimmerMatrix)
            canvas.drawText(mainText, cx, baseline, shimmer)
        }
        canvas.restore()

        postInvalidateDelayed(16)
    }

    private fun drawMedallion(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float) {
        canvas.save()
        canvas.rotate(t * 4f % 360f, cx, cy)
        canvas.drawCircle(cx, cy, r * 1.13f, dottedRing)
        canvas.restore()

        canvas.drawCircle(cx, cy, r, insidePaint)
        canvas.drawCircle(cx, cy, r, goldRing)
        canvas.drawCircle(cx, cy, r * 0.9f, thinRing)

        val bandHalf = r * 0.05f
        canvas.drawRect(cx - r, cy - bandHalf, cx + r, cy + bandHalf, bandPaint)
        canvas.drawRect(cx - r, cy - bandHalf - 1f * d, cx + r, cy - bandHalf + 1f * d, bandEdge)
        canvas.drawRect(cx - r, cy + bandHalf - 1f * d, cx + r, cy + bandHalf + 1f * d, bandEdge)

        val buttonY = cy + r * 0.54f
        canvas.drawCircle(cx, buttonY, r * 0.186f, bandPaint)
        canvas.drawCircle(cx, buttonY, r * 0.186f, buttonRing)
        canvas.drawCircle(cx, buttonY, r * 0.086f, buttonInner)

        canvas.drawCircle(cx - r * 1.13f, cy, 4f * d, dotPaint)
        canvas.drawCircle(cx + r * 1.13f, cy, 4f * d, dotPaint)

        drawSparkle(canvas, cx + r * 0.88f, cy - r * 0.89f, 1.4f, Palette.GOLD_LIGHT, t, 0f)
        drawSparkle(canvas, cx - r * 0.98f, cy + r * 0.89f, 1f, Palette.GLOW, t, 1.7f)
        drawSparkle(canvas, cx - r * 0.7f, cy - r * 1.02f, 0.8f, Palette.GOLD_LIGHT, t, 3.1f)
    }

    private fun drawSparkle(canvas: Canvas, x: Float, y: Float, scale: Float, colour: Int, t: Float, phase: Float) {
        val twinkle = 0.6f + 0.4f * sin(t * 2.2f + phase)
        sparklePaint.color = colour
        sparklePaint.alpha = (twinkle * 255).toInt()
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale * (0.8f + twinkle * 0.3f), scale * (0.8f + twinkle * 0.3f))
        canvas.drawPath(sparkle, sparklePaint)
        canvas.restore()
    }
}
