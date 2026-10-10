package com.melta.app.battery.creations

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class CreationKind(val prefix: String) { SPRITE("sprite_"), TILE("tile_") }

data class Creation(val file: File, val kind: CreationKind)

/** Saves and lists the user's own sprites and bar patterns as small PNG files. */
object CreationStore {

    private fun dir(context: Context) = File(context.filesDir, "creations").apply { mkdirs() }

    suspend fun list(context: Context): List<Creation> = withContext(Dispatchers.IO) {
        dir(context).listFiles().orEmpty()
            .filter { it.extension == "png" }
            .sortedByDescending { it.lastModified() }
            .mapNotNull { f ->
                val kind = CreationKind.entries.firstOrNull { f.name.startsWith(it.prefix) } ?: return@mapNotNull null
                Creation(f, kind)
            }
    }

    suspend fun save(context: Context, grid: PixelGrid, kind: CreationKind): File = withContext(Dispatchers.IO) {
        val bmp = Bitmap.createBitmap(grid.pixels, grid.size, grid.size, Bitmap.Config.ARGB_8888)
        val file = File(dir(context), "${kind.prefix}${System.currentTimeMillis()}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        file
    }

    suspend fun delete(creation: Creation) = withContext(Dispatchers.IO) { creation.file.delete() }

    /**
     * Turns any picked image into pixel art at the editor's grid size, so people can
     * start from a photo or drawing and then touch it up by hand.
     */
    suspend fun importToGrid(context: Context, uri: Uri, gridSize: Int): IntArray? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= gridSize * 4 && bounds.outHeight / (sample * 2) >= gridSize * 4) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return@withContext null

        // Center crop to a square, then shrink to the grid.
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val small = Bitmap.createScaledBitmap(square, gridSize, gridSize, true)
        val out = IntArray(gridSize * gridSize)
        small.getPixels(out, 0, gridSize, 0, 0, gridSize, gridSize)
        listOf(src, square, small).distinct().forEach { it.recycle() }
        out
    }
}
