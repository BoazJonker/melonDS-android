package me.magnum.melonds.ui.emulator.input.componentbuilder

import android.content.Context
import android.view.View
import me.magnum.melonds.R
import me.magnum.melonds.ui.common.LayoutComponentViewBuilder
import me.magnum.melonds.ui.emulator.input.ActionButtonsInputHandler
import me.magnum.melonds.ui.emulator.input.view.AnimatedFaceButtonsView

/**
 * Builds the A/B/X/Y buttons for the emulator screen, or the action buttons when using the stick control style. Unlike the static image used in the layout
 * editor, each button can be animated separately.
 */
class RuntimeButtonsLayoutComponentViewBuilder(private val useActionButtons: Boolean) : LayoutComponentViewBuilder() {

    private companion object {
        val ACTION_BUTTON_DRAWABLES = mapOf(
            ActionButtonsInputHandler.ITEM_INPUT to R.drawable.st_action_item,
            ActionButtonsInputHandler.SWORD_INPUT to R.drawable.st_action_sword,
            ActionButtonsInputHandler.SPIN_INPUT to R.drawable.st_action_spin,
            ActionButtonsInputHandler.ROLL_INPUT to R.drawable.st_action_roll,
        )
    }

    override fun build(context: Context): View {
        return if (useActionButtons) {
            AnimatedFaceButtonsView(context, ACTION_BUTTON_DRAWABLES)
        } else {
            AnimatedFaceButtonsView(context)
        }
    }

    override fun getAspectRatio() = 1f
}
