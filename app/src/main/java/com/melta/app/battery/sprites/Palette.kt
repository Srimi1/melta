package com.melta.app.battery.sprites

/**
 * Pulls a two color theme out of a sprite so the bar can match its companion,
 * for example yellow and orange for Pikachu. Pure Kotlin so it is unit tested.
 */
object Palette {

    /**
     * Returns the two most common vivid colors, or null when the sprite has none
     * (a mostly grey or black Pokémon keeps the user's own colors).
     */
    fun dominant(pixels: IntArray): IntArray? {
        val counts = IntArray(4096)
        val hsv = FloatArray(3)
        for (p in pixels) {
            if (p ushr 24 < 128) continue
            rgbToHsv(p, hsv)
            // Skip outlines, shading and highlights. Only real body colors count.
            if (hsv[1] < 0.25f || hsv[2] < 0.3f) continue
            counts[bucket(p)]++
        }
        val first = counts.indices.maxByOrNull { counts[it] } ?: return null
        if (counts[first] == 0) return null
        val firstColor = bucketColor(first)
        val firstHue = hueOf(firstColor)

        // Second color: the most common one with a clearly different hue.
        var second = -1
        for (i in counts.indices) {
            if (counts[i] == 0 || i == first) continue
            if (hueDistance(hueOf(bucketColor(i)), firstHue) < 25f) continue
            if (second < 0 || counts[i] > counts[second]) second = i
        }
        val secondColor = if (second >= 0) bucketColor(second) else shade(firstColor, 0.7f)
        return intArrayOf(firstColor, secondColor)
    }

    private fun bucket(p: Int): Int {
        val r = (p shr 20) and 0xF
        val g = (p shr 12) and 0xF
        val b = (p shr 4) and 0xF
        return (r shl 8) or (g shl 4) or b
    }

    private fun bucketColor(i: Int): Int {
        val r = ((i shr 8) and 0xF) * 17
        val g = ((i shr 4) and 0xF) * 17
        val b = (i and 0xF) * 17
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun shade(c: Int, f: Float): Int {
        val r = (((c shr 16) and 0xFF) * f).toInt()
        val g = (((c shr 8) and 0xFF) * f).toInt()
        val b = ((c and 0xFF) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun hueOf(c: Int): Float {
        val hsv = FloatArray(3)
        rgbToHsv(c, hsv)
        return hsv[0]
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = kotlin.math.abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    /** Same result as android.graphics.Color.colorToHSV, without needing Android. */
    fun rgbToHsv(c: Int, out: FloatArray) {
        val r = ((c shr 16) and 0xFF) / 255f
        val g = ((c shr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        out[0] = if (h < 0f) h + 360f else h
        out[1] = if (max == 0f) 0f else d / max
        out[2] = max
    }
}
