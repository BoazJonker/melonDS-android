package com.boazjonker.unbound.ui

import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Press animation of a 3D button: the cap sinks quickly when pressed and springs back up with a small bounce when released. A press also sends out a ring
 * that grows and fades.
 */
class PressState(private val onUpdate: () -> Unit) {

    companion object {
        const val PRESS_DURATION_MS = 60L
        const val RELEASE_DURATION_MS = 220L
        const val RIPPLE_DURATION_MS = 380L

        private val pressInterpolator = DecelerateInterpolator()
        private val releaseInterpolator = OvershootInterpolator(3f)
    }

    /**
     * How far the cap has sunk, from 0 (up) to 1 (fully pressed). Goes slightly below 0 while bouncing back.
     */
    var press = 0f
        private set

    /**
     * Progress of the ring sent out by the last press, from 0 to 1, or -1 when there is none.
     */
    var ripple = -1f
        private set

    var isPressed = false
        private set

    private var pressAnimator: ValueAnimator? = null
    private var rippleAnimator: ValueAnimator? = null

    fun setPressed(pressed: Boolean) {
        if (pressed == isPressed) return
        isPressed = pressed

        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(press, if (pressed) 1f else 0f).apply {
            duration = if (pressed) PRESS_DURATION_MS else RELEASE_DURATION_MS
            interpolator = if (pressed) pressInterpolator else releaseInterpolator
            addUpdateListener {
                press = it.animatedValue as Float
                onUpdate()
            }
            start()
        }

        if (pressed) {
            rippleAnimator?.cancel()
            rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = RIPPLE_DURATION_MS
                addUpdateListener {
                    ripple = if (it.animatedFraction >= 1f) -1f else it.animatedValue as Float
                    onUpdate()
                }
                start()
            }
        }
    }

    fun cancel() {
        pressAnimator?.cancel()
        rippleAnimator?.cancel()
        press = 0f
        ripple = -1f
        isPressed = false
    }
}
