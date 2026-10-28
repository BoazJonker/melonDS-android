package me.magnum.melonds.ui.emulator.input

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import me.magnum.melonds.ui.emulator.input.view.StickView
import kotlin.math.hypot

/**
 * Handles touches on the analog stick and forwards the stick position to the [VirtualStylus].
 */
class StickInputHandler(private val virtualStylus: VirtualStylus, private val enableHapticFeedback: Boolean) : View.OnTouchListener {

    private var trackedPointerId = MotionEvent.INVALID_POINTER_ID

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val stickView = v as? StickView ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                trackedPointerId = event.getPointerId(0)
                if (enableHapticFeedback) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                updateStick(stickView, event.getX(0), event.getY(0))
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(trackedPointerId)
                if (pointerIndex >= 0) {
                    updateStick(stickView, event.getX(pointerIndex), event.getY(pointerIndex))
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == trackedPointerId) {
                    releaseStick(stickView)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> releaseStick(stickView)
        }
        return true
    }

    private fun updateStick(stickView: StickView, touchX: Float, touchY: Float) {
        val radius = stickView.travelRadius
        if (radius <= 0f) {
            return
        }

        var x = (touchX - stickView.width / 2f) / radius
        var y = (touchY - stickView.height / 2f) / radius
        val magnitude = hypot(x, y)
        if (magnitude > 1f) {
            x /= magnitude
            y /= magnitude
        }

        stickView.setKnob(x, y)
        virtualStylus.setStick(x, y)
    }

    private fun releaseStick(stickView: StickView) {
        trackedPointerId = MotionEvent.INVALID_POINTER_ID
        stickView.resetKnob()
        virtualStylus.releaseStick()
    }
}
