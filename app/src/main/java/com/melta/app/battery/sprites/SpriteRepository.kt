package com.melta.app.battery.sprites

import android.content.Context
import com.melta.app.core.net.Http
import java.io.File

/**
 * Downloads Pokémon sprites from the public PokeAPI sprite repository and keeps them in app
 * storage. Nothing is bundled in the APK, and each sprite is only fetched once.
 */
object SpriteRepository {

    private const val BASE = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon"

    fun staticUrl(id: Int, shiny: Boolean = false): String =
        if (shiny) "$BASE/shiny/$id.png" else "$BASE/$id.png"

    fun animatedUrl(id: Int, shiny: Boolean = false): String {
        val dir = "$BASE/versions/generation-v/black-white/animated"
        return if (shiny) "$dir/shiny/$id.gif" else "$dir/$id.gif"
    }

    private fun dir(context: Context) = File(context.filesDir, "sprites")

    private fun file(context: Context, id: Int, shiny: Boolean, animated: Boolean): File {
        val name = buildString {
            append(id)
            if (shiny) append("_shiny")
            append(if (animated) ".gif" else ".png")
        }
        return File(dir(context), name)
    }

    /**
     * Returns a local file for the sprite, downloading it if needed. Animated sprites only exist
     * for Gen 1 to 5, so for later Pokémon this quietly returns the static PNG instead.
     */
    suspend fun fetch(context: Context, id: Int, shiny: Boolean, animated: Boolean): File? {
        val wantAnimated = animated && id in 1..PokemonEntry.MAX_ANIMATED_ID
        if (wantAnimated) {
            val gif = file(context, id, shiny, animated = true)
            if (gif.exists() && gif.length() > 0) return gif
            Http.download(animatedUrl(id, shiny), gif)?.let { return it }
        }
        val png = file(context, id, shiny, animated = false)
        if (png.exists() && png.length() > 0) return png
        return Http.download(staticUrl(id, shiny), png)
    }

    /** Size of the sprite cache on disk, shown in settings. */
    fun cacheSizeBytes(context: Context): Long =
        dir(context).listFiles()?.sumOf { it.length() } ?: 0L

    fun clearCache(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}
