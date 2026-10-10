package com.melta.app.battery.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.melta.app.R
import com.melta.app.battery.creations.CreationKind
import com.melta.app.battery.creations.CreationStore
import com.melta.app.battery.overlay.BatteryBarView
import com.melta.app.battery.overlay.EffectPicker
import com.melta.app.battery.service.BatteryOverlayService
import com.melta.app.battery.sprites.LoadedSprite
import com.melta.app.battery.sprites.Pokedex
import com.melta.app.battery.sprites.SpriteLoader
import com.melta.app.battery.sprites.SpriteRepository
import com.melta.app.core.prefs.BarPosition
import com.melta.app.core.prefs.BatteryBarConfig
import com.melta.app.core.prefs.ChargeEffect
import com.melta.app.core.prefs.FillStyle
import com.melta.app.core.prefs.SettingsStore
import com.melta.app.core.prefs.SpriteAnchor
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(onBack: () -> Unit, onPickCompanion: () -> Unit, onCreatePattern: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val scope = rememberCoroutineScope()
    val config by store.batteryBar.collectAsStateWithLifecycle(initialValue = BatteryBarConfig())
    val update: ((BatteryBarConfig) -> BatteryBarConfig) -> Unit = { t -> scope.launch { store.updateBatteryBar(t) } }

    var canDraw by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canDraw = Settings.canDrawOverlays(context) }

    // Keep the service in step with the saved switch, e.g. after granting the permission.
    LaunchedEffect(config.enabled, canDraw) {
        if (config.enabled && canDraw) BatteryOverlayService.start(context)
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun openOverlaySettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun setEnabled(on: Boolean) {
        if (on && !canDraw) {
            openOverlaySettings()
            return
        }
        if (on && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        scope.launch {
            store.updateBatteryBar { it.copy(enabled = on) }
            if (on) BatteryOverlayService.start(context) else BatteryOverlayService.stop(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.module_battery)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { PreviewCard(config) }

            if (!canDraw) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.perm_body), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = ::openOverlaySettings) { Text(stringResource(R.string.perm_grant)) }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    SwitchRow(
                        title = stringResource(R.string.show_bar),
                        subtitle = stringResource(R.string.show_bar_desc),
                        checked = config.enabled && canDraw,
                        onChange = ::setEnabled,
                    )
                }
            }

            item { CompanionSection(config, update, onPickCompanion) }
            item { BarSection(config, update, onCreatePattern) }
            item { EffectSection(config, update) }
            item { ExtrasSection(config, update) }
        }
    }
}

// ------------------------------------------------------------------- live preview

@Composable
private fun PreviewCard(config: BatteryBarConfig) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val statusBarPx = with(density) { 28.dp.roundToPx() }
    var simLevel by remember { mutableFloatStateOf(64f) }
    var simCharging by remember { mutableStateOf(true) }

    val sprite by produceState<LoadedSprite?>(null, config.spriteKey, config.position, config.barThicknessDp) {
        val target = BatteryBarView.spriteTargetHeightPx(config, statusBarPx, density.density)
        value = SpriteLoader.load(context, config, target)
    }

    Card(Modifier.fillMaxWidth()) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF2A1B4A), Color(0xFF6B3FA0)))),
            ) {
                // A fake status bar so the preview looks like the real thing.
                Row(
                    Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("12:30", color = Color.White, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text("5G  ${simLevel.roundToInt()}%", color = Color.White, fontSize = 12.sp)
                }
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { BatteryBarView(it).apply { statusBarHeightPx = statusBarPx } },
                    update = { v ->
                        v.applyConfig(config)
                        v.alpha = config.opacity
                        if (v.tag !== sprite) {
                            v.tag = sprite
                            v.setSprite(sprite)
                        }
                        v.setBattery(simLevel.roundToInt(), simCharging)
                    },
                )
                Text(
                    stringResource(R.string.preview),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                )
            }
            SwitchRow(stringResource(R.string.sim_charging), simCharging) { simCharging = it }
            SliderRow(stringResource(R.string.sim_level), simLevel, 0f..100f, "${simLevel.roundToInt()}%") { simLevel = it }
        }
    }
}

