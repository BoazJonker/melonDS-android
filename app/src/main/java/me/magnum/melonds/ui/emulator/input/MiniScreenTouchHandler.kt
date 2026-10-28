package me.magnum.melonds.ui.emulator.input

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Handles touches on the small screen window. A tap swaps the screens. A drag (like pulling the train's whistle cord, which sits under the window) calls
 * [onPulled] so the window can get out of the way, and the whole drag is passed on to the main screen, as if the window had not been there.
 *
 * @param mainScreenView The main screen, when it shows the DS touch screen. Null when drags shouldn't be passed on
 */
class MiniScreenTouchHandler(
    private val mainScreenView: View?,
    private val mainScreenHandler: View.OnTouchListener?,
    private val onPressedChanged: (isPressed: Boolean) -> Unit,
    private val onPulled: () -> Unit,
    private val onTap: () -> Unit,
) : View.OnTouchListener {

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var isPulling = false
    private val miniLocation = IntArray(2)
    private val mainLocation = IntArray(2)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                isPulling = false
                onPressedChanged(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isPulling) {
                    forward(v, event, MotionEvent.ACTION_MOVE)
                } else if (mainScreenView != null && mainScreenHandler != null) {
                    val touchSlop = ViewConfiguration.get(v.context).scaledTouchSlop
                    if (hypot(event.x - downX, event.y - downY) > touchSlop) {
                        isPulling = true
                        onPressedChanged(false)
                        onPulled()
                        // Start the drag where the finger first landed, so the game sees the whole pull
                        val (dx, dy) = offsetToMainScreen(v)
                        val down = MotionEvent.obtain(downTime, event.eventTime, MotionEvent.ACTION_DOWN, downX + dx, downY + dy, 0)
                        mainScreenHandler.onTouch(mainScreenView, down)
                        down.recycle()
                        forward(v, event, MotionEvent.ACTION_MOVE)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isPulling) {
                    isPulling = false
                    forward(v, event, MotionEvent.ACTION_UP)
                    return true
                }

                onPressedChanged(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val touchSlop = ViewConfiguration.get(v.context).scaledTouchSlop
                    val isTap = event.eventTime - downTime <= ViewConfiguration.getTapTimeout() * 3 &&
                            abs(event.x - downX) <= touchSlop && abs(event.y - downY) <= touchSlop
                    if (isTap) {
                        onTap()
                    }
                }
            }
        }
        return true
    }

    private fun forward(v: View, event: MotionEvent, action: Int) {
        val mainView = mainScreenView ?: return
        val handler = mainScreenHandler ?: return
        val (dx, dy) = offsetToMainScreen(v)
        val forwarded = MotionEvent.obtain(event)
        forwarded.action = action
        forwarded.offsetLocation(dx, dy)
        handler.onTouch(mainView, forwarded)
        forwarded.recycle()
    }

    private fun offsetToMainScreen(v: View): Pair<Float, Float> {
        v.getLocationOnScreen(miniLocation)
        mainScreenView?.getLocationOnScreen(mainLocation)
        return (miniLocation[0] - mainLocation[0]).toFloat() to (miniLocation[1] - mainLocation[1]).toFloat()
    }
}
