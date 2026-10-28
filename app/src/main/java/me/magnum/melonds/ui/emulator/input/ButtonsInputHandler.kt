package me.magnum.melonds.ui.emulator.input

import android.view.View
import me.magnum.melonds.common.vibration.TouchVibrator
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.ui.emulator.input.view.AnimatedFaceButtonsView

class ButtonsInputHandler(inputListener: IInputListener, enableHapticFeedback: Boolean, touchVibrator: TouchVibrator) : MultiButtonInputHandler(inputListener, enableHapticFeedback, touchVibrator) {
    override fun getTopInput() = Input.X
    override fun getLeftInput() = Input.Y
    override fun getBottomInput() = Input.B
    override fun getRightInput() = Input.A

    override fun onPressedInputsChanged(view: View, pressedInputs: List<Input>) {
        (view as? AnimatedFaceButtonsView)?.setPressedInputs(pressedInputs)
    }
}