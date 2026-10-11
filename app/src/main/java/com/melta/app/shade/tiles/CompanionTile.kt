package com.melta.app.shade.tiles

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.melta.app.MainActivity
import com.melta.app.R
import com.melta.app.battery.service.BatteryOverlayService
import com.melta.app.core.prefs.SettingsStore
import com.melta.app.shade.BatteryState
import com.melta.app.shade.CompanionCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile showing your companion and battery level. Tap to turn the battery bar
 * on or off. Needs no permissions at all.
 */
class CompanionTile : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refresh() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val store = SettingsStore.get(this@CompanionTile)
            val turnOn = !store.batteryBar.first().enabled
            store.updateBatteryBar { it.copy(enabled = turnOn) }
            if (turnOn) {
                // Android may refuse to start a service from the shade. Then open Melta, which starts it.
                if (!BatteryOverlayService.start(this@CompanionTile)) openApp()
            } else {
                BatteryOverlayService.stop(this@CompanionTile)
            }
            refresh()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun refresh() {
        val tile = qsTile ?: return
        val config = SettingsStore.get(this).batteryBar.first()
        val (level, charging) = BatteryState.read(this)
        tile.label = CompanionCard.companionName(this, config) ?: getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when {
                level < 0 -> null
                charging -> getString(R.string.tile_level_charging, level)
                else -> getString(R.string.tile_level, level)
            }
        }
        tile.state = if (config.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        val sprite = if (config.useCustomSprite) null else BatteryState.trimmedSprite(this, config.pokemonId)
        tile.icon = sprite?.let { Icon.createWithBitmap(it) } ?: Icon.createWithResource(this, R.drawable.ic_stat_melta)
        tile.updateTile()
    }

    private fun openApp() = launchFromTile(this, Intent(this, MainActivity::class.java))
}

/** Opens an activity from a tile and collapses the shade, on every Android version. */
internal fun launchFromTile(service: TileService, intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (Build.VERSION.SDK_INT >= 34) {
        service.startActivityAndCollapse(
            PendingIntent.getActivity(service, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
    } else {
        @Suppress("DEPRECATION")
        service.startActivityAndCollapse(intent)
    }
}
