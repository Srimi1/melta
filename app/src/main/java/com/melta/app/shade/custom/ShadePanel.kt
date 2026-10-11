package com.melta.app.shade.custom

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.melta.app.R
import com.melta.app.battery.overlay.BatteryBarView
import com.melta.app.battery.service.BatteryOverlayService
import com.melta.app.battery.sprites.SpriteLoader
import com.melta.app.core.prefs.BatteryBarConfig
import com.melta.app.core.prefs.SettingsStore
import com.melta.app.shade.BatteryState
import com.melta.app.shade.CardThemeSpec
import com.melta.app.shade.CompanionCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

/** How long the panel takes to slide in and out. */
const val ShadePanelAnimMs = 220

/** Melta Shade: header with the companion card, quick toggles, brightness and notifications. */
@Composable
fun ShadePanel(
    visible: Boolean,
    config: BatteryBarConfig,
    onClose: () -> Unit,
    onAction: (ShadeAction) -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    // Start hidden and flip on the next frame, so opening always slides in.
    LaunchedEffect(visible) { state.targetState = visible }

    val spec = CardThemeSpec.of(config.cardTheme)
    val bg = spec.background?.let { Color(it) } ?: MaterialTheme.colorScheme.surface
    val fg = if (ColorUtils.calculateLuminance(bg.toArgb()) < 0.5) Color.White else Color(0xFF1C1B1F)

    Box(Modifier.fillMaxSize()) {
        // Dim the app below. Tapping it closes the shade.
        AnimatedVisibility(state, enter = fadeIn(tween(ShadePanelAnimMs)), exit = fadeOut(tween(ShadePanelAnimMs))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            )
        }
        AnimatedVisibility(
            state,
            enter = slideInVertically(tween(ShadePanelAnimMs)) { -it } + fadeIn(tween(ShadePanelAnimMs)),
            exit = slideOutVertically(tween(ShadePanelAnimMs)) { -it } + fadeOut(tween(ShadePanelAnimMs)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 760.dp)
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(bg)
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Header(fg, onAction)
                Spacer(Modifier.height(10.dp))
                CompanionHeader(config)
                Spacer(Modifier.height(12.dp))
                QuickToggles(config, fg, onAction)
                Spacer(Modifier.height(8.dp))
                BrightnessRow(fg, onAction)
                Spacer(Modifier.height(8.dp))
                NotificationsList(fg, onAction)
                Spacer(Modifier.height(4.dp))
                // Grab handle: tap to close.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClose)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(fg.copy(alpha = 0.4f)))
                }
            }
        }
    }
}

@Composable
private fun Header(fg: Color, onAction: (ShadeAction) -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(DateFormat.getTimeFormat(context).format(Date(now)), color = fg, fontSize = 34.sp, fontWeight = FontWeight.Light)
            Text(DateFormat.format("EEEE, MMMM d", now).toString(), color = fg.copy(alpha = 0.75f), fontSize = 14.sp)
        }
        RoundIcon(Icons.Filled.Tune, stringResource(R.string.qs_settings), fg) {
            onAction(ShadeAction.Launch(Intent(Settings.ACTION_SETTINGS)))
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(fg.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = fg)
    }
}

@Composable
private fun CompanionHeader(config: BatteryBarConfig) {
    val context = LocalContext.current
    val view = remember { BatteryBarView(context) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(config) {
        val snap = CompanionCard.snapshotConfig(config)
        val target = BatteryBarView.spriteTargetHeightPx(snap, CompanionCard.heightPx(context), context.resources.displayMetrics.density)
        val sprite = SpriteLoader.load(context, snap, target)
        while (true) {
            val (level, charging) = BatteryState.read(context)
            image = CompanionCard.render(context, view, config, sprite, level, charging).asImageBitmap()
            delay(30_000)
        }
    }
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

private class QuickToggle(
    val label: String,
    val icon: ImageVector,
    val active: Boolean,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickToggles(config: BatteryBarConfig, fg: Color, onAction: (ShadeAction) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val torch = rememberTorch(context)
    var tick by remember { mutableLongStateOf(0L) } // bumps after a toggle so states are read again
    val nm = context.getSystemService(NotificationManager::class.java)
    val dndOn = remember(tick) { nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL }
    val autoRotate = remember(tick) {
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
    }

    fun panel(action: String, fallback: String) =
        onAction(ShadeAction.Launch(Intent(if (Build.VERSION.SDK_INT >= 29) action else fallback)))

    val toggles = buildList {
        add(QuickToggle(stringResource(R.string.qs_wifi), Icons.Filled.Wifi, false) {
            panel(Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS)
        })
        add(QuickToggle(stringResource(R.string.qs_internet), Icons.Filled.Language, false) {
            panel(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIRELESS_SETTINGS)
        })
        add(QuickToggle(stringResource(R.string.qs_bluetooth), Icons.Filled.Bluetooth, false) {
            onAction(ShadeAction.Launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)))
        })
        if (torch.available) {
            add(QuickToggle(stringResource(R.string.qs_flashlight), Icons.Filled.FlashlightOn, torch.on) { torch.toggle() })
        }
        add(QuickToggle(stringResource(R.string.qs_dnd), Icons.Filled.DoNotDisturbOn, dndOn) {
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(
                    if (dndOn) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                )
                tick++
            } else {
                onAction(ShadeAction.Launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)))
            }
        })
        add(QuickToggle(stringResource(R.string.qs_rotate), Icons.Filled.ScreenRotation, autoRotate) {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (autoRotate) 0 else 1)
                tick++
            } else {
                onAction(ShadeAction.Launch(writeSettingsIntent(context)))
            }
        })
        add(QuickToggle(stringResource(R.string.qs_volume), Icons.AutoMirrored.Filled.VolumeUp, false) {
            panel(Settings.Panel.ACTION_VOLUME, Settings.ACTION_SOUND_SETTINGS)
        })
        add(QuickToggle(stringResource(R.string.qs_battery_bar), Icons.Filled.BatteryChargingFull, config.enabled) {
            scope.launch {
                val on = !config.enabled
                SettingsStore.get(context).updateBatteryBar { it.copy(enabled = on) }
                if (on) BatteryOverlayService.start(context) else BatteryOverlayService.stop(context)
            }
        })
        if (Build.VERSION.SDK_INT >= 28) {
            add(QuickToggle(stringResource(R.string.qs_screenshot), Icons.Filled.Screenshot, false) {
                onAction(ShadeAction.Global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT))
            })
            add(QuickToggle(stringResource(R.string.qs_lock), Icons.Filled.Lock, false) {
                onAction(ShadeAction.Global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN))
            })
        }
        add(QuickToggle(stringResource(R.string.qs_system_shade), Icons.Filled.Notifications, false) {
            onAction(ShadeAction.Global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS))
        })
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 4,
        modifier = Modifier.fillMaxWidth(),
    ) {
        toggles.forEach { t -> ToggleButton(t, fg, Modifier.weight(1f)) }
    }
}

