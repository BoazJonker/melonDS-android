package me.magnum.melonds.ui.emulator.input.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Input
import kotlin.math.hypot

/**
 * Draws a set of 3D round buttons, each a wooden socket with a raised cap. When a button is pressed, its cap sinks into the socket, darkens and sends out a
 * gold ripple, and it springs back up when released.
 *
 * Positions and sizes are given in a 100x100 space that covers the view. [FACE_BUTTONS] places A/B/X/Y in the usual diamond, and [ACTION_BUTTONS] places the
 * action buttons of the stick control style in an arc around a large Sword button.
 */
class AnimatedFaceButtonsView(
    context: Context,
    specs: List<ButtonSpec> = FACE_BUTTONS,
    private val showLabels: Boolean = false,
) : View(context) {

    /**
     * @param centerX Center of the button's cap, in the 100x100 space
     * @param radius Radius of the button's socket, in the 100x100 space
     * @param label Name shown below the button, if labels are enabled
     */
    class ButtonSpec(val input: Input, val centerX: Float, val centerY: Float, val radius: Float, val capDrawable: Int, val label: Int? = null)

    companion object {
        val FACE_BUTTONS = listOf(
            ButtonSpec(Input.X, 50f, 17.5f, 16f, R.drawable.st_face_x),
            ButtonSpec(Input.A, 82.5f, 50f, 16f, R.drawable.st_face_a),
            ButtonSpec(Input.B, 50f, 82.5f, 16f, R.drawable.st_face_b),
            ButtonSpec(Input.Y, 17.5f, 50f, 16f, R.drawable.st_face_y),
        )

        /**
         * Action buttons of the stick control style: a large Sword button where the thumb rests, with Spin, Item and Roll in an arc around it. The inputs
         * match [me.magnum.melonds.ui.emulator.input.ActionButtonsInputHandler].
         */
        val ACTION_BUTTONS = listOf(
            ButtonSpec(Input.A, 62f, 58f, 22f, R.drawable.st_action_sword, R.string.action_sword),
            ButtonSpec(Input.B, 32f, 28f, 12f, R.drawable.st_action_spin, R.string.action_spin),
            ButtonSpec(Input.X, 82f, 13f, 12f, R.drawable.st_action_item, R.string.action_item),
            ButtonSpec(Input.Y, 22f, 74f, 12f, R.drawable.st_action_roll, R.string.action_roll),
        )

        // In the button drawables (100x100), the socket has a radius of 46, the cap is centered at y = 48, and a press moves the cap down by 5
        private const val DRAWABLE_SOCKET_RADIUS = 46f
        private const val DRAWABLE_CAP_CENTER_Y = 48f
        private const val DRAWABLE_CAP_RADIUS = 38.5f
        private const val DRAWABLE_PRESS_DEPTH = 5f

        // Touches up to this far outside a button (relative to its radius) still press it
        private const val TOUCH_RADIUS_FACTOR = 1.3f
    }

    private class Button(val spec: ButtonSpec, val cap: Drawable, val base: Drawable) {
        var press = 0f
        var ripple = -1f
        var isPressed = false
        var pressAnimator: ValueAnimator? = null
        var rippleAnimator: ValueAnimator? = null
    }

    private val buttons = specs.map {
        Button(it, loadDrawable(it.capDrawable), loadDrawable(R.drawable.st_disk_base))
    }

    private val pressedFilter = PorterDuffColorFilter(0xFFD6D0C4.toInt(), PorterDuff.Mode.MULTIPLY)
    private val pressInterpolator = DecelerateInterpolator()
    private val releaseInterpolator = OvershootInterpolator(3f)

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFBE7A1.toInt()
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3A2614.toInt()
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xEBF6EDD3.toInt()
    }
    private val labelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF3B2412.toInt()
    }
    private val labelRect = RectF()

    private fun loadDrawable(resId: Int): Drawable {
        return ContextCompat.getDrawable(context, resId)!!.mutate()
    }

    /**
     * Returns the input of the button at the given position in the view, if any. When the position is close to several buttons, the nearest one is returned.
     */
    fun getInputAt(x: Float, y: Float): Input? {
        val unitX = width / 100f
        val unitY = height / 100f
        return buttons
            .map { it to hypot(x / unitX - it.spec.centerX, y / unitY - it.spec.centerY) }
            .filter { (button, distance) -> distance <= button.spec.radius * TOUCH_RADIUS_FACTOR }
            .minByOrNull { (_, distance) -> distance }
            ?.first
            ?.spec
            ?.input
    }

    fun setPressedInputs(pressedInputs: Collection<Input>) {
        buttons.forEach { button ->
            val isPressed = button.spec.input in pressedInputs
            if (isPressed == button.isPressed) {
                return@forEach
            }

            button.isPressed = isPressed
            button.cap.colorFilter = if (isPressed) pressedFilter else null
            button.pressAnimator?.cancel()
            button.pressAnimator = ValueAnimator.ofFloat(button.press, if (isPressed) 1f else 0f).apply {
                duration = if (isPressed) ButtonPressAnimator.PRESS_DURATION_MS else ButtonPressAnimator.RELEASE_DURATION_MS
                interpolator = if (isPressed) pressInterpolator else releaseInterpolator
                addUpdateListener {
                    button.press = it.animatedValue as Float
                    invalidate()
                }
                start()
            }

            if (isPressed) {
                button.rippleAnimator?.cancel()
                button.rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = ButtonPressAnimator.RIPPLE_DURATION_MS
                    addUpdateListener {
                        button.ripple = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val unit = width / 100f
        val unitY = height / 100f
        buttons.forEach { button ->
            val spec = button.spec
            val drawableSize = spec.radius / DRAWABLE_SOCKET_RADIUS * 100f * unit
            val scale = drawableSize / 100f
            val capCenterX = spec.centerX * unit
            val capCenterY = spec.centerY * unitY
            val left = (capCenterX - drawableSize / 2f).toInt()
            val top = (capCenterY - DRAWABLE_CAP_CENTER_Y * scale).toInt()
            val size = drawableSize.toInt()

            button.base.setBounds(left, top, left + size, top + size)
            button.base.draw(canvas)

            if (button.ripple in 0f..1f) {
                val p = button.ripple
                ripplePaint.alpha = ((1f - p) * 230).toInt()
                ripplePaint.strokeWidth = DRAWABLE_CAP_RADIUS * scale * 0.1f
                canvas.drawCircle(capCenterX, capCenterY, DRAWABLE_CAP_RADIUS * scale * (1f + p * 0.35f), ripplePaint)
            }

            val pressOffset = (DRAWABLE_PRESS_DEPTH * scale * button.press).toInt()
            button.cap.setBounds(left, top + pressOffset, left + size, top + size + pressOffset)
            button.cap.draw(canvas)

            if (showLabels && spec.label != null) {
                drawLabel(canvas, context.getString(spec.label), capCenterX, top + size + 1.5f * unit, unit)
            }
        }
    }

    private fun drawLabel(canvas: Canvas, text: String, centerX: Float, top: Float, unit: Float) {
        labelTextPaint.textSize = 6.5f * unit
        val textWidth = labelTextPaint.measureText(text)
        val paddingX = 2.5f * unit
        val height = 9f * unit
        labelRect.set(centerX - textWidth / 2f - paddingX, top, centerX + textWidth / 2f + paddingX, top + height)
        val corner = height / 2f
        canvas.drawRoundRect(labelRect, corner, corner, labelBackgroundPaint)
        labelBorderPaint.strokeWidth = 0.6f * unit
        canvas.drawRoundRect(labelRect, corner, corner, labelBorderPaint)
        val textBaseline = labelRect.centerY() - (labelTextPaint.descent() + labelTextPaint.ascent()) / 2f
        canvas.drawText(text, centerX, textBaseline, labelTextPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        buttons.forEach {
            it.pressAnimator?.cancel()
            it.rippleAnimator?.cancel()
        }
    }
}
