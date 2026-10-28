package me.magnum.melonds.ui.emulator.input.componentbuilder

import android.content.Context
import android.view.View
import me.magnum.melonds.ui.common.LayoutComponentViewBuilder
import me.magnum.melonds.ui.emulator.input.view.AnimatedFaceButtonsView

/**
 * Builds the A/B/X/Y buttons for the emulator screen. Unlike the static image used in the layout editor, each button can be animated separately.
 */
class RuntimeButtonsLayoutComponentViewBuilder : LayoutComponentViewBuilder() {
    override fun build(context: Context): View {
        return AnimatedFaceButtonsView(context)
    }

    override fun getAspectRatio() = 1f
}
