package com.boazjonker.unbound.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.min
import kotlin.math.sin

/**
 * The title: "POKÉMON" in yellow and blue over a large silver and violet "UNBOUND", floating gently in front of a slowly turning Poké Ball emblem. A band of
 * light sweeps across the title every few seconds.
 */
class LogoView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val startTime = SystemClock.uptimeMillis()

    private val topText = "POKÉMON"
    private val mainText = "UNBOUND"

    private val topFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Palette.bold
        textAlign = Paint.Align.CENTER
        color = Palette.POKEMON_YELLOW
    }
    private val topStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Palette.bold
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = Palette.POKEMON_BLUE
    }
    private val mainFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Palette.bold
        textAlign = Paint.Align.CENTER
    }
    private val mainStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Palette.bold
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = Palette.INK
    }
    private val mainGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Palette.bold
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = 0x669C7BFF
    }
    private val shimmer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Palette.bold
        textAlign = Paint.Align.CENTER
    }
    private val shimmerMatrix = Matrix()

    private val emblemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x30E3C6FF
    }
    private val emblemHalo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emblemRect = RectF()

    private var mainSize = 0f
    private var topSize = 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val mainSize = fitMainSize(width.toFloat())
        val height = (mainSize * 2.1f).toInt()
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    private fun fitMainSize(width: Float): Float {
        mainFill.textSize = 100f
        val textWidth = mainFill.measureText(mainText)
        return min(width * 0.86f / textWidth * 100f, 84f * d)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        mainSize = fitMainSize(w.toFloat())
        topSize = mainSize * 0.42f
        topFill.textSize = topSize
        topStroke.textSize = topSize
        topStroke.strokeWidth = topSize * 0.2f
        listOf(mainFill, mainStroke, mainGlow, shimmer).forEach { it.textSize = mainSize }
        mainStroke.strokeWidth = mainSize * 0.1f
        mainGlow.strokeWidth = mainSize * 0.28f

        val mainBaseline = h * 0.86f
        mainFill.shader = LinearGradient(
            0f, mainBaseline - mainSize * 0.75f, 0f, mainBaseline,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFD9D2F2.toInt(), Palette.VIOLET_LIGHT, Palette.VIOLET),
            floatArrayOf(0f, 0.4f, 0.75f, 1f),
            Shader.TileMode.CLAMP,
        )
        shimmer.shader = LinearGradient(
            0f, 0f, mainSize * 1.2f, 0f,
            intArrayOf(0x00FFFFFF, 0xCCFFFFFF.toInt(), 0x00FFFFFF),
            null,
            Shader.TileMode.CLAMP,
        )
        val emblemRadius = h * 0.6f
        emblemHalo.shader = RadialGradient(w / 2f, h / 2f, emblemRadius * 1.3f, 0x405B36B8, 0x005B36B8, Shader.TileMode.CLAMP)
        emblemPaint.strokeWidth = emblemRadius * 0.06f
    }

    override fun onDraw(canvas: Canvas) {
        val t = (SystemClock.uptimeMillis() - startTime) / 1000f
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f

        drawEmblem(canvas, cx, h / 2f, h * 0.6f, t)

        canvas.save()
        canvas.translate(0f, sin(t * 1.4f) * 3f * d)

        val topBaseline = h * 0.3f
        canvas.drawText(topText, cx, topBaseline, topStroke)
        canvas.drawText(topText, cx, topBaseline, topFill)

        val mainBaseline = h * 0.86f
        canvas.drawText(mainText, cx, mainBaseline, mainGlow)
        canvas.drawText(mainText, cx, mainBaseline, mainStroke)
        canvas.drawText(mainText, cx, mainBaseline, mainFill)

        // A band of light crosses the title for 1.2 seconds, every 4 seconds
        val sweep = (t % 4f) / 1.2f
        if (sweep <= 1f) {
            val textWidth = mainFill.measureText(mainText)
            val x = cx - textWidth / 2f - mainSize * 1.2f + (textWidth + mainSize * 1.2f) * sweep
            shimmerMatrix.setTranslate(x, 0f)
            shimmer.shader.setLocalMatrix(shimmerMatrix)
            canvas.drawText(mainText, cx, mainBaseline, shimmer)
        }
        canvas.restore()

        postInvalidateDelayed(16)
    }

    private fun drawEmblem(canvas: Canvas, cx: Float, cy: Float, radius: Float, t: Float) {
        canvas.drawCircle(cx, cy, radius * 1.3f, emblemHalo)
        canvas.save()
        canvas.rotate(t * 6f % 360f, cx, cy)
        canvas.drawCircle(cx, cy, radius, emblemPaint)
        canvas.drawLine(cx - radius, cy, cx - radius * 0.3f, cy, emblemPaint)
        canvas.drawLine(cx + radius * 0.3f, cy, cx + radius, cy, emblemPaint)
        canvas.drawCircle(cx, cy, radius * 0.3f, emblemPaint)
        emblemRect.set(cx - radius * 0.14f, cy - radius * 0.14f, cx + radius * 0.14f, cy + radius * 0.14f)
        canvas.drawOval(emblemRect, emblemPaint)
        canvas.restore()
    }
}
