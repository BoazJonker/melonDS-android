package me.magnum.melonds.ui.emulator.input.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.min

/**
 * Spirit Tracks styled analog stick: a parchment base framed in dark wood and gold, with a gold knob that follows the player's thumb.
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
    private val path = Path()

    // Knob offset, in the range -1..1 of the knob's travel
    private var knobX = 0f
    private var knobY = 0f
    private var resetAnimator: ValueAnimator? = null

    /**
     * How far the knob can travel from the centre, in pixels. Used by the input handler to normalise the stick position.
     */
    val travelRadius: Float
        get() = min(width, height) * 0.30f

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
    }

    override fun onDraw(canvas: Canvas) {
        val unit = min(width, height) / 100f
        val cx = width / 2f
        val cy = height / 2f

        // Base
        fillPaint.color = 0xFF3B2412.toInt()
        canvas.drawCircle(cx, cy, 48 * unit, fillPaint)
        fillPaint.color = 0xFFE2B54A.toInt()
        canvas.drawCircle(cx, cy, 45.5f * unit, fillPaint)
        fillPaint.color = 0xFFEADBB4.toInt()
        canvas.drawCircle(cx, cy, 43.5f * unit, fillPaint)
        canvas.drawCircle(cx, cy, 30 * unit, ringPaint)

        // Direction arrows
        fillPaint.color = 0xFF6E4C2C.toInt()
        path.reset()
        drawArrow(cx, cy - 38 * unit, 0f, 1f, unit)
        drawArrow(cx, cy + 38 * unit, 0f, -1f, unit)
        drawArrow(cx - 38 * unit, cy, 1f, 0f, unit)
        drawArrow(cx + 38 * unit, cy, -1f, 0f, unit)
        canvas.drawPath(path, fillPaint)

        // Knob
        val kx = cx + knobX * travelRadius
        val ky = cy + knobY * travelRadius
        fillPaint.color = 0xFF3B2412.toInt()
        canvas.drawCircle(kx, ky, 19 * unit, fillPaint)
        knobPaint.shader = RadialGradient(
            kx - 5 * unit, ky - 6 * unit, 24 * unit,
            0xFFF3D27A.toInt(), 0xFFB8862A.toInt(),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(kx, ky, 17 * unit, knobPaint)

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
