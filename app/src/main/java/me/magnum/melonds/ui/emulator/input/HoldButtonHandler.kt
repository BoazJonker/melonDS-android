package me.magnum.melonds.ui.emulator.input

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.ui.emulator.input.view.AnimatedFaceButtonsView

/**
 * Handles a round 3D button that does something for as long as it's held, like the Blow button: the button sinks while pressed and [onHeldChanged] is called
 * when it's pressed and released.
 */
class HoldButtonHandler(
    private val input: Input,
    private val enableHapticFeedback: Boolean,
    private val onHeldChanged: (isHeld: Boolean) -> Unit,
) : View.OnTouchListener {

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val buttonView = v as? AnimatedFaceButtonsView ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                buttonView.setPressedInputs(setOf(input))
                if (enableHapticFeedback) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                onHeldChanged(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                buttonView.setPressedInputs(emptySet())
                onHeldChanged(false)
            }
        }
        return true
    }
}
