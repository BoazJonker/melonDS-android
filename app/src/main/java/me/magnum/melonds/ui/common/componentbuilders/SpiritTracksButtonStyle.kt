package me.magnum.melonds.ui.common.componentbuilders

import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.core.content.ContextCompat
import me.magnum.melonds.R

/**
 * Gives small utility buttons (pause, fast forward, swap screens, etc.) a round 3D parchment button as a backing, with the icon tinted in dark brown ink, to
 * match the Spirit Tracks styled face buttons.
 */
fun ImageView.applySpiritTracksUtilityButtonStyle() {
    background = ContextCompat.getDrawable(context, R.drawable.st_button_round)
    imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.stInk))
    scaleType = ImageView.ScaleType.FIT_CENTER
    // Keep the icon within the face of the button's cap, which sits slightly above the centre
    val density = resources.displayMetrics.density
    setPadding((11 * density).toInt(), (9 * density).toInt(), (11 * density).toInt(), (13 * density).toInt())
}
