package com.melta.app.shade.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.melta.app.R
import com.melta.app.battery.ui.ChoiceChips
import com.melta.app.battery.ui.SettingsSection
import com.melta.app.battery.ui.SwitchRow
import com.melta.app.core.prefs.BatteryBarConfig
import com.melta.app.core.prefs.TriggerZone
import com.melta.app.shade.custom.MeltaNotificationListener
import com.melta.app.shade.custom.MeltaShadeService

/**
 * Setup for Melta Shade. Each permission is a step with a live done or not done check, plus the
 * same "greyed out?" help as the battery bar, because Android locks these for sideloaded apps.
 */
@Composable
fun MeltaShadeSection(config: BatteryBarConfig, update: ((BatteryBarConfig) -> BatteryBarConfig) -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val a11y = remember(refresh) { MeltaShadeService.isEnabled(context) }
    val listener = remember(refresh) { MeltaNotificationListener.isEnabled(context) }
    val write = remember(refresh) { Settings.System.canWrite(context) }
    var showHelp by remember { mutableStateOf(false) }

    SettingsSection(stringResource(R.string.melta_shade_title)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.melta_shade_desc), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Step(stringResource(R.string.setup_step_a11y), a11y) { open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            Step(stringResource(R.string.setup_step_listener), listener) {
                open(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            Step(stringResource(R.string.setup_step_write), write) {
                open(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val service = MeltaShadeService.instance
                    if (service != null) {
                        service.openPanel()
                    } else {
                        Toast.makeText(context, R.string.shade_not_running, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.test_shade)) }
                OutlinedButton(onClick = { showHelp = !showHelp }) { Text(stringResource(R.string.perm_greyed_out)) }
            }
            if (showHelp) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.perm_restricted_steps), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = {
                    open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text(stringResource(R.string.open_app_info)) }
            }
        }
        SwitchRow(stringResource(R.string.use_melta_shade), config.customShade) { v -> update { it.copy(customShade = v) } }
        ChoiceChips(
            stringResource(R.string.trigger_zone),
            TriggerZone.entries.toList(),
            config.triggerZone,
            label = {
                when (it) {
                    TriggerZone.LEFT -> "Left half"
                    TriggerZone.RIGHT -> "Right half"
                    TriggerZone.FULL -> "Whole edge"
                }
            },
        ) { v -> update { it.copy(triggerZone = v) } }
    }
}

@Composable
private fun Step(label: String, done: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (!done) {
            FilledTonalButton(onClick = onOpen) { Text(stringResource(R.string.setup_open)) }
        } else {
            Text(stringResource(R.string.setup_done), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
