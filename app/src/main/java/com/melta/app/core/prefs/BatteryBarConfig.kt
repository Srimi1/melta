package com.melta.app.core.prefs

/** Where the companion stands along the bar. */
enum class SpriteAnchor { FOLLOW_LEVEL, LEFT, CENTER, RIGHT }

/** How big each Pokémon is drawn. */
enum class SpriteSizeMode {
    /** Every companion is scaled to the same height. */
    FIT,

    /** Keeps the relative sizes from the games, so Pichu stays smaller than Snorlax. */
    TRUE_SIZE,
}

/** Where the bar itself is drawn. */
enum class BarPosition { STATUS_BAR_BOTTOM, SCREEN_TOP }

enum class FillStyle { SOLID, GRADIENT, RAINBOW, STRIPES, DOTS, CHECKER, CUSTOM }

enum class ChargeEffect { AUTO, SPARKS, BOLTS, BUBBLES, FLAMES, LEAVES, HEARTS, SNOW, NONE }

/**
 * Everything the battery bar needs to draw itself. Kept as one immutable value so the
 * overlay service can diff it cheaply and only reload the sprite when sprite fields change.
 */
data class BatteryBarConfig(
    val enabled: Boolean = false,

    // Companion
    val pokemonId: Int = 25,
    val shiny: Boolean = false,
    val animated: Boolean = true,
    val useCustomSprite: Boolean = false,
    val customSpritePath: String? = null,
    val spriteSizeDp: Int = 22,
    val sizeMode: SpriteSizeMode = SpriteSizeMode.FIT,
    val anchor: SpriteAnchor = SpriteAnchor.FOLLOW_LEVEL,
    // Sprites face left, so by default flip them to look along the bar toward the empty part.
    val flip: Boolean = true,
    val bob: Boolean = true,

    // Bar
    val position: BarPosition = BarPosition.STATUS_BAR_BOTTOM,
    val barThicknessDp: Int = 3,
    val opacity: Float = 0.8f,
    val fillStyle: FillStyle = FillStyle.GRADIENT,
    val colorStart: Int = 0xFFFFD54F.toInt(),
    val colorEnd: Int = 0xFFFF7043.toInt(),
    /** Take the bar colors from the companion itself, e.g. a yellow bar for Pikachu. */
    val matchColors: Boolean = true,
    val customTilePath: String? = null,

    // Charging
    val effect: ChargeEffect = ChargeEffect.AUTO,
    val effectIntensity: Float = 0.6f,
    val celebrateFull: Boolean = true,

    // Extras
    val showPercent: Boolean = false,
    val lowThreshold: Int = 20,
    val hideInLandscape: Boolean = true,
    val hideInFullscreen: Boolean = true,
) {
    /** Identity of the sprite. When this changes the service reloads the bitmap. */
    val spriteKey: String
        get() = if (useCustomSprite && customSpritePath != null) {
            "custom:$customSpritePath:$spriteSizeDp:$sizeMode"
        } else {
            "pokemon:$pokemonId:$shiny:$animated:$spriteSizeDp:$sizeMode"
        }

    companion object {
        const val MAX_OPACITY = 0.8f
        const val MIN_OPACITY = 0.2f
    }
}