@Composable
private fun ToggleButton(t: QuickToggle, fg: Color, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (t.active) accent else fg.copy(alpha = 0.10f))
            .clickable(onClick = t.onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tint = if (t.active) MaterialTheme.colorScheme.onPrimary else fg
        Icon(t.icon, contentDescription = t.label, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(t.label, color = tint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun writeSettingsIntent(context: Context) =
    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))

@Composable
private fun BrightnessRow(fg: Color, onAction: (ShadeAction) -> Unit) {
    val context = LocalContext.current
    val canWrite = Settings.System.canWrite(context)
    var value by remember {
        mutableFloatStateOf(Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.BrightnessMedium, contentDescription = stringResource(R.string.qs_brightness), tint = fg)
        Spacer(Modifier.width(8.dp))
        if (canWrite) {
            Slider(
                value = value,
                onValueChange = { v ->
                    value = v
                    val cr = context.contentResolver
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (v * 255).toInt().coerceIn(1, 255))
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            TextButton(onClick = { onAction(ShadeAction.Launch(writeSettingsIntent(context))) }) {
                Text(stringResource(R.string.qs_allow_brightness), color = fg)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsList(fg: Color, onAction: (ShadeAction) -> Unit) {
    val context = LocalContext.current
    val items by MeltaNotificationListener.notifications.collectAsStateWithLifecycle()
    val connected = MeltaNotificationListener.instance != null

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.shade_notifications), color = fg, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (items.any { it.clearable }) {
            TextButton(onClick = { MeltaNotificationListener.instance?.clearAll() }) {
                Text(stringResource(R.string.shade_clear_all), color = fg)
            }
        }
    }

    if (!connected) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(fg.copy(alpha = 0.08f))
                .padding(14.dp),
        ) {
            Text(stringResource(R.string.shade_need_access), color = fg, fontSize = 13.sp)
            TextButton(onClick = { onAction(ShadeAction.Launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) }) {
                Text(stringResource(R.string.shade_allow_access), color = fg)
            }
        }
        return
    }
    if (items.isEmpty()) {
        Text(stringResource(R.string.shade_no_notifications), color = fg.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
        return
    }

    val now = System.currentTimeMillis()
    ShadeLogic.groupByApp(items, { it.packageName }, { it.postTime }).forEach { (_, group) ->
        val first = group.first()
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(fg.copy(alpha = 0.08f))
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = remember(first.packageName) { first.icon?.toBitmap(48, 48)?.asImageBitmap() }
                icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(6.dp))
                Text(first.appName, color = fg.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(ShadeLogic.ago(now, first.postTime), color = fg.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            group.take(4).forEach { n ->
                key(n.key) {
                    val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                        if (v != SwipeToDismissBoxValue.Settled) {
                            MeltaNotificationListener.instance?.dismiss(n.key)
                            true
                        } else {
                            false
                        }
                    })
                    SwipeToDismissBox(
                        state = dismiss,
                        backgroundContent = {},
                        enableDismissFromStartToEnd = n.clearable,
                        enableDismissFromEndToStart = n.clearable,
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (MeltaNotificationListener.instance?.open(n.key) == true) onAction(ShadeAction.Close)
                                }
                                .padding(top = 8.dp),
                        ) {
                            if (n.title.isNotBlank()) {
                                Text(n.title, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (n.text.isNotBlank()) {
                                Text(n.text, color = fg.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (group.size > 4) {
                Text(
                    context.getString(R.string.shade_more, group.size - 4),
                    color = fg.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** Flashlight state that follows the real torch, including changes made elsewhere. */
private class TorchState(val available: Boolean) {
    var on by mutableStateOf(false)
    var toggle: () -> Unit = {}
}

@Composable
private fun rememberTorch(context: Context): TorchState {
    val cm = remember { context.getSystemService(CameraManager::class.java) }
    val cameraId = remember {
        runCatching {
            cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }
    val torch = remember {
        TorchState(cameraId != null).also { t ->
            t.toggle = { cameraId?.let { id -> runCatching { cm.setTorchMode(id, !t.on) } } }
        }
    }
    DisposableEffect(cameraId) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) {
                if (id == cameraId) torch.on = enabled
            }
        }
        runCatching { cm.registerTorchCallback(callback, null) }
        onDispose { runCatching { cm.unregisterTorchCallback(callback) } }
    }
    return torch
}
