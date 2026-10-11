package com.melta.app.battery

import com.melta.app.battery.sprites.SpriteMath
import com.melta.app.core.prefs.SpriteAnchor
import com.melta.app.core.prefs.SpriteSizeMode
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

    @Test
    fun smallSpritesScaleByWholePixels() {
        // 20px tall into a 66px slot: 3x gives 60px, within 20% of the aim, so stay crisp.
        assertEquals(36 to 60, SpriteMath.pixelScale(12, 20, 66, SpriteSizeMode.FIT))
    }

    @Test
    fun awkwardSizesFallBackToExactHeight() {
        // 40px into 66px: 1x would be far too small, so scale to exactly 66.
        assertEquals(33 to 66, SpriteMath.pixelScale(20, 40, 66, SpriteSizeMode.FIT))
    }

    @Test
    fun tallSpritesShrinkToFit() {
        assertEquals(30 to 48, SpriteMath.pixelScale(60, 96, 48, SpriteSizeMode.FIT))
    }

    @Test
    fun trueSizeKeepsRelativeSizes() {
        val small = SpriteMath.pixelScale(24, 24, 96, SpriteSizeMode.TRUE_SIZE)
        val big = SpriteMath.pixelScale(90, 90, 96, SpriteSizeMode.TRUE_SIZE)
        assertEquals(24, small.second)
        assertEquals(90, big.second)
    }
}
