package com.melta.app.shade

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.melta.app.battery.sprites.SpriteMath
import com.melta.app.battery.sprites.SpriteRepository

/** Small helpers shared by the tiles and the Shade screen. */
object BatteryState {

    /** Current battery level and charging state from the sticky system broadcast. */
    fun read(context: Context): Pair<Int, Boolean> {
        val intent: Intent = ContextCompat.registerReceiver(
            context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        ) ?: return -1 to false
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL && intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        return (if (level < 0) -1 else level * 100 / scale) to charging
    }

    /** The bundled still sprite with its empty padding trimmed, for tile icons. */
    fun trimmedSprite(context: Context, id: Int): Bitmap? {
        val src = runCatching {
            context.assets.open(SpriteRepository.assetPath(id, false)).use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return null
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        val b = SpriteMath.opaqueBounds(pixels, src.width, src.height) ?: return src
        return Bitmap.createBitmap(src, b[0], b[1], b[2] - b[0], b[3] - b[1])
    }
}
