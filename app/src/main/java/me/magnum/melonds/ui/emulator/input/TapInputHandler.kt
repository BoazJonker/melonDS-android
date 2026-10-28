package me.magnum.melonds.ui.emulator.input

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Calls [onTap] when the view is tapped (pressed and released quickly without moving).
 */
class TapInputHandler(private val onTap: () -> Unit) : View.OnTouchListener {

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
            }
            MotionEvent.ACTION_UP -> {
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
