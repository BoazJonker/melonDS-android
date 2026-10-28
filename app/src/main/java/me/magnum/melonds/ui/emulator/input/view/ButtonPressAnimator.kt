package me.magnum.melonds.ui.emulator.input.view

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import me.magnum.melonds.domain.model.Input
import java.util.WeakHashMap
import kotlin.math.min

/**
 * Press animations for the on-screen buttons. 3D buttons ([CapImageView]) sink their cap into the socket, other buttons shrink slightly. Pressed buttons darken
 * and send out a gold ripple, and spring back with a small bounce when released. The D-pad tilts towards the pressed direction instead.
 */
object ButtonPressAnimator {

    const val PRESSED_SCALE = 0.88f
    const val PRESS_DURATION_MS = 60L
    const val RELEASE_DURATION_MS = 180L
    const val RIPPLE_DURATION_MS = 350L
    private const val DPAD_TILT_DEGREES = 10f
    private const val DPAD_PRESSED_SCALE = 0.96f

    private val pressInterpolator = DecelerateInterpolator()
    private val releaseInterpolator = OvershootInterpolator(3f)

    // Running cap animations, so that a new press or release can cancel the previous one
    private val scrollAnimators = WeakHashMap<View, ValueAnimator>()

    private val darkenFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.82f, 0.82f, 0.82f, 1f) })
    private val darkenPaint = Paint().apply { colorFilter = darkenFilter }

    fun press(view: View) {
        if (view is CapImageView) {
            view.setColorFilter(darkenFilter)
            animateScroll(view, -(view.height * view.pressDepthFraction).toInt(), pressed = true)
            showRipple(view, roundedRectangle = true)
        } else {
            setDarkened(view, true)
            view.animate()
                .scaleX(PRESSED_SCALE)
                .scaleY(PRESSED_SCALE)
                .setInterpolator(pressInterpolator)
                .setDuration(PRESS_DURATION_MS)
                .start()
            showRipple(view, roundedRectangle = false)
        }
    }

    fun release(view: View) {
        if (view is CapImageView) {
            view.clearColorFilter()
            animateScroll(view, 0, pressed = false)
        } else {
            setDarkened(view, false)
            view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setInterpolator(releaseInterpolator)
                .setDuration(RELEASE_DURATION_MS)
                .start()
        }
    }

    /**
     * Tilts the D-pad towards the pressed directions, like a physical rocker pad. Diagonals tilt towards both directions.
     */
    fun tiltDpad(view: View, pressedInputs: Collection<Input>) {
        // Keep the perspective subtle
        view.cameraDistance = 6000 * view.resources.displayMetrics.density

        // Positive X rotation pushes the top edge away from the viewer and negative Y rotation pushes the right edge away
        var rotationX = 0f
        var rotationY = 0f
        if (Input.UP in pressedInputs) rotationX += DPAD_TILT_DEGREES
        if (Input.DOWN in pressedInputs) rotationX -= DPAD_TILT_DEGREES
        if (Input.RIGHT in pressedInputs) rotationY -= DPAD_TILT_DEGREES
        if (Input.LEFT in pressedInputs) rotationY += DPAD_TILT_DEGREES

        val isPressed = pressedInputs.isNotEmpty()
        setDarkened(view, isPressed)
        val scale = if (isPressed) DPAD_PRESSED_SCALE else 1f
        view.animate()
            .rotationX(rotationX)
            .rotationY(rotationY)
            .scaleX(scale)
            .scaleY(scale)
            .setInterpolator(if (isPressed) pressInterpolator else releaseInterpolator)
            .setDuration(if (isPressed) PRESS_DURATION_MS else RELEASE_DURATION_MS)
            .start()
    }

    private fun animateScroll(view: View, targetScrollY: Int, pressed: Boolean) {
        scrollAnimators[view]?.cancel()
        scrollAnimators[view] = ObjectAnimator.ofInt(view, "scrollY", view.scrollY, targetScrollY).apply {
            duration = if (pressed) PRESS_DURATION_MS else RELEASE_DURATION_MS
            interpolator = if (pressed) pressInterpolator else releaseInterpolator
            start()
        }
    }

    private fun setDarkened(view: View, darkened: Boolean) {
        if (darkened) {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, darkenPaint)
        } else {
            view.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    /**
     * Shows a gold ring that grows out of the button and fades away.
     */
    private fun showRipple(view: View, roundedRectangle: Boolean) {
        if (view.width == 0 || view.height == 0) {
            return
        }

        val ripple = RippleDrawable(roundedRectangle).apply { setBounds(0, 0, view.width, view.height) }
        view.overlay.add(ripple)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = RIPPLE_DURATION_MS
            addUpdateListener {
                ripple.progress = it.animatedValue as Float
                ripple.invalidateSelf()
                if (it.animatedFraction >= 1f) {
                    view.overlay.remove(ripple)
                }
            }
            start()
        }
    }

    private class RippleDrawable(private val roundedRectangle: Boolean) : Drawable() {
        var progress = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFFBE7A1.toInt()
        }
        private val rect = RectF()

        override fun draw(canvas: Canvas) {
            val bounds = bounds
            val size = min(bounds.width(), bounds.height()).toFloat()
            paint.alpha = ((1f - progress) * 230).toInt()
            paint.strokeWidth = size * 0.05f
            if (roundedRectangle) {
                // Around the cap of a plank button, which fills most of the width and the middle of the height
                val grow = size * 0.12f * progress
                rect.set(bounds.width() * 0.06f - grow, bounds.height() * 0.28f - grow, bounds.width() * 0.94f + grow, bounds.height() * 0.61f + grow)
                val corner = rect.height() / 2f
                canvas.drawRoundRect(rect, corner, corner, paint)
            } else {
                canvas.drawCircle(bounds.exactCenterX(), bounds.height() * 0.48f, size * 0.38f * (1f + progress * 0.3f), paint)
            }
        }

        override fun setAlpha(alpha: Int) {
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
