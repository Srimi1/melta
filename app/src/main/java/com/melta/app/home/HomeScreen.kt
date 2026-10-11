package com.melta.app.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.melta.app.R

private data class Module(
    val title: Int,
    val subtitle: Int,
    val icon: ImageVector,
    val stage: Int,
    val ready: Boolean,
)

private val modules = listOf(
    Module(R.string.module_battery, R.string.module_battery_desc, Icons.Filled.BatteryChargingFull, 1, true),
    Module(R.string.module_shade, R.string.module_shade_desc, Icons.Filled.Notifications, 2, true),
    Module(R.string.module_lock, R.string.module_lock_desc, Icons.Filled.Lock, 3, false),
    Module(R.string.module_wallpaper, R.string.module_wallpaper_desc, Icons.Filled.Wallpaper, 4, false),
    Module(R.string.module_icons, R.string.module_icons_desc, Icons.Filled.GridView, 5, false),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenBattery: () -> Unit, onOpenShade: () -> Unit) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_melta_logo),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.app_name))
                            Text(
                                stringResource(R.string.tagline),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(modules) { m ->
                val open = when (m.title) {
                    R.string.module_battery -> onOpenBattery
                    R.string.module_shade -> onOpenShade
                    else -> null
                }
                ModuleCard(m, onClick = if (m.ready) open else null)
            }
        }
    }
}

@Composable
private fun ModuleCard(m: Module, onClick: (() -> Unit)?) {
    val colors = if (m.ready) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(m.icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(m.title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(m.subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!m.ready) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(stringResource(R.string.coming_in_stage, m.stage)) },
                    )
                }
            }
            if (m.ready) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, colors = colors, content = { content() })
    } else {
        Card(colors = colors, content = { content() })
    }
}
