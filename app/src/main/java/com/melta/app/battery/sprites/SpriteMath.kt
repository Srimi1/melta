package com.melta.app.battery.sprites

import com.melta.app.core.prefs.SpriteAnchor
import com.melta.app.core.prefs.SpriteSizeMode

/** Pure helpers kept free of Android types so they can be unit tested on the JVM. */
object SpriteMath {

    /**
     * Finds the tight bounding box of visible pixels. PokeAPI sprites have a lot of empty
     * padding, and trimming it lets the companion actually stand on the bar.
     *
     * @return `[left, top, right, bottom]` with right/bottom exclusive, or null if fully transparent.
     */
    fun opaqueBounds(pixels: IntArray, width: Int, height: Int, alphaThreshold: Int = 16): IntArray? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val alpha = pixels[row + x] ushr 24
                if (alpha > alphaThreshold) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < 0) return null
        return intArrayOf(left, top, right + 1, bottom + 1)
    }

    /** Scales (w, h) so that the height becomes [targetHeight], keeping the aspect ratio. */
    fun fitHeight(width: Int, height: Int, targetHeight: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0 || targetHeight <= 0) return 1 to 1
        val w = (width.toLong() * targetHeight / height).toInt().coerceAtLeast(1)
        return w to targetHeight
    }

    /** Height of the canvas every PokeAPI sprite is drawn on. Used as the TRUE_SIZE reference. */
    const val SPRITE_CANVAS = 96

    /**
     * Picks the on-screen size for a trimmed sprite of (width, height).
     *
     * FIT aims for [targetHeight]. TRUE_SIZE aims for the sprite's share of the 96px game canvas,
     * so small Pokémon stay small. Whenever a whole number multiple lands within 20% of the aim we
     * use it, which keeps every pixel perfectly square and the art razor sharp.
     */
    fun pixelScale(
        width: Int,
        height: Int,
        targetHeight: Int,
        mode: SpriteSizeMode,
        referenceHeight: Int = SPRITE_CANVAS,
    ): Pair<Int, Int> {
        if (width <= 0 || height <= 0 || targetHeight <= 0) return 1 to 1
        val aim = when (mode) {
            SpriteSizeMode.FIT -> targetHeight
            SpriteSizeMode.TRUE_SIZE ->
                (height.toLong() * targetHeight / referenceHeight.coerceAtLeast(1)).toInt().coerceIn(1, targetHeight)
        }
        val k = aim / height
        if (k >= 1 && k * height * 5 >= aim * 4) return width * k to height * k
        return fitHeight(width, height, aim)
    }

    /**
     * Horizontal centre of the companion on a bar of [barWidth] pixels.
     * In follow mode it rides the tip of the fill, clamped so it never leaves the screen.
     */
    fun spriteCenterX(
        anchor: SpriteAnchor,
        level: Int,
        barWidth: Float,
        spriteWidth: Float,
        margin: Float,
    ): Float {
        val half = spriteWidth / 2f
        val min = half + margin
        val max = (barWidth - half - margin).coerceAtLeast(min)
        val raw = when (anchor) {
            SpriteAnchor.FOLLOW_LEVEL -> barWidth * level.coerceIn(0, 100) / 100f
            SpriteAnchor.LEFT -> min
            SpriteAnchor.CENTER -> barWidth / 2f
            SpriteAnchor.RIGHT -> max
        }
        return raw.coerceIn(min, max)
    }
}
