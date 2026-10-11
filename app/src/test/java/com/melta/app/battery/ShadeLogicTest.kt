package com.melta.app.battery

import com.melta.app.shade.custom.ShadeLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadeLogicTest {

    @Test
    fun opensOnAClearPullOrAFlick() {
        val d = 3f
        assertTrue(ShadeLogic.shouldOpen(24 * d, 0f, d))
        assertTrue(ShadeLogic.shouldOpen(10 * d, 900 * d, d))
        assertFalse(ShadeLogic.shouldOpen(10 * d, 100 * d, d))
        assertFalse(ShadeLogic.shouldOpen(-40 * d, 2000 * d, d))
    }

    private data class N(val app: String, val time: Long)

    @Test
    fun groupsNewestAppFirst() {
        val items = listOf(N("mail", 10), N("chat", 30), N("mail", 50), N("chat", 20))
        val groups = ShadeLogic.groupByApp(items, { it.app }, { it.time })
        assertEquals(listOf("mail", "chat"), groups.map { it.first })
        assertEquals(listOf(50L, 10L), groups[0].second.map { it.time })
        assertEquals(listOf(30L, 20L), groups[1].second.map { it.time })
    }

    @Test
    fun formatsAge() {
        val now = 10_000_000L
        assertEquals("now", ShadeLogic.ago(now, now - 20_000))
        assertEquals("5m", ShadeLogic.ago(now, now - 5 * 60_000))
        assertEquals("3h", ShadeLogic.ago(now, now - 3 * 3_600_000))
        assertEquals("2d", ShadeLogic.ago(now, now - 2 * 86_400_000L))
    }
}
