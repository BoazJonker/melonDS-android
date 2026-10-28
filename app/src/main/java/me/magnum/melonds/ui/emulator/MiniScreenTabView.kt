package me.magnum.melonds.ui.emulator

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * Small gold tab shown at the edge of the screen while the small screen window is hidden. Tapping it brings the window back.
 */
class MiniScreenTabView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /**
     * Whether the tab sits on the right edge of the screen, in which case it's rounded on the left and its arrow points left.
     */
    var isOnRightEdge = true
        set(value) {
            field = value
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF3B2412.toInt()
        strokeWidth = 2 * d
    }
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF3B2412.toInt()
        strokeWidth = 2.5f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
    private val rect = RectF()
    private val path = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fill.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFFFBE7A1.toInt(), 0xFFE2B54A.toInt(), 0xFF9C6E1E.toInt()), null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = 10 * d
        canvas.save()
        if (!isOnRightEdge) {
            canvas.scale(-1f, 1f, w / 2f, h / 2f)
        }

        // Rounded on the inner side only: the outer corners are pushed past the screen edge
        rect.set(0f, 3 * d, w + radius, h - d)
        canvas.drawRoundRect(rect, radius, radius, shadow)
        rect.set(2 * d, 2 * d, w + radius, h - 3 * d)
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.drawRoundRect(rect, radius, radius, border)

        val cx = w * 0.55f
        val cy = (h - d) / 2f
        path.reset()
        path.moveTo(cx + 3 * d, cy - 6 * d)
        path.lineTo(cx - 3 * d, cy)
        path.lineTo(cx + 3 * d, cy + 6 * d)
        canvas.drawPath(path, arrow)
        canvas.restore()
    }
}
