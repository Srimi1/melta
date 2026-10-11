package com.melta.app.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.meltaDataStore: DataStore<Preferences> by preferencesDataStore(name = "melta_settings")

/** Single source of truth for user settings. Shared by the UI and the overlay service. */
class SettingsStore private constructor(private val store: DataStore<Preferences>) {

    val batteryBar: Flow<BatteryBarConfig> = store.data
        .map { it.toConfig() }
        .distinctUntilChanged()

    suspend fun updateBatteryBar(transform: (BatteryBarConfig) -> BatteryBarConfig) {
        store.edit { prefs ->
            val next = transform(prefs.toConfig())
            prefs.write(next)
        }
    }

    private object K {
        val enabled = booleanPreferencesKey("bb_enabled")
        val pokemonId = intPreferencesKey("bb_pokemon_id")
        val shiny = booleanPreferencesKey("bb_shiny")
        val animated = booleanPreferencesKey("bb_animated")
        val useCustomSprite = booleanPreferencesKey("bb_use_custom_sprite")
        val customSpritePath = stringPreferencesKey("bb_custom_sprite_path")
        val spriteSize = intPreferencesKey("bb_sprite_size")
        val sizeMode = stringPreferencesKey("bb_size_mode")
        val matchColors = booleanPreferencesKey("bb_match_colors")
        val anchor = stringPreferencesKey("bb_anchor")
        val flip = booleanPreferencesKey("bb_flip")
        val bob = booleanPreferencesKey("bb_bob")
        val position = stringPreferencesKey("bb_position")
        val barMode = stringPreferencesKey("bb_mode")
        val thickness = intPreferencesKey("bb_thickness")
        val opacity = floatPreferencesKey("bb_opacity")
        val fillStyle = stringPreferencesKey("bb_fill_style")
        val colorStart = intPreferencesKey("bb_color_start")
        val colorEnd = intPreferencesKey("bb_color_end")
        val customTilePath = stringPreferencesKey("bb_custom_tile_path")
        val effect = stringPreferencesKey("bb_effect")
        val effectIntensity = floatPreferencesKey("bb_effect_intensity")
        val celebrateFull = booleanPreferencesKey("bb_celebrate_full")
        val showPercent = booleanPreferencesKey("bb_show_percent")
        val lowThreshold = intPreferencesKey("bb_low_threshold")
        val hideInLandscape = booleanPreferencesKey("bb_hide_landscape")
        val hideInFullscreen = booleanPreferencesKey("bb_hide_fullscreen")
    }

    private fun Preferences.toConfig(): BatteryBarConfig {
        val d = BatteryBarConfig()
        return BatteryBarConfig(
            enabled = this[K.enabled] ?: d.enabled,
            pokemonId = this[K.pokemonId] ?: d.pokemonId,
            shiny = this[K.shiny] ?: d.shiny,
            animated = this[K.animated] ?: d.animated,
            useCustomSprite = this[K.useCustomSprite] ?: d.useCustomSprite,
            customSpritePath = this[K.customSpritePath],
            spriteSizeDp = this[K.spriteSize] ?: d.spriteSizeDp,
            sizeMode = enumOr(this[K.sizeMode], d.sizeMode),
            matchColors = this[K.matchColors] ?: d.matchColors,
            anchor = enumOr(this[K.anchor], d.anchor),
            flip = this[K.flip] ?: d.flip,
            bob = this[K.bob] ?: d.bob,
            position = enumOr(this[K.position], d.position),
            barMode = enumOr(this[K.barMode], d.barMode),
            barThicknessDp = this[K.thickness] ?: d.barThicknessDp,
            opacity = (this[K.opacity] ?: d.opacity)
                .coerceIn(BatteryBarConfig.MIN_OPACITY, BatteryBarConfig.MAX_OPACITY),
            fillStyle = enumOr(this[K.fillStyle], d.fillStyle),
            colorStart = this[K.colorStart] ?: d.colorStart,
            colorEnd = this[K.colorEnd] ?: d.colorEnd,
            customTilePath = this[K.customTilePath],
            effect = enumOr(this[K.effect], d.effect),
            effectIntensity = this[K.effectIntensity] ?: d.effectIntensity,
            celebrateFull = this[K.celebrateFull] ?: d.celebrateFull,
            showPercent = this[K.showPercent] ?: d.showPercent,
            lowThreshold = this[K.lowThreshold] ?: d.lowThreshold,
            hideInLandscape = this[K.hideInLandscape] ?: d.hideInLandscape,
            hideInFullscreen = this[K.hideInFullscreen] ?: d.hideInFullscreen,
        )
    }

    private fun MutablePreferences.write(c: BatteryBarConfig) {
        this[K.enabled] = c.enabled
        this[K.pokemonId] = c.pokemonId
        this[K.shiny] = c.shiny
        this[K.animated] = c.animated
        this[K.useCustomSprite] = c.useCustomSprite
        putOrRemove(K.customSpritePath, c.customSpritePath)
        this[K.spriteSize] = c.spriteSizeDp
        this[K.sizeMode] = c.sizeMode.name
        this[K.matchColors] = c.matchColors
        this[K.anchor] = c.anchor.name
        this[K.flip] = c.flip
        this[K.bob] = c.bob
        this[K.position] = c.position.name
        this[K.barMode] = c.barMode.name
        this[K.thickness] = c.barThicknessDp
        this[K.opacity] = c.opacity
        this[K.fillStyle] = c.fillStyle.name
        this[K.colorStart] = c.colorStart
        this[K.colorEnd] = c.colorEnd
        putOrRemove(K.customTilePath, c.customTilePath)
        this[K.effect] = c.effect.name
        this[K.effectIntensity] = c.effectIntensity
        this[K.celebrateFull] = c.celebrateFull
        this[K.showPercent] = c.showPercent
        this[K.lowThreshold] = c.lowThreshold
        this[K.hideInLandscape] = c.hideInLandscape
        this[K.hideInFullscreen] = c.hideInFullscreen
    }

    private fun MutablePreferences.putOrRemove(key: Preferences.Key<String>, value: String?) {
        if (value == null) remove(key) else this[key] = value
    }

    companion object {
        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext.meltaDataStore).also { instance = it }
            }
    }
}

private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback
