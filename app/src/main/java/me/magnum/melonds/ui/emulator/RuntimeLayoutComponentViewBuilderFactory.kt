package me.magnum.melonds.ui.emulator

import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.ui.common.LayoutComponentViewBuilder
import me.magnum.melonds.ui.common.LayoutComponentViewBuilderFactory
import me.magnum.melonds.ui.common.componentbuilders.*
import me.magnum.melonds.ui.emulator.input.componentbuilder.RuntimeButtonsLayoutComponentViewBuilder
import me.magnum.melonds.ui.emulator.input.componentbuilder.RuntimeScreenLayoutComponentViewBuilder
import me.magnum.melonds.ui.emulator.input.componentbuilder.StickLayoutComponentViewBuilder
import me.magnum.melonds.ui.emulator.input.componentbuilder.ToggleableSingleButtonLayoutComponentViewBuilder

/**
 * @param useStickControls Whether to replace the D-pad with an analog stick and the A/B/X/Y buttons with action buttons that control the game through stylus
 * gestures
 * @param showButtonLabels Whether to show the names of the action buttons
 */
class RuntimeLayoutComponentViewBuilderFactory(
    private val useStickControls: Boolean = false,
    private val showButtonLabels: Boolean = true,
) : LayoutComponentViewBuilderFactory {
    private val layoutComponentViewBuilderCache = mutableMapOf<LayoutComponent, LayoutComponentViewBuilder>()

    override fun getLayoutComponentViewBuilder(layoutComponent: LayoutComponent): LayoutComponentViewBuilder {
        return layoutComponentViewBuilderCache.getOrElse(layoutComponent) {
            val builder = when (layoutComponent) {
                LayoutComponent.TOP_SCREEN -> RuntimeScreenLayoutComponentViewBuilder()
                LayoutComponent.BOTTOM_SCREEN -> RuntimeScreenLayoutComponentViewBuilder()
                LayoutComponent.DPAD -> if (useStickControls) StickLayoutComponentViewBuilder() else DpadLayoutComponentViewBuilder()
                LayoutComponent.BUTTONS -> RuntimeButtonsLayoutComponentViewBuilder(useActionButtons = useStickControls, showLabels = showButtonLabels)
                LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE,
                LayoutComponent.BUTTON_MICROPHONE_TOGGLE,
                LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT -> ToggleableSingleButtonLayoutComponentViewBuilder(layoutComponent)
                else -> SingleButtonLayoutComponentViewBuilder(layoutComponent)
            }

            layoutComponentViewBuilderCache[layoutComponent] = builder
            builder
        }
    }
}