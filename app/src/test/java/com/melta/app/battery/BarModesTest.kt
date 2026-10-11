package com.melta.app.battery

import com.melta.app.core.prefs.BarMode
import com.melta.app.core.prefs.BarModes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarModesTest {

    @Test
    fun overlayOnlyWhenAllowed() {
        assertEquals(BarMode.OVERLAY, BarModes.effective(BarMode.OVERLAY, canDrawOverlays = true))
        assertEquals(BarMode.NOTIFICATION, BarModes.effective(BarMode.OVERLAY, canDrawOverlays = false))
        assertEquals(BarMode.NOTIFICATION, BarModes.effective(BarMode.NOTIFICATION, canDrawOverlays = true))
    }

    @Test
    fun formatsChargeTime() {
        assertEquals("1 h 20 m", BarModes.formatChargeTime(80 * 60_000L))
        assertEquals("45 m", BarModes.formatChargeTime(45 * 60_000L))
        assertEquals("2 h", BarModes.formatChargeTime(120 * 60_000L))
        assertEquals("1 m", BarModes.formatChargeTime(10_000L))
        assertNull(BarModes.formatChargeTime(-1L))
        assertNull(BarModes.formatChargeTime(0L))
    }
}
