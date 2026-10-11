package com.melta.app.battery.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.melta.app.battery.sprites.LoadedSprite
import com.melta.app.battery.sprites.SpriteMath
import com.melta.app.core.prefs.BarPosition
import com.melta.app.core.prefs.BatteryBarConfig
import com.melta.app.core.prefs.ChargeEffect
import com.melta.app.core.prefs.FillStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The battery bar with its companion. A plain View with hand written Canvas drawing,
 * so the overlay service never has to load Compose. It only animates while something is
 * actually moving and stops completely when paused (screen off) or hidden.
 */
class BatteryBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    var config: BatteryBarConfig = BatteryBarConfig()
        private set
    var level: Int = 50
        private set
    var charging: Boolean = false
        private set

    /** Height of the system status bar. The bar sits on its bottom edge by default. */
    var statusBarHeightPx: Int = dp(24f).toInt()
        set(value) {
            field = value
            shaderDirty = true
            requestLayout()
            invalidate()
        }

    private var sprite: LoadedSprite? = null

    /** Optional tint for the companion, e.g. the Game Boy green of the shade card theme. */
    var spriteColorFilter: ColorFilter? = null
        set(value) {
            field = value
            invalidate()
        }
    private var effect: ChargeEffect = ChargeEffect.SPARKS
    private val particles = ParticleSystem(density)

    private var paused = false
    private var hidden = false

    // Animation state
    private var running = false
    private var lastFrame = 0L
    private var time = 0f
    private var flowOffset = 0f
    private var emitAccumulator = 0f
    private var celebrateLeft = 0f
    private var wasFull = false

    // Drawing objects, allocated once
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
        setShadowLayer(dp(1.5f), 0f, dp(0.5f), 0xAA000000.toInt())
    }
    private val rect = RectF()
    private val fillMatrix = Matrix()
    private val shineMatrix = Matrix()
    private var fillShader: Shader? = null
    private var shineShader: Shader? = null
    private var shaderDirty = true
    private var customTile: Bitmap? = null

    private val frame = Runnable { tick() }

    // ---------------------------------------------------------------- public API

    fun applyConfig(newConfig: BatteryBarConfig) {
        val old = config
        config = newConfig
        if (old.customTilePath != newConfig.customTilePath) loadCustomTile(newConfig.customTilePath)
        if (old.fillStyle != newConfig.fillStyle || old.colorStart != newConfig.colorStart ||
            old.colorEnd != newConfig.colorEnd || old.barThicknessDp != newConfig.barThicknessDp ||
            old.matchColors != newConfig.matchColors ||
            old.customTilePath != newConfig.customTilePath
        ) {
            shaderDirty = true
        }
        effect = EffectPicker.resolve(newConfig.effect, sprite?.types.orEmpty())
        particles.effect = effect
        if (old.position != newConfig.position || old.barThicknessDp != newConfig.barThicknessDp) requestLayout()
        invalidate()
        ensureRunning()
    }

    fun setSprite(newSprite: LoadedSprite?) {
        (sprite?.drawable as? Animatable)?.stop()
        sprite?.drawable?.callback = null
        sprite = newSprite
        newSprite?.drawable?.callback = this
        if (!paused && !hidden) (newSprite?.drawable as? Animatable)?.start()
        effect = EffectPicker.resolve(config.effect, newSprite?.types.orEmpty())
        particles.effect = effect
        particles.clear()
        if (config.matchColors) shaderDirty = true
        requestLayout()
        invalidate()
        ensureRunning()
    }

    fun setBattery(newLevel: Int, isCharging: Boolean) {
        val full = newLevel >= 100 && isCharging
        if (full && !wasFull && config.celebrateFull) {
            celebrateLeft = CELEBRATE_SECONDS
            post {
                val g = geometry()
                particles.burst(g.spriteCx, g.spriteTop + g.spriteH / 2f, 24)
            }
        }
        wasFull = full
        if (!isCharging) particles.clear()
        if (newLevel == level && isCharging == charging) return
        level = newLevel.coerceIn(0, 100)
        charging = isCharging
        invalidate()
        ensureRunning()
    }

    /** Stop every animation, used while the screen is off. */
    fun setPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        updateAnimatedSprite()
        if (!value) ensureRunning()
    }

    /** Hide the bar (fullscreen app, landscape) without tearing down the window. */
    fun setHidden(value: Boolean) {
        if (hidden == value) return
        hidden = value
        updateAnimatedSprite()
        invalidate()
        if (!value) ensureRunning()
    }

    /**
     * Draws one still frame into a bitmap, for the notification panel bar. Reuses all the normal
     * drawing (fill, matched colors, sprite, low battery tilt). While charging it also freezes a
     * small burst of the charging effect. The view is never attached, so no animation runs.
     */
    fun renderSnapshot(width: Int, height: Int): Bitmap {
        statusBarHeightPx = height
        measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        layout(0, 0, width, height)
        particles.clear()
        if (charging && effect != ChargeEffect.NONE) {
            repeat(SNAPSHOT_PARTICLES) { emitOne() }
            particles.update(0.18f, width.toFloat(), height.toFloat())
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap))
        particles.clear()
        return bitmap
    }

    /** Height the overlay window needs for the current config and sprite. */
    fun requiredHeightPx(): Int {
        val thick = dp(config.barThicknessDp.toFloat()).toInt()
        val spriteH = sprite?.height ?: spriteTargetHeightPx(config, statusBarHeightPx, density)
        return when (config.position) {
            BarPosition.STATUS_BAR_BOTTOM -> statusBarHeightPx
            BarPosition.SCREEN_TOP -> thick + spriteH + dp(3f).toInt()
        }
    }

    // ---------------------------------------------------------------- View plumbing

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = resolveSize(requiredHeightPx(), heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        shaderDirty = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimatedSprite()
        ensureRunning()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        running = false
        (sprite?.drawable as? Animatable)?.stop()
        super.onDetachedFromWindow()
    }

    override fun verifyDrawable(who: Drawable): Boolean =
        who === sprite?.drawable || super.verifyDrawable(who)

    private fun updateAnimatedSprite() {
        val anim = sprite?.drawable as? Animatable ?: return
        if (paused || hidden || !isAttachedToWindow) anim.stop() else anim.start()
    }

    // ---------------------------------------------------------------- animation loop

    private fun needsFrames(): Boolean {
        if (paused || hidden || !isAttachedToWindow) return false
        return charging || config.bob || celebrateLeft > 0f || !particles.isEmpty
    }

    private fun ensureRunning() {
        if (running || !needsFrames()) return
        running = true
        lastFrame = SystemClock.uptimeMillis()
        postOnAnimation(frame)
    }

    private fun tick() {
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastFrame) / 1000f).coerceIn(0f, 0.1f)
        lastFrame = now
        time += dt

        if (celebrateLeft > 0f) celebrateLeft = (celebrateLeft - dt).coerceAtLeast(0f)
        if (charging && FillPatterns.canFlow(config.fillStyle)) flowOffset += dp(18f) * dt

        if (charging && effect != ChargeEffect.NONE) {
            emitAccumulator += dt * (6f + 30f * config.effectIntensity)
            while (emitAccumulator >= 1f) {
                emitAccumulator -= 1f
                emitOne()
            }
        }
        particles.update(dt, width.toFloat(), height.toFloat())
        invalidate()

        if (needsFrames()) {
            // 30 fps while something lively is going on, 15 fps for the gentle idle bob.
            val delay = if (charging || celebrateLeft > 0f || !particles.isEmpty) 33L else 66L
            postOnAnimationDelayed(frame, delay)
        } else {
            running = false
        }
    }

    private fun emitOne() {
        val g = geometry()
        // Mostly from around the companion, sometimes along the filled part of the bar.
        if (Math.random() < 0.55) {
            particles.emit(g.spriteCx + (Math.random().toFloat() - 0.5f) * g.spriteW, g.spriteTop + g.spriteH * 0.5f)
        } else {
            particles.emit((Math.random() * g.fillEnd).toFloat(), g.barTop + g.thick / 2f)
        }
    }

    // ---------------------------------------------------------------- drawing

    private class Geometry {
        var thick = 0f
        var barTop = 0f
        var fillEnd = 0f
        var spriteW = 0f
        var spriteH = 0f
        var spriteCx = 0f
        var spriteTop = 0f
    }

    private val geo = Geometry()

    private fun geometry(): Geometry {
        val g = geo
        val w = width.toFloat()
        g.thick = dp(config.barThicknessDp.toFloat())
        g.barTop = when (config.position) {
            BarPosition.STATUS_BAR_BOTTOM -> statusBarHeightPx - g.thick
            BarPosition.SCREEN_TOP -> 0f
        }
        g.fillEnd = w * level / 100f
        g.spriteW = (sprite?.width ?: 0).toFloat()
        g.spriteH = (sprite?.height ?: 0).toFloat()
        g.spriteCx = SpriteMath.spriteCenterX(config.anchor, level, w, g.spriteW, dp(2f))
        g.spriteTop = when (config.position) {
            BarPosition.STATUS_BAR_BOTTOM -> g.barTop - g.spriteH + dp(0.5f)
            BarPosition.SCREEN_TOP -> g.barTop + g.thick + dp(1f)
        }
        return g
    }

    override fun onDraw(canvas: Canvas) {
        if (hidden) return
        val w = width.toFloat()
        if (w <= 0f) return
        if (shaderDirty) rebuildShaders(w)
        val g = geometry()
        val radius = g.thick / 2f
        val low = !charging && level <= config.lowThreshold

        // Track
        trackPaint.color = 0x33FFFFFF
        rect.set(0f, g.barTop, w, g.barTop + g.thick)
        canvas.drawRoundRect(rect, radius, radius, trackPaint)

        // Fill
        if (g.fillEnd > 0f) {
            rect.set(0f, g.barTop, g.fillEnd.coerceAtLeast(g.thick), g.barTop + g.thick)
            if (low) {
                fillPaint.shader = null
                fillPaint.color = LOW_COLOR
            } else if (fillShader != null) {
                fillMatrix.setTranslate(flowOffset, g.barTop)
                fillShader?.setLocalMatrix(fillMatrix)
                fillPaint.shader = fillShader
            } else {
                fillPaint.shader = null
                fillPaint.color = startColor()
            }
            canvas.drawRoundRect(rect, radius, radius, fillPaint)

            // Charging shine sweeping along the fill
            if (charging && shineShader != null) {
                val sweep = dp(48f)
                val period = 1.6f
                val pos = ((time % period) / period) * (g.fillEnd + sweep) - sweep
                shineMatrix.setTranslate(pos, 0f)
                shineShader?.setLocalMatrix(shineMatrix)
                canvas.drawRoundRect(rect, radius, radius, shinePaint)
            }
        }

        drawSprite(canvas, g, low)

        if (config.showPercent) drawPercent(canvas, g)

        particles.draw(canvas)
    }

    private fun drawSprite(canvas: Canvas, g: Geometry, low: Boolean) {
        val d = sprite?.drawable ?: return
        val bobAmp = if (config.bob) dp(if (low) 0.6f else 1f) else 0f
        val bobSpeed = if (low) 0.5f else 1f
        val bob = sin(time * 2f * PI.toFloat() * 0.7f * bobSpeed) * bobAmp
        val left = g.spriteCx - g.spriteW / 2f
        val top = g.spriteTop + bob - bobAmp

        canvas.save()
        canvas.translate(left, top)
        val pivotX = g.spriteW / 2f
        val pivotY = g.spriteH
        var sx = if (config.flip) -1f else 1f
        if (celebrateLeft > 0f) {
            // Spin in place a couple of times to celebrate a full battery.
            val p = 1f - celebrateLeft / CELEBRATE_SECONDS
            sx *= cos(p * 4f * PI.toFloat())
            if (sx in -0.08f..0.08f) sx = if (sx < 0f) -0.08f else 0.08f
        }
        canvas.scale(sx, 1f, pivotX, pivotY)
        if (low) canvas.rotate(-10f, pivotX, pivotY)
        if (spriteColorFilter != null) {
            d.colorFilter = spriteColorFilter
            d.draw(canvas)
            d.colorFilter = null
        } else {
            d.draw(canvas)
        }
        canvas.restore()
    }

    private fun drawPercent(canvas: Canvas, g: Geometry) {
        val label = "$level%"
        val size = if (g.spriteH > 0f) (g.spriteH * 0.55f).coerceAtMost(dp(10f)) else dp(9f)
        textPaint.textSize = size
        val tw = textPaint.measureText(label)
        val gap = dp(3f)
        val rightSide = g.spriteCx + g.spriteW / 2f + gap
        val x = if (rightSide + tw < width) rightSide else g.spriteCx - g.spriteW / 2f - gap - tw
        val baseline = when (config.position) {
            BarPosition.STATUS_BAR_BOTTOM -> g.barTop - dp(2f)
            BarPosition.SCREEN_TOP -> g.barTop + g.thick + size + dp(1f)
        }
        canvas.drawText(label, x, baseline, textPaint)
    }

    private fun rebuildShaders(w: Float) {
        shaderDirty = false
        val thick = dp(config.barThicknessDp.toFloat()).toInt()
        fillShader = FillPatterns.build(
            config.fillStyle, startColor(), endColor(), w, thick, density,
            if (config.fillStyle == FillStyle.CUSTOM) customTile else null,
        )
        shineShader = LinearGradient(
            0f, 0f, dp(48f), 0f,
            intArrayOf(0x00FFFFFF, 0x99FFFFFF.toInt(), 0x00FFFFFF),
            null,
            Shader.TileMode.CLAMP,
        )
        shinePaint.shader = shineShader
    }

    /** Bar colors: taken from the companion when matching is on, otherwise the user's picks. */
    private fun startColor(): Int = matchedPalette()?.get(0) ?: config.colorStart

    private fun endColor(): Int = matchedPalette()?.get(1) ?: config.colorEnd

    private fun matchedPalette(): IntArray? =
        if (config.matchColors) sprite?.palette?.takeIf { it.size >= 2 } else null

    private fun loadCustomTile(path: String?) {
        customTile = path?.let { BitmapFactory.decodeFile(it) }
    }

    companion object {
        private const val CELEBRATE_SECONDS = 2.4f
        private const val SNAPSHOT_PARTICLES = 14
        private const val LOW_COLOR = 0xFFFF5252.toInt()

        /** Sprite height that fits the chosen layout. Used to decode the sprite at the right size. */
        fun spriteTargetHeightPx(config: BatteryBarConfig, statusBarHeightPx: Int, density: Float): Int {
            val wanted = (config.spriteSizeDp * density).toInt()
            return when (config.position) {
                BarPosition.STATUS_BAR_BOTTOM -> {
                    val room = statusBarHeightPx - (config.barThicknessDp * density).toInt() - (2 * density).toInt()
                    wanted.coerceAtMost(room).coerceAtLeast((8 * density).toInt())
                }
                BarPosition.SCREEN_TOP -> wanted
            }
        }
    }
}
