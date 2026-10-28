package me.magnum.melonds.ui.common.componentbuilders

import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.core.content.ContextCompat
import me.magnum.melonds.R

/**
 * Gives small utility buttons (pause, fast forward, swap screens, etc.) a round parchment backing framed in dark wood and gold, with the icon tinted in dark
 * brown ink, to match the Spirit Tracks styled face buttons.
 */
fun ImageView.applySpiritTracksUtilityButtonStyle() {
    background = ContextCompat.getDrawable(context, R.drawable.st_button_circle)
    imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.stInk))
    scaleType = ImageView.ScaleType.FIT_CENTER
    val padding = (9 * resources.displayMetrics.density).toInt()
    setPadding(padding, padding, padding, padding)
}
