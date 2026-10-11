package com.melta.app.battery

import com.melta.app.battery.sprites.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaletteTest {

    private val yellow = 0xFFFFEE55.toInt()
    private val red = 0xFFCC2211.toInt()
    private val outline = 0xFF101010.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun picksMainBodyColorThenAccent() {
        val pixels = IntArray(100) { i ->
            when {
                i < 60 -> yellow
                i < 70 -> red
                i < 95 -> outline // lots of outline, must be ignored
                else -> 0 // transparent
            }
        }
        val colors = Palette.dominant(pixels)
        assertNotNull(colors)
        assertEquals(yellow, colors!![0])
        assertEquals(red, colors[1])
    }

    @Test
    fun greyOnlySpriteHasNoPalette() {
        assertNull(Palette.dominant(IntArray(50) { if (it % 2 == 0) outline else white }))
        assertNull(Palette.dominant(IntArray(10)))
    }

    @Test
    fun singleHueGetsADarkerSecondColor() {
        val colors = Palette.dominant(IntArray(20) { yellow })!!
        assertEquals(yellow, colors[0])
        // Second color is a 70% shade of the first.
        assertEquals(0xFFB2A63B.toInt(), colors[1])
    }

    @Test
    fun hsvMatchesAndroid() {
        val out = FloatArray(3)
        Palette.rgbToHsv(0xFFFF0000.toInt(), out)
        assertEquals(0f, out[0], 0.01f)
        assertEquals(1f, out[1], 0.01f)
        Palette.rgbToHsv(0xFF0000FF.toInt(), out)
        assertEquals(240f, out[0], 0.01f)
    }
}
