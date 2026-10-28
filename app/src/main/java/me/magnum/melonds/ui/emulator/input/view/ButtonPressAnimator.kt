package me.magnum.melonds.ui.emulator.input.view

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import me.magnum.melonds.domain.model.Input

/**
 * Press animations for the on-screen buttons: pressed buttons sink (shrink slightly) and darken, and spring back with a small bounce when released. The D-pad
 * tilts towards the pressed direction instead.
 */
object ButtonPressAnimator {

    const val PRESSED_SCALE = 0.88f
    const val PRESS_DURATION_MS = 60L
    const val RELEASE_DURATION_MS = 180L
    private const val DPAD_TILT_DEGREES = 10f
    private const val DPAD_PRESSED_SCALE = 0.96f

    private val pressInterpolator = DecelerateInterpolator()
    private val releaseInterpolator = OvershootInterpolator(3f)

    private val darkenPaint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.8f, 0.8f, 0.8f, 1f) })
    }

    fun press(view: View) {
        setDarkened(view, true)
        view.animate()
            .scaleX(PRESSED_SCALE)
            .scaleY(PRESSED_SCALE)
            .setInterpolator(pressInterpolator)
            .setDuration(PRESS_DURATION_MS)
            .start()
    }

    fun release(view: View) {
        setDarkened(view, false)
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setInterpolator(releaseInterpolator)
            .setDuration(RELEASE_DURATION_MS)
            .start()
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

    private fun setDarkened(view: View, darkened: Boolean) {
        if (darkened) {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, darkenPaint)
        } else {
            view.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }
}
