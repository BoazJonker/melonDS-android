package me.magnum.melonds.ui.emulator.input.componentbuilder

import android.content.Context
import android.view.View
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.ui.common.LayoutComponentViewBuilder
import me.magnum.melonds.ui.emulator.input.view.AnimatedFaceButtonsView

/**
 * Builds the swap screens button for the emulator screen: a single round 3D button, drawn and animated like the action buttons.
 */
class RuntimeSwapScreensLayoutComponentViewBuilder : LayoutComponentViewBuilder() {

    private companion object {
        // One button filling the view: the socket (radius 46 in the drawable) spans the whole width, with the cap centred at y = 48
        val SWAP_SCREENS_BUTTON = listOf(AnimatedFaceButtonsView.ButtonSpec(Input.SWAP_SCREENS, 50f, 48f, 46f, R.drawable.st_swap_screens))
    }

    override fun build(context: Context): View {
        return AnimatedFaceButtonsView(context, SWAP_SCREENS_BUTTON)
    }

    override fun getAspectRatio() = 1f
}
