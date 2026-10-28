package me.magnum.melonds.ui.emulator.input

import android.view.View
import me.magnum.melonds.common.vibration.TouchVibrator
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.ui.emulator.input.view.ButtonPressAnimator

class DpadInputHandler(inputListener: IInputListener, enableHapticFeedback: Boolean, touchVibrator: TouchVibrator) : MultiButtonInputHandler(inputListener, enableHapticFeedback, touchVibrator) {
    override fun getTopInput() = Input.UP
    override fun getLeftInput() = Input.LEFT
    override fun getBottomInput() = Input.DOWN
    override fun getRightInput() = Input.RIGHT

    override fun onPressedInputsChanged(view: View, pressedInputs: List<Input>) {
        ButtonPressAnimator.tiltDpad(view, pressedInputs)
    }
}