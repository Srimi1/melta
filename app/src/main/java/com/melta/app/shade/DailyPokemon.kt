package com.melta.app.shade

import kotlin.random.Random

/** Picks the "Pokémon of the day". Pure Kotlin so it is unit tested. */
object DailyPokemon {

    const val COUNT = 1025

    /** The same Pokémon all day for everyone, a different one tomorrow. */
    fun forDate(epochDay: Long): Int {
        // Multiplicative hash spreads consecutive days across the whole Pokédex.
        val mixed = (epochDay * 2_654_435_761L) xor (epochDay ushr 7)
        return (Math.floorMod(mixed, COUNT.toLong()) + 1).toInt()
    }

    /** A random Pokémon that is not [except]. */
    fun random(except: Int, random: Random = Random): Int {
        var id: Int
        do {
            id = random.nextInt(1, COUNT + 1)
        } while (id == except)
        return id
    }
}
