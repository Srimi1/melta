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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A sprite ready to draw: a drawable with bounds already set to its on-screen size. */
class LoadedSprite(val drawable: Drawable, val types: List<String>) {
    val width: Int get() = drawable.bounds.width()
    val height: Int get() = drawable.bounds.height()
    val isAnimated: Boolean get() = Build.VERSION.SDK_INT >= 28 && drawable is AnimatedImageDrawable
}

/**
 * Turns a [BatteryBarConfig] into a small, crisp, ready-to-draw sprite.
 * Static sprites are trimmed and scaled with nearest neighbour so the pixel art stays sharp,
 * and the result is only a few KB of memory.
 */
object SpriteLoader {

    suspend fun load(context: Context, config: BatteryBarConfig, targetHeightPx: Int): LoadedSprite =
        withContext(Dispatchers.IO) {
            val res = context.resources
            if (config.useCustomSprite && config.customSpritePath != null) {
                val file = File(config.customSpritePath)
                decodeStatic(res, file, targetHeightPx)?.let { return@withContext LoadedSprite(it, emptyList()) }
            }

            val entry = Pokedex.find(context, config.pokemonId)
            val types = entry?.types.orEmpty()
            val file = SpriteRepository.fetch(context, config.pokemonId, config.shiny, config.animated)
            val drawable = file?.let {
                if (it.extension == "gif") decodeAnimated(it, targetHeightPx) ?: decodeStatic(res, it, targetHeightPx)
                else decodeStatic(res, it, targetHeightPx)
            }
            LoadedSprite(drawable ?: mascot(res, targetHeightPx), types)
        }

    private fun decodeStatic(res: Resources, file: File, targetHeightPx: Int): Drawable? {
        if (!file.exists()) return null
        val src = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return toDrawable(res, src, targetHeightPx)
    }

    /** Crops transparent padding then scales to the target height without smoothing. */
    fun toDrawable(res: Resources, src: Bitmap, targetHeightPx: Int): Drawable {
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        val b = SpriteMath.opaqueBounds(pixels, src.width, src.height)
        val trimmed = if (b == null) src else Bitmap.createBitmap(src, b[0], b[1], b[2] - b[0], b[3] - b[1])
        val (w, h) = SpriteMath.fitHeight(trimmed.width, trimmed.height, targetHeightPx)
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

    /** Original built-in mascot used when offline or before the first download finishes. */
    fun mascot(res: Resources, targetHeightPx: Int): Drawable =
        toDrawable(res, Mascot.bitmap(), targetHeightPx)
}
