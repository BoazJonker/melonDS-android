package me.magnum.melonds.ui.emulator.input

import android.view.MotionEvent
import android.view.View
import me.magnum.melonds.common.vibration.TouchVibrator
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.ui.emulator.input.view.ButtonPressAnimator

class SingleButtonInputHandler(inputListener: IInputListener, private val input: Input, enableHapticFeedback: Boolean, touchVibrator: TouchVibrator) : FeedbackInputHandler(inputListener, enableHapticFeedback, touchVibrator) {
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                inputListener.onKeyPress(input)
                performHapticFeedback(v, HapticFeedbackType.KEY_PRESS)
                ButtonPressAnimator.press(v)
            }
            MotionEvent.ACTION_UP -> {
                inputListener.onKeyReleased(input)
                performHapticFeedback(v, HapticFeedbackType.KEY_RELEASE)
                ButtonPressAnimator.release(v)
            }
            MotionEvent.ACTION_CANCEL -> {
                ButtonPressAnimator.release(v)
            }
        }
        return true
    }
}