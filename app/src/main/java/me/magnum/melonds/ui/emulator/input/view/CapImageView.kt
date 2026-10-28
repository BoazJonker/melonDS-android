package me.magnum.melonds.ui.emulator.input.view

import android.content.Context
import androidx.appcompat.widget.AppCompatImageView

/**
 * A 3D button made of a socket (the background) and a cap (the image). Pressing the button scrolls the content, so that the cap sinks into the socket while
 * the socket stays in place.
 *
 * @param pressDepthFraction How far the cap sinks when pressed, as a fraction of the view's height
 * @param isRound Whether the button is round (otherwise it's a plank shaped button)
 */
class CapImageView(context: Context, val pressDepthFraction: Float, val isRound: Boolean = false) : AppCompatImageView(context)
