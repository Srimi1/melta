package com.melta.app.battery.sprites

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.melta.app.core.prefs.BatteryBarConfig
import com.melta.app.core.prefs.SpriteSizeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A sprite ready to draw: a drawable with bounds already set to its on-screen size,
 * the companion's types (for the Auto effect) and its two theme colors (for matching the bar).
 */
class LoadedSprite(val drawable: Drawable, val types: List<String>, val palette: IntArray? = null) {
    val width: Int get() = drawable.bounds.width()
    val height: Int get() = drawable.bounds.height()
}

/**
 * Turns a [BatteryBarConfig] into a small, crisp, ready-to-draw sprite.
 * Still sprites come from the pack bundled in the APK, so this works offline. They are trimmed
 * and scaled with whole pixel steps where possible, and the result is only a few KB of memory.
 */
object SpriteLoader {

    suspend fun load(context: Context, config: BatteryBarConfig, targetHeightPx: Int): LoadedSprite =
        withContext(Dispatchers.IO) {
            val res = context.resources

            if (config.useCustomSprite && config.customSpritePath != null) {
                // Custom art has no game canvas to compare against, so it always fills the height.
                BitmapFactory.decodeFile(config.customSpritePath)?.let { src ->
                    val palette = palette(src)
                    return@withContext LoadedSprite(toDrawable(res, src, targetHeightPx, SpriteSizeMode.FIT), emptyList(), palette)
                }
            }

            val id = config.pokemonId
            val types = Pokedex.find(context, id)?.types.orEmpty()
            val still = decodeAsset(context, SpriteRepository.assetPath(id, config.shiny))
                ?: SpriteRepository.fetchStatic(context, id, config.shiny)?.let { BitmapFactory.decodeFile(it.absolutePath) }
            val palette = still?.let(::palette)

            if (config.animated && Build.VERSION.SDK_INT >= 28) {
                val gif = SpriteRepository.fetchAnimated(context, id, config.shiny)
                val animatedHeight = still?.let { stillHeight(it, targetHeightPx, config.sizeMode) } ?: targetHeightPx
                val anim = gif?.let { decodeAnimated(it, animatedHeight) }
                if (anim != null) {
                    still?.recycle()
                    return@withContext LoadedSprite(anim, types, palette)
                }
            }

            val drawable = still?.let { toDrawable(res, it, targetHeightPx, config.sizeMode) }
                ?: toDrawable(res, Mascot.bitmap(), targetHeightPx, SpriteSizeMode.FIT)
            LoadedSprite(drawable, types, palette)
        }

    private fun decodeAsset(context: Context, path: String): Bitmap? =
        runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()

    private fun palette(src: Bitmap): IntArray? {
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        return Palette.dominant(pixels)
    }

    /** On-screen height the still sprite would get, so the animated one matches it. */
    private fun stillHeight(src: Bitmap, targetHeightPx: Int, mode: SpriteSizeMode): Int {
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        val b = SpriteMath.opaqueBounds(pixels, src.width, src.height) ?: return targetHeightPx
        return SpriteMath.pixelScale(b[2] - b[0], b[3] - b[1], targetHeightPx, mode).second
    }

    /** Crops transparent padding then scales without smoothing so the pixel art stays sharp. */
    fun toDrawable(res: Resources, src: Bitmap, targetHeightPx: Int, mode: SpriteSizeMode): Drawable {
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        val b = SpriteMath.opaqueBounds(pixels, src.width, src.height)
        val trimmed = if (b == null) src else Bitmap.createBitmap(src, b[0], b[1], b[2] - b[0], b[3] - b[1])
        val (w, h) = SpriteMath.pixelScale(trimmed.width, trimmed.height, targetHeightPx, mode)
        val scaled = Bitmap.createScaledBitmap(trimmed, w, h, false)
        if (trimmed !== src && trimmed !== scaled) trimmed.recycle()
        if (src !== scaled) src.recycle()
        return BitmapDrawable(res, scaled).apply {
            isFilterBitmap = false
            setBounds(0, 0, w, h)
        }
    }

    private fun decodeAnimated(file: File, targetHeightPx: Int): Drawable? {
        if (Build.VERSION.SDK_INT < 28) return null
        return try {
            val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
            if (drawable !is AnimatedImageDrawable) return null
            val (w, h) = SpriteMath.fitHeight(drawable.intrinsicWidth, drawable.intrinsicHeight, targetHeightPx)
            drawable.isFilterBitmap = false
            drawable.setBounds(0, 0, w, h)
            drawable
        } catch (e: Exception) {
            null
        }
    }
}
