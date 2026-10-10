package com.melta.app.battery

import com.melta.app.battery.creations.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelGridTest {

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    @Test
    fun paintWithMirror() {
        val g = PixelGrid(4)
        g.paint(0, 1, red, mirror = true)
        assertEquals(red, g[0, 1])
        assertEquals(red, g[3, 1])
        assertEquals(0, g[1, 1])
    }

    @Test
    fun floodFillStopsAtBorders() {
        val g = PixelGrid(4)
        for (y in 0 until 4) g.paint(2, y, red, mirror = false)
        g.fill(0, 0, blue)
        for (y in 0 until 4) {
            assertEquals(blue, g[0, y])
            assertEquals(blue, g[1, y])
            assertEquals(red, g[2, y])
            assertEquals(0, g[3, y])
        }
    }

    @Test
    fun undoRestoresWholeStroke() {
        val g = PixelGrid(4)
        assertFalse(g.canUndo)
        g.checkpoint()
        g.paint(0, 0, red, false)
        g.paint(1, 0, red, false)
        assertTrue(g.canUndo)
        g.undo()
        assertTrue(g.isEmpty)
    }

    @Test
    fun undoHistoryIsBounded() {
        val g = PixelGrid(2)
        repeat(PixelGrid.MAX_UNDO + 10) { g.checkpoint() }
        var undos = 0
        while (g.canUndo) {
            g.undo(); undos++
        }
        assertEquals(PixelGrid.MAX_UNDO, undos)
    }
}
