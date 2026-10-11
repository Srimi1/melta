package com.melta.app.shade.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.melta.app.R
import com.melta.app.battery.overlay.BatteryBarView
import com.melta.app.battery.service.BatteryOverlayService
import com.melta.app.battery.sprites.SpriteLoader
import com.melta.app.battery.ui.ChoiceChips
import com.melta.app.battery.ui.SettingsSection
import com.melta.app.battery.ui.SwitchRow
import com.melta.app.core.prefs.BatteryBarConfig
import com.melta.app.core.prefs.CardTheme
import com.melta.app.core.prefs.SettingsStore
import com.melta.app.shade.BatteryState
import com.melta.app.shade.CardThemeSpec
import com.melta.app.shade.CompanionCard
import com.melta.app.shade.tiles.CompanionTile
import com.melta.app.shade.tiles.DailyPokemonTile
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val scope = rememberCoroutineScope()
    val config by store.batteryBar.collectAsStateWithLifecycle(initialValue = BatteryBarConfig())
    val update: ((BatteryBarConfig) -> BatteryBarConfig) -> Unit = { t -> scope.launch { store.updateBatteryBar(t) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.module_shade)) },
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
            item { CardPreview(config) }

            if (!config.enabled) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.shade_needs_bar), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                scope.launch {
                                    store.updateBatteryBar { it.copy(enabled = true) }
                                    BatteryOverlayService.start(context)
                                }
                            }) { Text(stringResource(R.string.turn_on_bar)) }
                        }
                    }
                }
            }

            item {
                SettingsSection(stringResource(R.string.section_card)) {
                    SwitchRow(
                        stringResource(R.string.shade_card),
                        config.shadeCard,
                        subtitle = stringResource(R.string.shade_card_desc),
                    ) { v -> update { it.copy(shadeCard = v) } }
                    ChoiceChips(
                        stringResource(R.string.card_theme),
                        CardTheme.entries.toList(),
                        config.cardTheme,
                        label = { CardThemeSpec.of(it).label },
                    ) { v -> update { it.copy(cardTheme = v) } }
                }
            }

            item { TilesSection() }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.melta_shade_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.melta_shade_soon),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Live preview of the themed card, drawn by the same code as the real notification. */
@Composable
private fun CardPreview(config: BatteryBarConfig) {
    val context = LocalContext.current
    val view = remember { BatteryBarView(context) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    val (title, subtitle) = remember(config) {
        val (level, charging) = BatteryState.read(context)
        CompanionCard.texts(context, config, level, charging)
    }

    LaunchedEffect(config) {
        val snap = CompanionCard.snapshotConfig(config)
        val target = BatteryBarView.spriteTargetHeightPx(snap, CompanionCard.heightPx(context), context.resources.displayMetrics.density)
        val sprite = SpriteLoader.load(context, snap, target)
        val (level, charging) = BatteryState.read(context)
        image = CompanionCard.render(context, view, config, sprite, level, charging).asImageBitmap()
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.preview), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            // Looks like a notification in the shade.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(14.dp),
            ) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    image?.let {
                        Image(
                            bitmap = it,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TilesSection() {
    val context = LocalContext.current
    SettingsSection(stringResource(R.string.section_tiles)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.tiles_desc), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            if (Build.VERSION.SDK_INT >= 33) {
                FilledTonalButton(
                    onClick = { requestTile(context, CompanionTile::class.java, context.getString(R.string.tile_companion)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.add_companion_tile)) }
                FilledTonalButton(
                    onClick = { requestTile(context, DailyPokemonTile::class.java, context.getString(R.string.tile_daily)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.add_daily_tile)) }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                stringResource(R.string.tiles_manual),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start),
            )
        }
    }
}

/** Asks Android 13+ to add one of our tiles to Quick Settings, with a system confirmation. */
private fun requestTile(context: android.content.Context, cls: Class<*>, label: String) {
    if (Build.VERSION.SDK_INT < 33) return
    val sbm = context.getSystemService(StatusBarManager::class.java) ?: return
    sbm.requestAddTileService(
        ComponentName(context, cls),
        label,
        Icon.createWithResource(context, R.drawable.ic_stat_melta),
        context.mainExecutor,
    ) { result ->
        val msg = when (result) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.tile_added
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.tile_already_added
            else -> null
        }
        msg?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
}
