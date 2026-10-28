package me.magnum.melonds.ui.emulator.input

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Calls [onTap] when the view is tapped (pressed and released quickly without moving), and [onPressedChanged] when the view starts or stops being pressed.
 */
class TapInputHandler(
    private val onPressedChanged: ((isPressed: Boolean) -> Unit)? = null,
    private val onTap: () -> Unit,
) : View.OnTouchListener {

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                onPressedChanged?.invoke(true)
            }
            MotionEvent.ACTION_CANCEL -> onPressedChanged?.invoke(false)
            MotionEvent.ACTION_UP -> {
                onPressedChanged?.invoke(false)
                val touchSlop = ViewConfiguration.get(v.context).scaledTouchSlop
                val isTap = event.eventTime - downTime <= ViewConfiguration.getTapTimeout() * 3 &&
                        abs(event.x - downX) <= touchSlop && abs(event.y - downY) <= touchSlop
                if (isTap) {
                    onTap()
                }
            }
        }
        return true
    }
}