// ------------------------------------------------------------------- sections

@Composable
private fun CompanionSection(
    config: BatteryBarConfig,
    update: ((BatteryBarConfig) -> BatteryBarConfig) -> Unit,
    onPickCompanion: () -> Unit,
) {
    val context = LocalContext.current
    val entry = remember(config.pokemonId) { Pokedex.find(context, config.pokemonId) }
    val custom = config.useCustomSprite && config.customSpritePath != null

    SettingsSection(stringResource(R.string.section_companion)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onPickCompanion).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = if (custom) File(config.customSpritePath!!) else SpriteRepository.staticUrl(config.pokemonId, config.shiny),
                contentDescription = null,
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (custom) stringResource(R.string.your_creation) else entry?.name ?: "#${config.pokemonId}",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!custom && entry != null) {
                    Text(
                        entry.number + "  " + entry.types.joinToString(" / ") { it.replaceFirstChar(Char::uppercase) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledTonalButton(onClick = onPickCompanion) { Text(stringResource(R.string.change)) }
        }
        if (!custom) {
            SwitchRow(stringResource(R.string.shiny), config.shiny) { v -> update { it.copy(shiny = v) } }
            SwitchRow(
                stringResource(R.string.animated),
                config.animated,
                subtitle = stringResource(R.string.animated_desc),
            ) { v -> update { it.copy(animated = v) } }
        }
        SliderRow(stringResource(R.string.size), config.spriteSizeDp.toFloat(), 12f..48f, "${config.spriteSizeDp} dp") { v ->
            update { it.copy(spriteSizeDp = v.roundToInt()) }
        }
        ChoiceChips(
            stringResource(R.string.where_it_sits),
            SpriteAnchor.entries.toList(),
            config.anchor,
            label = { anchorLabel(it) },
        ) { v -> update { it.copy(anchor = v) } }
        SwitchRow(stringResource(R.string.flip), config.flip) { v -> update { it.copy(flip = v) } }
        SwitchRow(stringResource(R.string.bob), config.bob, subtitle = stringResource(R.string.bob_desc)) { v ->
            update { it.copy(bob = v) }
        }
    }
}

@Composable
private fun BarSection(
    config: BatteryBarConfig,
    update: ((BatteryBarConfig) -> BatteryBarConfig) -> Unit,
    onCreatePattern: () -> Unit,
) {
    val context = LocalContext.current
    val tiles by produceState(emptyList<File>()) {
        value = CreationStore.list(context).filter { it.kind == CreationKind.TILE }.map { it.file }
    }

    SettingsSection(stringResource(R.string.section_bar)) {
        ChoiceChips(
            stringResource(R.string.position),
            BarPosition.entries.toList(),
            config.position,
            label = { if (it == BarPosition.STATUS_BAR_BOTTOM) "Under status bar" else "Screen top" },
        ) { v -> update { it.copy(position = v) } }
        SliderRow(stringResource(R.string.thickness), config.barThicknessDp.toFloat(), 1f..10f, "${config.barThicknessDp} dp", steps = 8) { v ->
            update { it.copy(barThicknessDp = v.roundToInt()) }
        }
        SliderRow(
            stringResource(R.string.opacity),
            config.opacity,
            BatteryBarConfig.MIN_OPACITY..BatteryBarConfig.MAX_OPACITY,
            "${(config.opacity * 100).roundToInt()}%",
        ) { v -> update { it.copy(opacity = v) } }
        ChoiceChips(
            stringResource(R.string.fill_style),
            FillStyle.entries.toList(),
            config.fillStyle,
            label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
        ) { v -> update { it.copy(fillStyle = v) } }

        if (config.fillStyle != FillStyle.RAINBOW && config.fillStyle != FillStyle.CUSTOM) {
            SwatchRow(stringResource(R.string.color_main), config.colorStart) { c -> update { it.copy(colorStart = c) } }
            if (config.fillStyle != FillStyle.SOLID) {
                SwatchRow(stringResource(R.string.color_second), config.colorEnd) { c -> update { it.copy(colorEnd = c) } }
            }
        }

        if (config.fillStyle == FillStyle.CUSTOM) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.my_patterns), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                if (tiles.isEmpty()) {
                    Text(
                        stringResource(R.string.no_patterns),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tiles) { f ->
                            val selected = config.customTilePath == f.absolutePath
                            AsyncImage(
                                model = f,
                                contentDescription = null,
                                filterQuality = FilterQuality.None,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        if (selected) 3.dp else 1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else Color(0x33000000),
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable { update { it.copy(customTilePath = f.absolutePath) } },
                            )
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = onCreatePattern, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Icon(Icons.Filled.Brush, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.create_pattern))
        }
    }
}

