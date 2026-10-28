package me.magnum.melonds.ui.common.componentbuilders

import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.ui.common.LayoutComponentViewBuilder
import me.magnum.melonds.ui.emulator.input.view.CapImageView

class SingleButtonLayoutComponentViewBuilder(private val layoutComponent: LayoutComponent) : LayoutComponentViewBuilder() {

    private companion object {
        // These buttons have their own fully styled drawables. Every other button is a utility button that gets a round backing
        val FACE_BUTTONS = setOf(LayoutComponent.BUTTON_L, LayoutComponent.BUTTON_R, LayoutComponent.BUTTON_START, LayoutComponent.BUTTON_SELECT)

        // In the plank drawables (100x100), the cap sinks 4 units into the socket
        const val PLANK_PRESS_DEPTH_FRACTION = 0.04f
        // In the round button drawables, the cap sinks 5 units
        const val ROUND_PRESS_DEPTH_FRACTION = 0.05f
    }

    override fun build(context: Context): View {
        return if (layoutComponent == LayoutComponent.BUTTON_SWAP_SCREENS) {
            // Round 3D button, like the action buttons
            CapImageView(context, ROUND_PRESS_DEPTH_FRACTION, isRound = true).apply {
                background = ContextCompat.getDrawable(context, R.drawable.st_disk_base)
                setImageResource(R.drawable.st_swap_screens)
            }
        } else if (layoutComponent in FACE_BUTTONS) {
            // 3D button: the socket is the background and the cap is the image, so that the cap can sink into the socket when pressed
            CapImageView(context, PLANK_PRESS_DEPTH_FRACTION).apply {
                background = ContextCompat.getDrawable(context, R.drawable.st_plank_base)
                setImageResource(getInputDrawable())
            }
        } else {
            ImageView(context).apply {
                setImageResource(getInputDrawable())
                applySpiritTracksUtilityButtonStyle()
            }
        }
    }

    override fun getAspectRatio() = 1f

    private fun getInputDrawable(): Int {
        return when (layoutComponent) {
            LayoutComponent.BUTTON_L -> R.drawable.button_l
            LayoutComponent.BUTTON_R -> R.drawable.button_r
            LayoutComponent.BUTTON_START -> R.drawable.button_start
            LayoutComponent.BUTTON_SELECT -> R.drawable.button_select
            LayoutComponent.BUTTON_HINGE -> R.drawable.button_toggle_lid
            LayoutComponent.BUTTON_PAUSE -> R.drawable.button_pause
            LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE -> R.drawable.button_fast_forward
            LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT -> R.drawable.ic_touch_enabled
            LayoutComponent.BUTTON_RESET -> R.drawable.button_reset
            LayoutComponent.BUTTON_SWAP_SCREENS -> R.drawable.button_swap_screens
            LayoutComponent.BUTTON_QUICK_SAVE -> R.drawable.button_quick_save
            LayoutComponent.BUTTON_QUICK_LOAD -> R.drawable.button_quick_load
            LayoutComponent.BUTTON_REWIND -> R.drawable.button_rewind
            LayoutComponent.BUTTON_MICROPHONE_TOGGLE -> R.drawable.button_microphone
            else -> -1
        }
    }
}