package com.melta.app.battery

import com.melta.app.battery.sprites.Pokedex
import com.melta.app.battery.sprites.PokemonEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PokedexTest {

    private val sample = sequenceOf(
        "# comment",
        "",
        "1|Bulbasaur|grass,poison",
        "25|Pikachu|electric",
        "broken line",
        "650|Chespin|grass",
    )

    @Test
    fun parsesEntriesAndSkipsJunk() {
        val list = Pokedex.parse(sample)
        assertEquals(3, list.size)
        assertEquals(PokemonEntry(25, "Pikachu", listOf("electric")), list[1])
        assertEquals(listOf("grass", "poison"), list[0].types)
    }

    @Test
    fun searchByNameNumberAndType() {
        val list = Pokedex.parse(sample)
        assertEquals(listOf(25), Pokedex.search(list, "pika", null).map { it.id })
        assertEquals(listOf(25), Pokedex.search(list, "#025", null).map { it.id })
        assertEquals(listOf(1, 650), Pokedex.search(list, "", "grass").map { it.id })
        assertTrue(Pokedex.search(list, "pika", "grass").isEmpty())
    }

    @Test
    fun animatedSpritesOnlyForGenOneToFive() {
        assertTrue(PokemonEntry(649, "Genesect", listOf("bug", "steel")).hasAnimatedSprite)
        assertFalse(PokemonEntry(650, "Chespin", listOf("grass")).hasAnimatedSprite)
    }

    @Test
    fun bundledPokedexIsComplete() {
        val file = listOf("src/main/assets/pokedex.txt", "app/src/main/assets/pokedex.txt")
            .map(::File).first { it.exists() }
        val list = Pokedex.parse(file.readLines().asSequence())
        assertEquals(1025, list.size)
        assertEquals((1..1025).toList(), list.map { it.id })
        assertTrue(list.all { it.types.isNotEmpty() && it.types.all { t -> t in Pokedex.allTypes } })
    }
}