@Composable
private fun EffectSection(config: BatteryBarConfig, update: ((BatteryBarConfig) -> BatteryBarConfig) -> Unit) {
    val context = LocalContext.current
    val types = remember(config.pokemonId) { Pokedex.find(context, config.pokemonId)?.types.orEmpty() }
    SettingsSection(stringResource(R.string.section_charging)) {
        ChoiceChips(
            null,
            ChargeEffect.entries.toList(),
            config.effect,
            label = {
                if (it == ChargeEffect.AUTO && !config.useCustomSprite) {
                    "Auto (" + effectLabel(EffectPicker.resolve(it, types)) + ")"
                } else {
                    effectLabel(it)
                }
            },
        ) { v -> update { it.copy(effect = v) } }
        SliderRow(
            stringResource(R.string.intensity),
            config.effectIntensity,
            0.1f..1f,
            "${(config.effectIntensity * 100).roundToInt()}%",
        ) { v -> update { it.copy(effectIntensity = v) } }
        SwitchRow(
            stringResource(R.string.celebrate),
            config.celebrateFull,
            subtitle = stringResource(R.string.celebrate_desc),
        ) { v -> update { it.copy(celebrateFull = v) } }
    }
}

@Composable
private fun ExtrasSection(config: BatteryBarConfig, update: ((BatteryBarConfig) -> BatteryBarConfig) -> Unit) {
    SettingsSection(stringResource(R.string.section_extras)) {
        SwitchRow(stringResource(R.string.show_percent), config.showPercent) { v -> update { it.copy(showPercent = v) } }
        SliderRow(stringResource(R.string.low_threshold), config.lowThreshold.toFloat(), 5f..50f, "${config.lowThreshold}%") { v ->
            update { it.copy(lowThreshold = v.roundToInt()) }
        }
        SwitchRow(stringResource(R.string.hide_landscape), config.hideInLandscape) { v -> update { it.copy(hideInLandscape = v) } }
        SwitchRow(
            stringResource(R.string.hide_fullscreen),
            config.hideInFullscreen,
            subtitle = stringResource(R.string.hide_fullscreen_desc),
        ) { v -> update { it.copy(hideInFullscreen = v) } }
    }
}

private fun anchorLabel(a: SpriteAnchor) = when (a) {
    SpriteAnchor.FOLLOW_LEVEL -> "Rides the level"
    SpriteAnchor.LEFT -> "Left"
    SpriteAnchor.CENTER -> "Center"
    SpriteAnchor.RIGHT -> "Right"
}

fun effectLabel(e: ChargeEffect) = when (e) {
    ChargeEffect.AUTO -> "Auto"
    ChargeEffect.SPARKS -> "Sparks"
    ChargeEffect.BOLTS -> "Thunder"
    ChargeEffect.BUBBLES -> "Bubbles"
    ChargeEffect.FLAMES -> "Flames"
    ChargeEffect.LEAVES -> "Leaves"
    ChargeEffect.HEARTS -> "Hearts"
    ChargeEffect.SNOW -> "Snow"
    ChargeEffect.NONE -> "None"
}
