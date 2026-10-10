package com.melta.app.battery.sprites

import android.content.Context

data class PokemonEntry(val id: Int, val name: String, val types: List<String>) {
    /** Gen 1 to 5 have animated Black/White sprites in the PokeAPI sprite repo. */
    val hasAnimatedSprite: Boolean get() = id in 1..MAX_ANIMATED_ID

    val number: String get() = "#" + id.toString().padStart(4, '0')

    companion object {
        const val MAX_ANIMATED_ID = 649
    }
}

/**
 * The list of every Pokémon (names and types only, no art) ships in assets/pokedex.txt so
 * search and type filters work offline. Sprites are fetched on demand by [SpriteRepository].
 */
object Pokedex {

    @Volatile
    private var cache: List<PokemonEntry>? = null

    val allTypes = listOf(
        "normal", "fire", "water", "electric", "grass", "ice", "fighting", "poison", "ground",
        "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy",
    )

    fun all(context: Context): List<PokemonEntry> =
        cache ?: synchronized(this) {
            cache ?: context.assets.open("pokedex.txt").bufferedReader().useLines { parse(it) }
                .also { cache = it }
        }

    fun find(context: Context, id: Int): PokemonEntry? = all(context).firstOrNull { it.id == id }

    /** Parses lines in the form `id|Name|type1,type2`. Comments and blank lines are skipped. */
    fun parse(lines: Sequence<String>): List<PokemonEntry> = lines
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size < 3) return@mapNotNull null
            val id = parts[0].toIntOrNull() ?: return@mapNotNull null
            PokemonEntry(id, parts[1], parts[2].split(',').filter { it.isNotBlank() })
        }
        .toList()

    fun search(all: List<PokemonEntry>, query: String, type: String?): List<PokemonEntry> {
        val q = query.trim().lowercase()
        return all.filter { p ->
            (type == null || type in p.types) &&
                (q.isEmpty() || p.name.lowercase().contains(q) || p.id.toString() == q.trimStart('#', '0'))
        }
    }
}
