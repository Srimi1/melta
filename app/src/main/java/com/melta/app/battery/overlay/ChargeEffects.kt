package com.melta.app.battery.overlay

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.melta.app.core.prefs.ChargeEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A tiny fixed size particle system. All storage is allocated once, so a running effect
 * creates no garbage per frame. That keeps the overlay smooth and memory flat.
 */
class ParticleSystem(private val density: Float) {

    private val max = 40
    private val x = FloatArray(max)
    private val y = FloatArray(max)
    private val vx = FloatArray(max)
    private val vy = FloatArray(max)
    private val life = FloatArray(max)
    private val maxLife = FloatArray(max)
    private val size = FloatArray(max)
    private val rot = FloatArray(max)
    private val spin = FloatArray(max)
    private val tint = IntArray(max)
    private var alive = 0

    var effect: ChargeEffect = ChargeEffect.SPARKS

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val star = Path().apply {
        moveTo(0f, -1f); lineTo(0.25f, -0.25f); lineTo(1f, 0f); lineTo(0.25f, 0.25f)
        lineTo(0f, 1f); lineTo(-0.25f, 0.25f); lineTo(-1f, 0f); lineTo(-0.25f, -0.25f); close()
    }
    private val bolt = Path().apply {
        moveTo(0.2f, -1f); lineTo(-0.5f, 0.1f); lineTo(-0.05f, 0.1f); lineTo(-0.25f, 1f)
        lineTo(0.5f, -0.2f); lineTo(0.05f, -0.2f); close()
    }
    private val heart = Path().apply {
        moveTo(0f, 0.9f)
        cubicTo(-1.2f, 0f, -0.9f, -1.1f, 0f, -0.45f)
        cubicTo(0.9f, -1.1f, 1.2f, 0f, 0f, 0.9f)
        close()
    }
    private val leaf = Path().apply {
        moveTo(0f, -1f)
        quadTo(0.9f, 0f, 0f, 1f)
        quadTo(-0.9f, 0f, 0f, -1f)
        close()
    }
    private val flame = Path().apply {
        moveTo(0f, -1f)
        cubicTo(0.6f, -0.2f, 0.8f, 0.5f, 0f, 1f)
        cubicTo(-0.8f, 0.5f, -0.6f, -0.2f, 0f, -1f)
        close()
    }

    val isEmpty: Boolean get() = alive == 0

    fun clear() {
        alive = 0
    }

    private fun dp(v: Float) = v * density
    private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

    /** Emits one particle at (px, py) using the current effect's look and motion. */
    fun emit(px: Float, py: Float) {
        if (alive >= max || effect == ChargeEffect.NONE || effect == ChargeEffect.AUTO) return
        val i = alive++
        x[i] = px
        y[i] = py
        rot[i] = rnd(0f, 360f)
        spin[i] = 0f
        when (effect) {
            ChargeEffect.SPARKS -> {
                val a = rnd(-PI.toFloat(), 0f)
                val s = dp(rnd(20f, 55f))
                vx[i] = cos(a) * s; vy[i] = sin(a) * s
                maxLife[i] = rnd(0.5f, 0.9f); size[i] = dp(rnd(1.6f, 3f)); spin[i] = rnd(-180f, 180f)
                tint[i] = if (Random.nextBoolean()) 0xFFFFF59D.toInt() else 0xFFFFFFFF.toInt()
            }
            ChargeEffect.BOLTS -> {
                vx[i] = dp(rnd(-6f, 6f)); vy[i] = dp(rnd(-18f, -6f))
                maxLife[i] = rnd(0.25f, 0.45f); size[i] = dp(rnd(3f, 5f)); rot[i] = rnd(-20f, 20f)
                tint[i] = if (Random.nextInt(4) == 0) 0xFFFFFFFF.toInt() else 0xFFFFE34D.toInt()
            }
            ChargeEffect.BUBBLES -> {
                vx[i] = dp(rnd(-4f, 4f)); vy[i] = dp(rnd(-30f, -14f))
                maxLife[i] = rnd(0.7f, 1.2f); size[i] = dp(rnd(1.2f, 2.6f))
                tint[i] = 0xFF81D4FA.toInt()
            }
            ChargeEffect.FLAMES -> {
                vx[i] = dp(rnd(-5f, 5f)); vy[i] = dp(rnd(-45f, -22f)); rot[i] = rnd(-10f, 10f)
                maxLife[i] = rnd(0.4f, 0.7f); size[i] = dp(rnd(2.2f, 3.6f))
                tint[i] = if (Random.nextBoolean()) 0xFFFF9800.toInt() else 0xFFFF5722.toInt()
            }
            ChargeEffect.LEAVES -> {
                vx[i] = dp(rnd(-22f, 22f)); vy[i] = dp(rnd(-16f, 6f)); spin[i] = rnd(-240f, 240f)
                maxLife[i] = rnd(0.8f, 1.3f); size[i] = dp(rnd(2f, 3.2f))
                tint[i] = if (Random.nextBoolean()) 0xFF66BB6A.toInt() else 0xFFAED581.toInt()
            }
            ChargeEffect.HEARTS -> {
                vx[i] = dp(rnd(-6f, 6f)); vy[i] = dp(rnd(-26f, -12f)); rot[i] = rnd(-15f, 15f)
                maxLife[i] = rnd(0.7f, 1.1f); size[i] = dp(rnd(2.2f, 3.4f))
                tint[i] = if (Random.nextBoolean()) 0xFFFF80AB.toInt() else 0xFFF48FB1.toInt()
            }
            ChargeEffect.SNOW -> {
                vx[i] = dp(rnd(-10f, 10f)); vy[i] = dp(rnd(-8f, 10f))
                maxLife[i] = rnd(0.9f, 1.5f); size[i] = dp(rnd(0.8f, 1.8f))
                tint[i] = 0xFFE1F5FE.toInt()
            }
            ChargeEffect.AUTO, ChargeEffect.NONE -> Unit
        }
        life[i] = maxLife[i]
    }

