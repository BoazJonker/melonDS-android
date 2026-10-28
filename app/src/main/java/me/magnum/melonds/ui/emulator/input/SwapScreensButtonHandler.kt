package me.magnum.melonds.ui.emulator.input

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.ui.emulator.input.view.AnimatedFaceButtonsView

/**
 * Handles the 3D swap screens button: the button sinks while pressed, and the screens are swapped when it's released over the button.
 */
class SwapScreensButtonHandler(private val enableHapticFeedback: Boolean, private val onSwap: () -> Unit) : View.OnTouchListener {

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val buttonView = v as? AnimatedFaceButtonsView ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                buttonView.setPressedInputs(setOf(Input.SWAP_SCREENS))
                if (enableHapticFeedback) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }
            MotionEvent.ACTION_UP -> {
                buttonView.setPressedInputs(emptySet())
                val isInside = event.x >= 0 && event.x < v.width && event.y >= 0 && event.y < v.height
                if (isInside) {
                    onSwap()
                }
            }
            MotionEvent.ACTION_CANCEL -> buttonView.setPressedInputs(emptySet())
        }
        return true
    }
}
