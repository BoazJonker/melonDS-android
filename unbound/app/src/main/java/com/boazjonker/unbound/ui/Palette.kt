package com.boazjonker.unbound.ui

import android.graphics.Typeface

/**
 * Colours of the app: a Borrius night sky in deep violet, gold trims, and the red, white and blue of Poké Balls.
 */
object Palette {
    const val NIGHT = 0xFF0B0820.toInt()
    const val NIGHT_MID = 0xFF1E1448.toInt()
    const val NIGHT_HORIZON = 0xFF4A2A7A.toInt()
    const val VIOLET_DARK = 0xFF2A1A5E.toInt()
    const val VIOLET = 0xFF5B36B8.toInt()
    const val VIOLET_LIGHT = 0xFF9C7BFF.toInt()
    const val GLOW = 0xFFE3C6FF.toInt()
    const val AURORA_TEAL = 0xFF46F0C8.toInt()
    const val AURORA_PINK = 0xFFFF6FD8.toInt()

    const val GOLD_LIGHT = 0xFFFFEDB0.toInt()
    const val GOLD = 0xFFF2C14E.toInt()
    const val GOLD_DARK = 0xFF8C5E10.toInt()

    const val RED_LIGHT = 0xFFFF6A4A.toInt()
    const val RED = 0xFFE3350D.toInt()
    const val RED_DARK = 0xFF7E1503.toInt()
    const val BLUE_LIGHT = 0xFF6FA8FF.toInt()
    const val BLUE = 0xFF2F6FE0.toInt()
    const val BLUE_DARK = 0xFF132F78.toInt()
    const val WHITE = 0xFFF8F6FF.toInt()
    const val WHITE_SHADE = 0xFFB9B1D3.toInt()

    const val INK = 0xFF140F22.toInt()
    const val METAL_LIGHT = 0xFF4B3F78.toInt()
    const val METAL = 0xFF261E44.toInt()
    const val METAL_DARK = 0xFF0C0818.toInt()
    const val SHADOW = 0x66000000

    const val POKEMON_YELLOW = 0xFFFFCB05.toInt()
    const val POKEMON_BLUE = 0xFF2A4FA8.toInt()

    val bold: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    val condensed: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
}
