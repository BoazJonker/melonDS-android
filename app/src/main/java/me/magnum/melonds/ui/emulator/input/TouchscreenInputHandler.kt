package me.magnum.melonds.ui.emulator.input

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.View
import me.magnum.melonds.MelonEmulator.onScreenRelease
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.Point

/**
 * @param virtualStylus When the stick control style is used, real touches are reported to it so that real touches take priority over the stick and action
 * buttons
 * @param onTouchStarted Called when the player starts touching the screen
 */
class TouchscreenInputHandler(
    inputListener: IInputListener,
    private val virtualStylus: VirtualStylus? = null,
    private val onTouchStarted: (() -> Unit)? = null,
) : BaseInputHandler(inputListener) {
    private val touchPoint: Point = Point()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                onTouchStarted?.invoke()
                virtualStylus?.onRealTouchStarted()
                inputListener.onKeyPress(Input.TOUCHSCREEN)
                inputListener.onTouch(normalizeTouchCoordinates(event, v.width, v.height))
            }
            MotionEvent.ACTION_MOVE -> {
                inputListener.onTouch(normalizeTouchCoordinates(event, v.width, v.height))
            }
            MotionEvent.ACTION_UP -> {
                inputListener.onKeyReleased(Input.TOUCHSCREEN)
                onScreenRelease()
                virtualStylus?.onRealTouchEnded()
            }
        }
        return true
    }

    private fun normalizeTouchCoordinates(event: MotionEvent, viewWidth: Int, viewHeight: Int): Point {
        var averageTouchX = 0f
        var averageTouchY = 0f
        val pointerCoordinates = PointerCoords()

        // Average out touch positions. Even though the DS has a resistive touch screen, some games rely on the nuances
        // of this technology for some mechanics. Averaging out the coordinates of the touch position allows us to
        // simulate those nuances to some degree
        for (i in 0 until event.pointerCount) {
            event.getPointerCoords(i, pointerCoordinates)
            averageTouchX += pointerCoordinates.x
            averageTouchY += pointerCoordinates.y
        }
        averageTouchX /= event.pointerCount
        averageTouchY /= event.pointerCount

        touchPoint.x = (averageTouchX / viewWidth * 256).toInt().coerceIn(0, 255)
        touchPoint.y = (averageTouchY / viewHeight * 192).toInt().coerceIn(0, 191)
        return touchPoint
    }
}