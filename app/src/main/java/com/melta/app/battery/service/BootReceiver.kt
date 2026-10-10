package com.melta.app.battery.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.melta.app.core.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Brings the battery bar back after a reboot or app update, if the user had it on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (SettingsStore.get(app).batteryBar.first().enabled) {
                    BatteryOverlayService.start(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
