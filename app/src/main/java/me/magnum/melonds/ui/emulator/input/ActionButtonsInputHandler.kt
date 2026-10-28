package me.magnum.melonds.ui.emulator.input

import android.view.View
import me.magnum.melonds.common.vibration.TouchVibrator
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.ui.emulator.input.view.AnimatedFaceButtonsView

/**
 * Handles the action buttons of the stick control style. The buttons use the same diamond arrangement as A/B/X/Y: Item (top), Sword (right), Spin (bottom)
 * and Roll (left). Each press performs the matching stylus gesture through the [VirtualStylus].
 */
class ActionButtonsInputHandler(
    virtualStylus: VirtualStylus,
    enableHapticFeedback: Boolean,
    touchVibrator: TouchVibrator,
) : MultiButtonInputHandler(ActionListener(virtualStylus), enableHapticFeedback, touchVibrator) {

    companion object {
        val ITEM_INPUT = Input.X
        val SWORD_INPUT = Input.A
        val SPIN_INPUT = Input.B
        val ROLL_INPUT = Input.Y
    }

    override fun getTopInput() = ITEM_INPUT
    override fun getLeftInput() = ROLL_INPUT
    override fun getBottomInput() = SPIN_INPUT
    override fun getRightInput() = SWORD_INPUT

    override fun onPressedInputsChanged(view: View, pressedInputs: List<Input>) {
        (view as? AnimatedFaceButtonsView)?.setPressedInputs(pressedInputs)
    }

    private class ActionListener(private val virtualStylus: VirtualStylus) : IInputListener {
        override fun onKeyPress(key: Input) {
            when (key) {
                ITEM_INPUT -> virtualStylus.useItem()
                SWORD_INPUT -> virtualStylus.swordSlash()
                SPIN_INPUT -> virtualStylus.spinAttack()
                ROLL_INPUT -> virtualStylus.roll()
                else -> Unit
            }
        }

        override fun onKeyReleased(key: Input) {
        }

        override fun onTouch(point: Point) {
        }
    }
}
