package me.magnum.melonds.ui.emulator.input.componentbuilder

import android.content.Context
import android.view.View
import me.magnum.melonds.ui.common.LayoutComponentViewBuilder
import me.magnum.melonds.ui.emulator.input.view.AnimatedFaceButtonsView

/**
 * Builds the A/B/X/Y buttons for the emulator screen, or the action buttons when using the stick control style. Unlike the static image used in the layout
 * editor, each button can be animated separately.
 *
 * @param showLabels Whether to show the names of the action buttons
 */
class RuntimeButtonsLayoutComponentViewBuilder(private val useActionButtons: Boolean, private val showLabels: Boolean) : LayoutComponentViewBuilder() {

    override fun build(context: Context): View {
        return if (useActionButtons) {
            AnimatedFaceButtonsView(context, AnimatedFaceButtonsView.ACTION_BUTTONS, showLabels)
        } else {
            AnimatedFaceButtonsView(context)
        }
    }

    override fun getAspectRatio() = 1f
}
