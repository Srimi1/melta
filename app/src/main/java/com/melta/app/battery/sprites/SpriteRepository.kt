package com.melta.app.battery.sprites

import android.content.Context
import com.melta.app.core.net.Http
import java.io.File

/**
 * Where Pokémon sprites come from. Every still sprite (normal and shiny) ships inside the APK,
 * gathered at build time from the public PokeAPI sprite repository. Animated sprites are
 * downloaded once on first use and kept in app storage.
 */
object SpriteRepository {

    private const val BASE = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon"

    fun staticUrl(id: Int, shiny: Boolean = false): String =
        if (shiny) "$BASE/shiny/$id.png" else "$BASE/$id.png"

    fun animatedUrl(id: Int, shiny: Boolean = false): String {
        val dir = "$BASE/versions/generation-v/black-white/animated"
        return if (shiny) "$dir/shiny/$id.gif" else "$dir/$id.gif"
    }

    // ---------------------------------------------------------------- bundled pack

    /** Path of a sprite inside the APK. The build packs every Pokémon here (see SpritePackTask). */
    fun assetPath(id: Int, shiny: Boolean): String = if (shiny) "sprites/shiny/$id.png" else "sprites/$id.png"

    @Volatile
    private var packAvailable: Boolean? = null

    /** True when this build carries the offline sprite pack. Checked once using Pikachu. */
    fun hasPack(context: Context): Boolean =
        packAvailable ?: runCatching { context.assets.open(assetPath(25, false)).close(); true }
            .getOrDefault(false)
            .also { packAvailable = it }

    /** Image model for thumbnails: the bundled asset when present, otherwise the web URL. */
    fun thumbnail(context: Context, id: Int, shiny: Boolean = false): String =
        if (hasPack(context)) "file:///android_asset/${assetPath(id, shiny)}" else staticUrl(id, shiny)

    // ---------------------------------------------------------------- downloads

    private fun dir(context: Context) = File(context.filesDir, "sprites")

    private fun file(context: Context, id: Int, shiny: Boolean, animated: Boolean): File {
        val name = buildString {
            append(id)
            if (shiny) append("_shiny")
            append(if (animated) ".gif" else ".png")
        }
        return File(dir(context), name)
    }

    /** Animated Gen 1 to 5 sprite, downloaded once on first use. Null if unavailable or offline. */
    suspend fun fetchAnimated(context: Context, id: Int, shiny: Boolean): File? {
        if (id !in 1..PokemonEntry.MAX_ANIMATED_ID) return null
        val gif = file(context, id, shiny, animated = true)
        if (gif.exists() && gif.length() > 0) return gif
        return Http.download(animatedUrl(id, shiny), gif)
    }

    /** Still sprite download, only used by builds made without the sprite pack. */
    suspend fun fetchStatic(context: Context, id: Int, shiny: Boolean): File? {
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