    /** A radial burst, used for the full charge celebration. */
    fun burst(px: Float, py: Float, count: Int) {
        repeat(count) {
            if (alive >= max) return
            emit(px, py)
            val i = alive - 1
            val a = rnd(0f, 2f * PI.toFloat())
            val s = dp(rnd(30f, 70f))
            vx[i] = cos(a) * s
            vy[i] = sin(a) * s
            maxLife[i] = maxLife[i] * 1.3f
            life[i] = maxLife[i]
        }
    }

    fun update(dt: Float, width: Float, height: Float) {
        var i = 0
        while (i < alive) {
            life[i] -= dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            rot[i] += spin[i] * dt
            if (effect == ChargeEffect.BUBBLES) x[i] += sin(life[i] * 12f) * dp(0.4f)
            val out = x[i] < -dp(8f) || x[i] > width + dp(8f) || y[i] < -dp(8f) || y[i] > height + dp(8f)
            if (life[i] <= 0f || out) {
                removeAt(i)
            } else {
                i++
            }
        }
    }

    private fun removeAt(i: Int) {
        val last = alive - 1
        x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]
        life[i] = life[last]; maxLife[i] = maxLife[last]; size[i] = size[last]
        rot[i] = rot[last]; spin[i] = spin[last]; tint[i] = tint[last]
        alive = last
    }

    fun draw(canvas: Canvas) {
        for (i in 0 until alive) {
            val t = (life[i] / maxLife[i]).coerceIn(0f, 1f)
            var alpha = (t * 255).toInt()
            if (effect == ChargeEffect.BOLTS && Random.nextInt(5) == 0) alpha /= 3 // flicker
            val s = if (effect == ChargeEffect.FLAMES) size[i] * (0.4f + 0.6f * t) else size[i]

            if (effect == ChargeEffect.BUBBLES || effect == ChargeEffect.SNOW) {
                val p = if (effect == ChargeEffect.BUBBLES) strokePaint else paint
                p.color = tint[i]
                p.alpha = alpha
                p.strokeWidth = dp(0.6f)
                canvas.drawCircle(x[i], y[i], s, p)
                continue
            }

            paint.color = tint[i]
            paint.alpha = alpha
            canvas.save()
            canvas.translate(x[i], y[i])
            canvas.rotate(rot[i])
            canvas.scale(s, s)
            val path = when (effect) {
                ChargeEffect.BOLTS -> bolt
                ChargeEffect.HEARTS -> heart
                ChargeEffect.LEAVES -> leaf
                ChargeEffect.FLAMES -> flame
                else -> star
            }
            canvas.drawPath(path, paint)
            canvas.restore()
        }
    }
}
