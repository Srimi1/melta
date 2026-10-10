package com.melta.app.battery.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.melta.app.MainActivity
import com.melta.app.R
import com.melta.app.battery.overlay.BatteryBarView
import com.melta.app.battery.sprites.Pokedex
import com.melta.app.battery.sprites.SpriteLoader
import com.melta.app.core.prefs.BatteryBarConfig
import com.melta.app.core.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the battery bar on screen. One small window, one View, a couple of broadcast
 * receivers, nothing else. All animation stops while the screen is off.
 */
class BatteryOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var view: BatteryBarView? = null
    private var params: WindowManager.LayoutParams? = null
    private var config = BatteryBarConfig()
    private var spriteKey: String? = null
    private var spriteJob: Job? = null
    private var fullscreen = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onBattery(intent)
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> view?.setPaused(true)
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> view?.setPaused(false)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startInForeground()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        addOverlay()

        ContextCompat.registerReceiver(
            this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.let(::onBattery)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        scope.launch {
            SettingsStore.get(this@BatteryOverlayService).batteryBar.collect { onConfig(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                SettingsStore.get(this@BatteryOverlayService).updateBatteryBar { it.copy(enabled = false) }
            }
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        view?.statusBarHeightPx = statusBarHeight()
        updateVisibility()
        updateWindowSize()
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { unregisterReceiver(batteryReceiver) }
        runCatching { unregisterReceiver(screenReceiver) }
        view?.let { v -> runCatching { windowManager.removeView(v) } }
        view = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- overlay window

    private fun addOverlay() {
        val v = BatteryBarView(this)
        v.statusBarHeightPx = statusBarHeight()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            v.requiredHeightPx(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            title = "Melta battery bar"
            // Android 12+ lets taps pass through another app's overlay only when it is at most
            // 80% opaque, so the bar never blocks touches near the top of the screen.
            alpha = config.opacity.coerceAtMost(BatteryBarConfig.MAX_OPACITY)
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            if (Build.VERSION.SDK_INT >= 30) {
                // Draw at the very top instead of being pushed below the status bar.
                fitInsetsTypes = 0
            }
        }

        if (Build.VERSION.SDK_INT >= 30) {
            v.setOnApplyWindowInsetsListener { _, insets ->
                fullscreen = !insets.isVisible(WindowInsets.Type.statusBars())
                updateVisibility()
                insets
            }
        }

        runCatching { windowManager.addView(v, lp) }.onFailure {
            stopSelf()
            return
        }
        view = v
        params = lp
        v.applyConfig(config)
    }

    private fun updateWindowSize() {
        val v = view ?: return
        val lp = params ?: return
        val h = v.requiredHeightPx()
        val a = config.opacity.coerceAtMost(BatteryBarConfig.MAX_OPACITY)
        if (lp.height == h && lp.alpha == a) return
        lp.height = h
        lp.alpha = a
        runCatching { windowManager.updateViewLayout(v, lp) }
    }

    private fun updateVisibility() {
        val v = view ?: return
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val hide = (config.hideInLandscape && landscape) || (config.hideInFullscreen && fullscreen)
        v.setHidden(hide)
    }

    @Suppress("DiscouragedApi", "InternalInsetResource")
    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else (24 * resources.displayMetrics.density).toInt()
    }

    // ---------------------------------------------------------------- state

    private fun onBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (level < 0) return
        val percent = level * 100 / scale
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL && intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        view?.setBattery(percent, charging)
    }

    private fun onConfig(newConfig: BatteryBarConfig) {
        if (!newConfig.enabled) {
            stopSelf()
            return
        }
        config = newConfig
        val v = view ?: return
        v.applyConfig(newConfig)
        updateVisibility()
        updateWindowSize()

        if (newConfig.spriteKey != spriteKey) {
            spriteKey = newConfig.spriteKey
            spriteJob?.cancel()
            spriteJob = scope.launch {
                val target = BatteryBarView.spriteTargetHeightPx(newConfig, v.statusBarHeightPx, resources.displayMetrics.density)
                val sprite = SpriteLoader.load(this@BatteryOverlayService, newConfig, target)
                v.setSprite(sprite)
                updateWindowSize()
                updateNotification(newConfig)
            }
        }
    }

    // ---------------------------------------------------------------- notification

    private fun startInForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.channel_battery_bar), NotificationManager.IMPORTANCE_MIN).apply {
                    description = getString(R.string.channel_battery_bar_desc)
                    setShowBadge(false)
                },
            )
        }
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(null), type)
    }

    private fun updateNotification(c: BatteryBarConfig) {
        val name = if (c.useCustomSprite) getString(R.string.your_creation) else Pokedex.find(this, c.pokemonId)?.name
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(NOTIFICATION_ID, buildNotification(name)) }
    }

    private fun buildNotification(companion: String?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, BatteryOverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (companion != null) getString(R.string.notif_guarding, companion) else getString(R.string.notif_running)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_melta)
            .setContentTitle(getString(R.string.battery_bar))
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, getString(R.string.hide_bar), stop)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "battery_bar"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "com.melta.app.action.STOP_BATTERY_BAR"

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            // Background start limits can still refuse us in rare cases. Never crash over it,
            // the bar comes back the next time the app is opened.
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, BatteryOverlayService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BatteryOverlayService::class.java))
        }
    }
}
