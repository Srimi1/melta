package com.melta.app.battery.sprites

import android.graphics.Bitmap

/**
 * "Melty", Melta's own original mascot: a small droplet of molten energy.
 * Drawn as 16x16 pixel art in code so the app always has a companion, even offline.
 */
object Mascot {

    private val ART = arrayOf(
        "................",
        ".......oo.......",
        "......oyyo......",
        ".....oyyyyo.....",
        "....oyyyyyyo....",
        "...oyyhyyyyyo...",
        "...oyhyyyyyyo...",
        "..oyyyyyyyyyyo..",
        "..oyykyyyykyyo..",
        "..oyykyyyykyyo..",
        "..oyyyyyyyyyyo..",
        "..oyppyyyyppyo..",
        "...oyyykkyyyo...",
        "...ooyyyyyyoo...",
        "....oo.oo.oo....",
        "................",
    )

    private val PALETTE = mapOf(
        'o' to 0xFF5B2A86.toInt(), // outline, deep violet
        'y' to 0xFFFFC93C.toInt(), // body, warm yellow
        'h' to 0xFFFFF4C2.toInt(), // highlight
        'k' to 0xFF2B1B3D.toInt(), // eyes and mouth
        'p' to 0xFFFF8FA3.toInt(), // cheeks
    )

    fun pixels(): IntArray {
        val size = ART.size
        val out = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            out[y * size + x] = PALETTE[ART[y][x]] ?: 0
        }
        return out
    }

    fun bitmap(): Bitmap {
        val size = ART.size
        return Bitmap.createBitmap(pixels(), size, size, Bitmap.Config.ARGB_8888)
    }
}
