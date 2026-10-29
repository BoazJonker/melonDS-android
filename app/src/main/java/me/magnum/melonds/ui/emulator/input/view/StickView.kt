package me.magnum.melonds.ui.emulator.input.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.min

/**
 * Spirit Tracks styled analog stick: a shaded parchment base framed in dark wood and gold, with a raised, glossy gold knob that follows the player's thumb
 * and casts a shadow on the base.
 */
class StickView(context: Context) : View(context) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFC9AE74.toInt()
    }
    private val spiralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFF7A4A25.toInt()
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val woodPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val ovalRect = RectF()

    // Knob offset, in the range -1..1 of the knob's travel
    private var knobX = 0f
    private var knobY = 0f
    private var resetAnimator: ValueAnimator? = null

    /**
     * How far the knob can travel from the centre, in pixels. Used by the input handler to normalise the stick position.
     */
    val travelRadius: Float
        // Leaves room for the knob and its shadow at the edge of the view, so it's never cut off when pushed all the way
        get() = min(width, height) * 0.24f

    fun setKnob(x: Float, y: Float) {
        resetAnimator?.cancel()
        knobX = x
        knobY = y
        invalidate()
    }

    fun resetKnob() {
        val startX = knobX
        val startY = knobY
        resetAnimator?.cancel()
        resetAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 180
            interpolator = OvershootInterpolator(2f)
            addUpdateListener {
                val t = it.animatedValue as Float
                knobX = startX * t
                knobY = startY * t
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val unit = min(w, h) / 100f
        ringPaint.strokeWidth = 2 * unit
        ringPaint.pathEffect = DashPathEffect(floatArrayOf(4 * unit, 5 * unit), 0f)
        spiralPaint.strokeWidth = 2 * unit

        val cx = w / 2f
        val cy = h / 2f
        woodPaint.shader = RadialGradient(
            cx - 15 * unit, cy - 20 * unit, 80 * unit,
            intArrayOf(0xFF7A4E28.toInt(), 0xFF3B2412.toInt(), 0xFF1E1008.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        goldPaint.shader = LinearGradient(
            0f, cy - 45 * unit, 0f, cy + 45 * unit,
            intArrayOf(0xFFFBE7A1.toInt(), 0xFFE2B54A.toInt(), 0xFF9C6E1E.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        facePaint.shader = RadialGradient(
            cx - 12 * unit, cy - 14 * unit, 55 * unit,
            intArrayOf(0xFFFFFBEE.toInt(), 0xFFF3E6C4.toInt(), 0xFFCDB27A.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val unit = min(width, height) / 100f
        val cx = width / 2f
        val cy = height / 2f

        // Base, with its shadow on the ground
        fillPaint.color = 0x4D000000
        ovalRect.set(cx - 47 * unit, cy - 38 * unit, cx + 47 * unit, cy + 50 * unit)
        canvas.drawOval(ovalRect, fillPaint)
        canvas.drawCircle(cx, cy, 47 * unit, woodPaint)
        canvas.drawCircle(cx, cy, 43 * unit, goldPaint)
        canvas.drawCircle(cx, cy, 40 * unit, facePaint)
        canvas.drawCircle(cx, cy, 28 * unit, ringPaint)

        // Direction arrows
        fillPaint.color = 0xFF6E4C2C.toInt()
        path.reset()
        drawArrow(cx, cy - 38 * unit, 0f, 1f, unit)
        drawArrow(cx, cy + 38 * unit, 0f, -1f, unit)
        drawArrow(cx - 38 * unit, cy, 1f, 0f, unit)
        drawArrow(cx + 38 * unit, cy, -1f, 0f, unit)
        canvas.drawPath(path, fillPaint)

        // Knob: shadow on the base, its gold side, then the glossy top
        val kx = cx + knobX * travelRadius
        val ky = cy + knobY * travelRadius
        fillPaint.color = 0x59000000
        canvas.drawCircle(kx + 1.5f * unit, ky + 6 * unit, 19 * unit, fillPaint)
        fillPaint.color = 0xFF5A3A10.toInt()
        canvas.drawCircle(kx, ky + 3.5f * unit, 19 * unit, fillPaint)
        fillPaint.color = 0xFF3B2412.toInt()
        canvas.drawCircle(kx, ky, 19 * unit, fillPaint)
        knobPaint.shader = RadialGradient(
            kx - 6 * unit, ky - 7 * unit, 26 * unit,
            intArrayOf(0xFFFFF0B0.toInt(), 0xFFE2B54A.toInt(), 0xFF8A5E14.toInt()), floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(kx, ky, 17 * unit, knobPaint)
        glossPaint.shader = LinearGradient(0f, ky - 14 * unit, 0f, ky - 2 * unit, 0xC0FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        ovalRect.set(kx - 10 * unit, ky - 14 * unit, kx + 8 * unit, ky - 3 * unit)
        canvas.drawOval(ovalRect, glossPaint)

        // Spiral emblem on the knob
        path.reset()
        path.moveTo(kx, ky)
        path.arcTo(kx, ky - 1.5f * unit, kx + 3 * unit, ky + 1.5f * unit, 180f, 180f, false)
        path.arcTo(kx - 6 * unit, ky - 4.5f * unit, kx + 3 * unit, ky + 4.5f * unit, 0f, 180f, false)
        path.arcTo(kx - 6 * unit, ky - 7.5f * unit, kx + 9 * unit, ky + 7.5f * unit, 180f, 180f, false)
        canvas.drawPath(path, spiralPaint)
    }

    private fun drawArrow(tipX: Float, tipY: Float, dirX: Float, dirY: Float, unit: Float) {
        // Triangle pointing outwards, with its tip at (tipX, tipY) and the base towards (dirX, dirY)
        val baseX = tipX + dirX * 7 * unit
        val baseY = tipY + dirY * 7 * unit
        path.moveTo(tipX, tipY)
        path.lineTo(baseX + dirY * 5 * unit, baseY + dirX * 5 * unit)
        path.lineTo(baseX - dirY * 5 * unit, baseY - dirX * 5 * unit)
        path.close()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        resetAnimator?.cancel()
    }
}
