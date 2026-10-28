package me.magnum.melonds.ui.emulator.input

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.ui.emulator.input.view.AnimatedFaceButtonsView

/**
 * Handles the action buttons of the stick control style (Sword, Spin, Item and Roll). Each press performs the matching stylus gesture through the
 * [VirtualStylus]. Several buttons can be pressed at once with different fingers, and sliding a finger onto another button presses it.
 */
class ActionButtonsInputHandler(
    private val virtualStylus: VirtualStylus,
    private val enableHapticFeedback: Boolean,
) : View.OnTouchListener {

    companion object {
        val ITEM_INPUT = Input.X
        val SWORD_INPUT = Input.A
        val SPIN_INPUT = Input.B
        val ROLL_INPUT = Input.Y
    }

    // The button each finger is currently on, by pointer ID
    private val pointerInputs = mutableMapOf<Int, Input?>()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val buttonsView = v as? AnimatedFaceButtonsView ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(i)
                    val isNewPointer = pointerId !in pointerInputs
                    if (event.actionMasked == MotionEvent.ACTION_MOVE || isNewPointer) {
                        val previousInput = pointerInputs[pointerId]
                        val input = buttonsView.getInputAt(event.getX(i), event.getY(i))
                        pointerInputs[pointerId] = input
                        if (input != null && input != previousInput) {
                            performAction(input)
                            if (enableHapticFeedback) {
                                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> pointerInputs.remove(event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pointerInputs.clear()
        }

        buttonsView.setPressedInputs(pointerInputs.values.filterNotNull().toSet())
        return true
    }

    private fun performAction(input: Input) {
        when (input) {
            ITEM_INPUT -> virtualStylus.useItem()
            SWORD_INPUT -> virtualStylus.swordSlash()
            SPIN_INPUT -> virtualStylus.spinAttack()
            ROLL_INPUT -> virtualStylus.roll()
            else -> Unit
        }
    }
}
