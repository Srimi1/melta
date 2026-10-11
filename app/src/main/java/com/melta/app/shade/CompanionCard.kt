package com.melta.app.shade

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.BatteryManager
import android.os.Build
import com.melta.app.R
import com.melta.app.battery.overlay.BatteryBarView
import com.melta.app.battery.sprites.LoadedSprite
import com.melta.app.battery.sprites.Pokedex
import com.melta.app.core.prefs.BarModes
import com.melta.app.core.prefs.BarPosition
import com.melta.app.core.prefs.BatteryBarConfig
import com.melta.app.core.prefs.FillStyle
import kotlin.random.Random

/**
 * The companion card shown in the notification shade: the themed background with the battery bar
 * and the companion riding it. Used by the service for the real notification and by the Shade
 * screen for its live preview, so both always look the same.
 */
object CompanionCard {

    /** Tallest bitmap we put in a notification. Big bitmaps can exceed the binder limit. */
    private const val MAX_BITMAP_WIDTH = 600

    private fun dp(context: Context, v: Float) = (v * context.resources.displayMetrics.density).toInt()

    fun heightPx(context: Context) = dp(context, 60f)

    fun widthPx(context: Context): Int =
        (context.resources.displayMetrics.widthPixels - dp(context, 64f)).coerceIn(dp(context, 200f), dp(context, 400f))

    /** Look used inside the card: a thicker bar, a bigger still sprite, no percent text, theme colors. */
    fun snapshotConfig(c: BatteryBarConfig): BatteryBarConfig {
        val spec = CardThemeSpec.of(c.cardTheme)
        val base = c.copy(
            position = BarPosition.STATUS_BAR_BOTTOM,
            barThicknessDp = c.barThicknessDp.coerceAtLeast(6),
            spriteSizeDp = 44,
            showPercent = false,
            animated = false,
        )
        return if (spec.overridesBarColors) {
            base.copy(
                matchColors = false,
                fillStyle = FillStyle.GRADIENT,
                colorStart = spec.barStart!!,
                colorEnd = spec.barEnd!!,
            )
        } else {
            base
        }
    }

    /**
     * Draws the card. [view] is an offscreen [BatteryBarView] the caller keeps around, so the
     * sprite is only handed over when it changes.
     */
    fun render(
        context: Context,
        view: BatteryBarView,
        config: BatteryBarConfig,
        sprite: LoadedSprite?,
        level: Int,
        charging: Boolean,
        width: Int = widthPx(context),
        height: Int = heightPx(context),
    ): Bitmap {
        val spec = CardThemeSpec.of(config.cardTheme)
        view.applyConfig(snapshotConfig(config))
        if (view.tag !== sprite) {
            view.tag = sprite
            view.setSprite(sprite)
        }
        view.spriteColorFilter = if (spec.gameBoySprite) GAME_BOY_FILTER else null
        view.setBattery(level.coerceAtLeast(0), charging)

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val pad = if (spec.background != null) dp(context, 10f) else 0
        drawBackground(context, canvas, spec, width, height)
        val bar = view.renderSnapshot(width - pad * 2, height - pad)
        canvas.drawBitmap(bar, pad.toFloat(), 0f, null)
        bar.recycle()

        if (out.width <= MAX_BITMAP_WIDTH) return out
        val h = out.height * MAX_BITMAP_WIDTH / out.width
        return Bitmap.createScaledBitmap(out, MAX_BITMAP_WIDTH, h, true).also { out.recycle() }
    }

    private fun drawBackground(context: Context, canvas: Canvas, spec: CardThemeSpec, w: Int, h: Int) {
        val bg = spec.background ?: return
        val r = dp(context, 14f).toFloat()
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        canvas.drawRoundRect(rect, r, r, paint)

        if (spec.stars) {
            // Same sky every time, so the card doesn't flicker between updates.
            val rnd = Random(42)
            paint.color = 0xCCFFFFFF.toInt()
            repeat(22) {
                val size = dp(context, if (rnd.nextInt(4) == 0) 1.6f else 0.9f).toFloat()
                canvas.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.7f, size, paint)
            }
        }

        if (spec.pokedexLights) {
            val cx = dp(context, 16f).toFloat()
            val cy = dp(context, 14f).toFloat()
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(cx, cy, dp(context, 8f).toFloat(), paint)
            paint.color = 0xFF30A7D7.toInt()
            canvas.drawCircle(cx, cy, dp(context, 6f).toFloat(), paint)
            val small = dp(context, 3f).toFloat()
            listOf(0xFFFF3B30, 0xFFFFDE00, 0xFF34C759).forEachIndexed { i, c ->
                paint.color = c.toInt()
                canvas.drawCircle(cx + dp(context, 16f + i * 9f), dp(context, 9f).toFloat(), small, paint)
            }
        }

        spec.rim?.let { rim ->
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(context, 1.5f).toFloat()
                color = rim
                if (spec.gradientRim && spec.barStart != null && spec.barEnd != null) {
                    shader = LinearGradient(0f, 0f, w.toFloat(), 0f, spec.barStart, spec.barEnd, Shader.TileMode.CLAMP)
                }
            }
            val inset = stroke.strokeWidth / 2f
            rect.inset(inset, inset)
            canvas.drawRoundRect(rect, r, r, stroke)
        }
    }

    /** Title and subtitle, e.g. "Pikachu · 72%" and "Charging · full in 1 h 20 m". */
    fun texts(context: Context, config: BatteryBarConfig, level: Int, charging: Boolean): Pair<String, String> {
        val name = companionName(context, config) ?: context.getString(R.string.app_name)
        val title = context.getString(R.string.notif_bar_title, name, level.coerceAtLeast(0))
        val subtitle = when {
            charging -> {
                val remaining = if (Build.VERSION.SDK_INT >= 28) {
                    (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).computeChargeTimeRemaining()
                } else {
                    -1L
                }
                BarModes.formatChargeTime(remaining)?.let { context.getString(R.string.notif_charging_eta, it) }
                    ?: context.getString(R.string.notif_charging)
            }
            level <= config.lowThreshold -> context.getString(R.string.notif_low)
            else -> context.getString(R.string.notif_on_battery)
        }
        return title to subtitle
    }

    fun companionName(context: Context, c: BatteryBarConfig): String? =
        if (c.useCustomSprite) context.getString(R.string.your_creation) else Pokedex.find(context, c.pokemonId)?.name

    /** Maps brightness onto the four shades of green of the original Game Boy screen. */
    private val GAME_BOY_FILTER = run {
        fun row(dark: Int, light: Int): FloatArray {
            val k = (light - dark) / 255f
            return floatArrayOf(0.299f * k, 0.587f * k, 0.114f * k, 0f, dark.toFloat())
        }
        val r = row(15, 155)
        val g = row(56, 188)
        val b = row(15, 15)
        ColorMatrixColorFilter(ColorMatrix(r + g + b + floatArrayOf(0f, 0f, 0f, 1f, 0f)))
    }
}
