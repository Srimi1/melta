package com.melta.app.battery

import com.melta.app.core.prefs.CardTheme
import com.melta.app.shade.CardThemeSpec
import com.melta.app.shade.DailyPokemon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShadeTest {

    @Test
    fun dailyPokemonIsStableAndInRange() {
        for (day in 0L..5000L) {
            val id = DailyPokemon.forDate(day)
            assertTrue("day $day gave $id", id in 1..DailyPokemon.COUNT)
            assertEquals(id, DailyPokemon.forDate(day))
        }
        // Consecutive days should not all be the same Pokémon.
        assertTrue((20000L..20030L).map { DailyPokemon.forDate(it) }.toSet().size > 25)
    }

    @Test
    fun randomNeverRepeatsCurrent() {
        val rnd = Random(1)
        repeat(500) { assertNotEquals(25, DailyPokemon.random(25, rnd)) }
    }

    @Test
    fun systemThemeKeepsTheShadeLook() {
        val spec = CardThemeSpec.of(CardTheme.SYSTEM)
        assertNull(spec.background)
        assertFalse(spec.overridesBarColors)
    }

    @Test
    fun otherThemesBringTheirOwnColors() {
        CardTheme.entries.filter { it != CardTheme.SYSTEM }.forEach {
            val spec = CardThemeSpec.of(it)
            assertTrue("$it needs a background", spec.background != null)
            assertTrue("$it needs bar colors", spec.overridesBarColors)
        }
        assertTrue(CardThemeSpec.of(CardTheme.GAMEBOY).gameBoySprite)
    }
}
