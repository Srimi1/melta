package com.melta.app.shade.tiles

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.melta.app.R
import com.melta.app.battery.sprites.Pokedex
import com.melta.app.core.prefs.SettingsStore
import com.melta.app.shade.BatteryState
import com.melta.app.shade.DailyPokemon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * "Pokémon of the day" tile. Tap to make today's Pokémon your companion, tap again for a
 * random surprise.
 */
class DailyPokemonTile : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun today(): Int = DailyPokemon.forDate(LocalDate.now().toEpochDay())

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refresh() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val store = SettingsStore.get(this@DailyPokemonTile)
            val current = store.batteryBar.first()
            val todays = today()
            val next = if (current.useCustomSprite || current.pokemonId != todays) todays else DailyPokemon.random(todays)
            store.updateBatteryBar { it.copy(pokemonId = next, useCustomSprite = false) }
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
        val todays = today()
        val showing = if (!config.useCustomSprite && config.pokemonId != todays) config.pokemonId else todays
        val (name, sprite) = withContext(Dispatchers.IO) {
            Pokedex.find(this@DailyPokemonTile, showing)?.name to BatteryState.trimmedSprite(this@DailyPokemonTile, todays)
        }
        tile.label = getString(R.string.tile_daily)
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = name
        tile.state = if (!config.useCustomSprite && config.pokemonId == todays) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = sprite?.let { Icon.createWithBitmap(it) } ?: Icon.createWithResource(this, R.drawable.ic_stat_melta)
        tile.updateTile()
    }
}
