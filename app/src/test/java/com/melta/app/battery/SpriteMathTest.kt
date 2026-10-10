package com.melta.app.battery

import com.melta.app.battery.sprites.SpriteMath
import com.melta.app.core.prefs.SpriteAnchor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpriteMathTest {

    @Test
    fun findsOpaqueBounds() {
        val w = 5
        val h = 4
        val px = IntArray(w * h)
        px[1 * w + 2] = 0xFF000000.toInt()
        px[2 * w + 3] = 0xFF000000.toInt()
        assertArrayEquals(intArrayOf(2, 1, 4, 3), SpriteMath.opaqueBounds(px, w, h))
    }

    @Test
    fun fullyTransparentHasNoBounds() {
        assertNull(SpriteMath.opaqueBounds(IntArray(16), 4, 4))
    }

    @Test
    fun fitHeightKeepsAspect() {
        assertEquals(40 to 20, SpriteMath.fitHeight(80, 40, 20))
        assertEquals(1 to 1, SpriteMath.fitHeight(0, 0, 20))
    }

    @Test
    fun companionFollowsLevelAndStaysOnScreen() {
        val x50 = SpriteMath.spriteCenterX(SpriteAnchor.FOLLOW_LEVEL, 50, 1000f, 40f, 0f)
        assertEquals(500f, x50, 0.01f)
        val x0 = SpriteMath.spriteCenterX(SpriteAnchor.FOLLOW_LEVEL, 0, 1000f, 40f, 0f)
        assertEquals(20f, x0, 0.01f)
        val x100 = SpriteMath.spriteCenterX(SpriteAnchor.FOLLOW_LEVEL, 100, 1000f, 40f, 0f)
        assertEquals(980f, x100, 0.01f)
        assertEquals(500f, SpriteMath.spriteCenterX(SpriteAnchor.CENTER, 10, 1000f, 40f, 0f), 0.01f)
    }
}
