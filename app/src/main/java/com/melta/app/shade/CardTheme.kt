package com.melta.app.shade

import com.melta.app.core.prefs.CardTheme

/**
 * Plain data for a theme, kept free of Android types so it is unit tested.
 * A null [background] means "use the shade's own background".
 */
data class CardThemeSpec(
    val label: String,
    val background: Int?,
    val rim: Int?,
    val barStart: Int?,
    val barEnd: Int?,
    val gameBoySprite: Boolean = false,
    val stars: Boolean = false,
    val pokedexLights: Boolean = false,
    val gradientRim: Boolean = false,
) {
    /** Themes other than System pick their own bar colors. */
    val overridesBarColors: Boolean get() = barStart != null && barEnd != null

    companion object {
        fun of(theme: CardTheme): CardThemeSpec = when (theme) {
            CardTheme.SYSTEM -> CardThemeSpec("System", null, null, null, null)
            CardTheme.POKEDEX -> CardThemeSpec(
                "Pokédex", 0xFFDC0A2D.toInt(), 0xFFFFFFFF.toInt(),
                0xFFFFFFFF.toInt(), 0xFFFFDE00.toInt(), pokedexLights = true,
            )
            CardTheme.GAMEBOY -> CardThemeSpec(
                "Game Boy", 0xFF9BBC0F.toInt(), 0xFF306230.toInt(),
                0xFF306230.toInt(), 0xFF0F380F.toInt(), gameBoySprite = true,
            )
            CardTheme.NEON -> CardThemeSpec(
                "Neon", 0xFF0B0B1A.toInt(), 0xFF00F0FF.toInt(),
                0xFFFF2BD6.toInt(), 0xFF00F0FF.toInt(), gradientRim = true,
            )
            CardTheme.NIGHT -> CardThemeSpec(
                "Night", 0xFF14163A.toInt(), 0xFF3A3F8F.toInt(),
                0xFF8EA6FF.toInt(), 0xFFE2B6FF.toInt(), stars = true,
            )
        }
    }
}
