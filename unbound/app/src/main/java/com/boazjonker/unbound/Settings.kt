package com.boazjonker.unbound

import android.content.Context

class Settings(context: Context) {

    enum class ScreenFilter(val label: String) {
        // Sharp pixels with smooth edges between them
        SHARP("Sharp"),
        SMOOTH("Smooth"),
        PIXELS("Pixel perfect"),
    }

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var screenFilter: ScreenFilter
        get() = ScreenFilter.entries.getOrNull(prefs.getInt("screenFilter", 0)) ?: ScreenFilter.SHARP
        set(value) = prefs.edit().putInt("screenFilter", value.ordinal).apply()

    var fastForwardSpeed: Int
        get() = prefs.getInt("fastForwardSpeed", 3)
        set(value) = prefs.edit().putInt("fastForwardSpeed", value).apply()

    var isHapticsEnabled: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) = prefs.edit().putBoolean("haptics", value).apply()

    var isBackgroundAnimated: Boolean
        get() = prefs.getBoolean("backgroundAnimated", true)
        set(value) = prefs.edit().putBoolean("backgroundAnimated", value).apply()

    /**
     * Opacity of the on-screen controls, in percent.
     */
    var controlsOpacity: Int
        get() = prefs.getInt("controlsOpacity", 100)
        set(value) = prefs.edit().putInt("controlsOpacity", value).apply()

    var volume: Int
        get() = prefs.getInt("volume", 100)
        set(value) = prefs.edit().putInt("volume", value).apply()

    /**
     * Whether the fast-forward button shows next to L and R. Off by default; fast-forward stays available from a controller.
     */
    var isFastForwardButtonShown: Boolean
        get() = prefs.getBoolean("fastForwardButton", false)
        set(value) = prefs.edit().putBoolean("fastForwardButton", value).apply()

    /**
     * Whether the tip about opening the options with the back button has been shown.
     */
    var isBackTipShown: Boolean
        get() = prefs.getBoolean("backTipShown", false)
        set(value) = prefs.edit().putBoolean("backTipShown", value).apply()

    /**
     * Minutes between automatic quick saves while playing, or 0 to only save when leaving the game.
     */
    var autosaveMinutes: Int
        get() = prefs.getInt("autosaveMinutes", 5)
        set(value) = prefs.edit().putInt("autosaveMinutes", value).apply()
}
