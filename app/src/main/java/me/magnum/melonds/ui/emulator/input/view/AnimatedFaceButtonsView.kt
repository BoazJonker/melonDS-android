package me.magnum.melonds.ui.emulator.input.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Input

/**
 * Draws four buttons individually, in the same diamond arrangement as the static A/B/X/Y image (X top, A right, B bottom, Y left), so that each button can be
 * animated separately when pressed. [drawables] maps each of those inputs to the image drawn in its position.
 */
class AnimatedFaceButtonsView(
    context: Context,
    drawables: Map<Input, Int> = FACE_BUTTON_DRAWABLES,
) : View(context) {

    companion object {
        val FACE_BUTTON_DRAWABLES = mapOf(
            Input.X to R.drawable.st_face_x,
            Input.A to R.drawable.st_face_a,
            Input.B to R.drawable.st_face_b,
            Input.Y to R.drawable.st_face_y,
        )

        // Positions in a 100x100 space, matching the static buttons drawable
        const val BUTTON_RADIUS = 16f
        // The individual button drawables are drawn with a radius of 46 in a 100x100 viewport
        const val DRAWABLE_RADIUS_FRACTION = 0.46f
    }

    private class FaceButton(val input: Input, val centerX: Float, val centerY: Float, val drawable: Drawable) {
        var scale = 1f
        var isPressed = false
        var animator: ValueAnimator? = null
    }

    private val buttons = listOf(
        FaceButton(Input.X, 50f, 17.5f, loadDrawable(drawables.getValue(Input.X))),
        FaceButton(Input.A, 82.5f, 50f, loadDrawable(drawables.getValue(Input.A))),
        FaceButton(Input.B, 50f, 82.5f, loadDrawable(drawables.getValue(Input.B))),
        FaceButton(Input.Y, 17.5f, 50f, loadDrawable(drawables.getValue(Input.Y))),
    )

    private val pressedFilter = PorterDuffColorFilter(0xFFCCCCCC.toInt(), PorterDuff.Mode.MULTIPLY)
    private val pressInterpolator = DecelerateInterpolator()
    private val releaseInterpolator = OvershootInterpolator(3f)

    private fun loadDrawable(resId: Int): Drawable {
        return ContextCompat.getDrawable(context, resId)!!.mutate()
    }

    fun setPressedInputs(pressedInputs: Collection<Input>) {
        buttons.forEach { button ->
            val isPressed = button.input in pressedInputs
            if (isPressed == button.isPressed) {
                return@forEach
            }

            button.isPressed = isPressed
            button.drawable.colorFilter = if (isPressed) pressedFilter else null
            button.animator?.cancel()
            button.animator = ValueAnimator.ofFloat(button.scale, if (isPressed) ButtonPressAnimator.PRESSED_SCALE else 1f).apply {
                duration = if (isPressed) ButtonPressAnimator.PRESS_DURATION_MS else ButtonPressAnimator.RELEASE_DURATION_MS
                interpolator = if (isPressed) pressInterpolator else releaseInterpolator
                addUpdateListener {
                    button.scale = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val unit = width / 100f
        val drawableHalfSize = BUTTON_RADIUS / DRAWABLE_RADIUS_FRACTION / 2f * unit
        buttons.forEach { button ->
            val cx = button.centerX * unit
            val cy = button.centerY * height / 100f
            val half = drawableHalfSize * button.scale
            button.drawable.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
            button.drawable.draw(canvas)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        buttons.forEach { it.animator?.cancel() }
    }
}
