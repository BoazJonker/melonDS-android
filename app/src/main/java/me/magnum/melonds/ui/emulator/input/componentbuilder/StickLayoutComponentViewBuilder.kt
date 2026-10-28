package me.magnum.melonds.ui.emulator.input.componentbuilder

import android.content.Context
import android.view.View
import me.magnum.melonds.ui.common.LayoutComponentViewBuilder
import me.magnum.melonds.ui.emulator.input.view.StickView

/**
 * Builds the analog stick that replaces the D-pad when using the stick control style.
 */
class StickLayoutComponentViewBuilder : LayoutComponentViewBuilder() {
    override fun build(context: Context): View {
        return StickView(context)
    }

    override fun getAspectRatio() = 1f
}
