package me.magnum.melonds.ui.emulator.input.componentbuilder

import android.content.Context
import android.view.View
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.ui.common.LayoutComponentViewBuilder
import me.magnum.melonds.ui.emulator.input.view.AnimatedFaceButtonsView

/**
 * Builds a small utility button for the emulator screen (swap screens, blow): a single round 3D button, drawn and animated like the action buttons.
 */
class RuntimeRoundButtonLayoutComponentViewBuilder(private val input: Input, private val capDrawable: Int) : LayoutComponentViewBuilder() {

    override fun build(context: Context): View {
        // One button filling the view: the socket (radius 46 in the drawable) spans the whole width, with the cap centred at y = 48
        return AnimatedFaceButtonsView(context, listOf(AnimatedFaceButtonsView.ButtonSpec(input, 50f, 48f, 46f, capDrawable)))
    }

    override fun getAspectRatio() = 1f
}
