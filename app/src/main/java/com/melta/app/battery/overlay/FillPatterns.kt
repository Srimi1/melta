package com.melta.app.battery.overlay

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.melta.app.core.prefs.FillStyle

/** Builds the shader used to paint the battery fill. Called only when size or style changes. */
object FillPatterns {

    private val RAINBOW = intArrayOf(
        0xFFFF5252.toInt(), 0xFFFFB74D.toInt(), 0xFFFFEE58.toInt(), 0xFF69F0AE.toInt(),
        0xFF40C4FF.toInt(), 0xFF7C4DFF.toInt(), 0xFFFF4081.toInt(),
    )

    /** True when the pattern can scroll along the bar while charging. */
    fun canFlow(style: FillStyle): Boolean = when (style) {
        FillStyle.RAINBOW, FillStyle.STRIPES, FillStyle.DOTS, FillStyle.CHECKER, FillStyle.CUSTOM -> true
        FillStyle.SOLID, FillStyle.GRADIENT -> false
    }

    fun build(
        style: FillStyle,
        start: Int,
        end: Int,
        width: Float,
        thickness: Int,
        density: Float,
        customTile: Bitmap?,
    ): Shader? {
        val t = thickness.coerceAtLeast(1)
        return when (style) {
            FillStyle.SOLID -> null
            FillStyle.GRADIENT -> LinearGradient(0f, 0f, width.coerceAtLeast(1f), 0f, start, end, Shader.TileMode.CLAMP)
            FillStyle.RAINBOW -> LinearGradient(0f, 0f, width.coerceAtLeast(1f) / 2f, 0f, RAINBOW, null, Shader.TileMode.MIRROR)
            FillStyle.STRIPES -> {
                val s = 6f * density
                LinearGradient(
                    0f, 0f, s, s,
                    intArrayOf(start, start, end, end),
                    floatArrayOf(0f, 0.5f, 0.5f, 1f),
                    Shader.TileMode.REPEAT,
                )
            }
            FillStyle.DOTS -> BitmapShader(dotsTile(start, end, t), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            FillStyle.CHECKER -> BitmapShader(checkerTile(start, end, t), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            FillStyle.CUSTOM -> {
                if (customTile == null) {
                    LinearGradient(0f, 0f, width.coerceAtLeast(1f), 0f, start, end, Shader.TileMode.CLAMP)
                } else {
                    val w = (customTile.width.toLong() * t / customTile.height.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                    val tile = Bitmap.createScaledBitmap(customTile, w, t, false)
                    BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                }
            }
        }
    }

    private fun checkerTile(a: Int, b: Int, t: Int): Bitmap {
        val half = (t / 2).coerceAtLeast(1)
        val size = half * 2
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()
        p.color = a; c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        p.color = b
        c.drawRect(0f, 0f, half.toFloat(), half.toFloat(), p)
        c.drawRect(half.toFloat(), half.toFloat(), size.toFloat(), size.toFloat(), p)
        return bmp
    }

    private fun dotsTile(bg: Int, dot: Int, t: Int): Bitmap {
        val size = (t * 2).coerceAtLeast(4)
        val bmp = Bitmap.createBitmap(size, t.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dot }
        c.drawCircle(size / 2f, bmp.height / 2f, bmp.height * 0.35f, p)
        return bmp
    }
}
