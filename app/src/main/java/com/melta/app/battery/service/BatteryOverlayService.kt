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
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.melta.app.MainActivity
import com.melta.app.R
import com.melta.app.battery.overlay.BatteryBarView
import com.melta.app.battery.sprites.LoadedSprite
import com.melta.app.battery.sprites.Pokedex
import com.melta.app.battery.sprites.SpriteLoader
import com.melta.app.core.prefs.BarMode
import com.melta.app.core.prefs.BarModes
import com.melta.app.core.prefs.BarPosition
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
 *
 * Without the "Display over other apps" permission (Android locks it for sideloaded apps) the
 * bar is shown in the notification panel instead, so it always works.
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
    private var configLoaded = false
    private var mode = BarMode.NOTIFICATION
    private var overlayFailed = false
    private var sprite: LoadedSprite? = null
    private var level = -1
    private var charging = false
    private var snapshotView: BatteryBarView? = null
    private var lastNotificationKey: String? = null

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
        // Started again from the app, e.g. right after the overlay permission was granted.
        if (configLoaded) onConfig(config)
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
        removeOverlay()
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
            // Some phones refuse overlays even with the permission. Use the notification panel.
            overlayFailed = true
            return
        }
        view = v
        params = lp
        v.applyConfig(config)
        if (level >= 0) v.setBattery(level, charging)
    }

    private fun removeOverlay() {
        view?.let { v -> runCatching { windowManager.removeView(v) } }
        view = null
        params = null
    }

    /** Puts the bar where it belongs right now: over the status bar, or in the notification panel. */
    private fun refreshMode() {
        val canDraw = Settings.canDrawOverlays(this) && !overlayFailed
        val newMode = BarModes.effective(config.barMode, canDraw)
        if (newMode == BarMode.OVERLAY && view == null) addOverlay()
        if (newMode == BarMode.NOTIFICATION && view != null) removeOverlay()
        val actual = if (view != null) BarMode.OVERLAY else BarMode.NOTIFICATION
        if (actual != mode) {
            mode = actual
            spriteKey = null // the two modes use differently sized sprites
            lastNotificationKey = null
        }
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
        this.level = level * 100 / scale
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL && intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        view?.setBattery(this.level, charging)
        if (mode == BarMode.NOTIFICATION) postBarNotification()
    }

    private fun onConfig(newConfig: BatteryBarConfig) {
        if (!newConfig.enabled) {
            stopSelf()
            return
        }
        config = newConfig
        configLoaded = true
        refreshMode()
        view?.let { v ->
            v.applyConfig(newConfig)
            updateVisibility()
            updateWindowSize()
        }

        val key = "${newConfig.spriteKey}:$mode"
        if (key != spriteKey) {
            spriteKey = key
            spriteJob?.cancel()
            spriteJob = scope.launch {
                val loaded = if (mode == BarMode.OVERLAY) {
                    val target = BatteryBarView.spriteTargetHeightPx(newConfig, statusBarHeight(), resources.displayMetrics.density)
                    SpriteLoader.load(this@BatteryOverlayService, newConfig, target)
                } else {
                    val snap = snapshotConfig(newConfig)
                    val target = BatteryBarView.spriteTargetHeightPx(snap, snapshotHeight(), resources.displayMetrics.density)
                    SpriteLoader.load(this@BatteryOverlayService, snap, target)
                }
                sprite = loaded
                view?.setSprite(loaded)
                updateWindowSize()
                lastNotificationKey = null
                updateNotification()
            }
        } else {
            updateNotification()
        }
    }

    // ---------------------------------------------------------------- notification

    private fun startInForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(COMPANION_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    COMPANION_CHANNEL_ID,
                    getString(R.string.channel_companion),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.channel_companion_desc)
                    setShowBadge(false)
                },
            )
        }
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

    private fun companionName(c: BatteryBarConfig): String? =
        if (c.useCustomSprite) getString(R.string.your_creation) else Pokedex.find(this, c.pokemonId)?.name

    private fun updateNotification() {
        if (mode == BarMode.NOTIFICATION) {
            postBarNotification()
        } else {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            runCatching { nm.notify(NOTIFICATION_ID, buildNotification(companionName(config))) }
        }
    }

    // ---------------------------------------------------------------- notification panel bar

    /** Look used inside the notification: a thicker bar, a bigger still sprite, no percent text. */
    private fun snapshotConfig(c: BatteryBarConfig) = c.copy(
        position = BarPosition.STATUS_BAR_BOTTOM,
        barThicknessDp = c.barThicknessDp.coerceAtLeast(6),
        spriteSizeDp = 44,
        showPercent = false,
        animated = false,
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun snapshotHeight() = dp(60)

    private fun snapshotWidth() = (resources.displayMetrics.widthPixels - dp(64)).coerceIn(dp(200), dp(400))

    /** Re-posts the card only when something visible changed, so it costs nothing while idle. */
    private fun postBarNotification() {
        if (!configLoaded || mode != BarMode.NOTIFICATION || level < 0) return
        val key = "$level:$charging:$spriteKey:${config.copy(enabled = true).hashCode()}"
        if (key == lastNotificationKey) return
        lastNotificationKey = key
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(NOTIFICATION_ID, buildBarNotification()) }
    }

    private fun renderBar(): Bitmap {
        val v = snapshotView ?: BatteryBarView(this).also { snapshotView = it }
        v.applyConfig(snapshotConfig(config))
        if (v.tag !== sprite) {
            v.tag = sprite
            v.setSprite(sprite)
        }
        v.setBattery(level.coerceAtLeast(0), charging)
        val full = v.renderSnapshot(snapshotWidth(), snapshotHeight())
        // Keep the notification parcel small: big bitmaps can exceed the binder limit.
        if (full.width <= MAX_NOTIFICATION_BITMAP_WIDTH) return full
        val h = full.height * MAX_NOTIFICATION_BITMAP_WIDTH / full.width
        return Bitmap.createScaledBitmap(full, MAX_NOTIFICATION_BITMAP_WIDTH, h, true).also { full.recycle() }
    }

    private fun barTexts(): Pair<String, String> {
        val name = companionName(config) ?: getString(R.string.app_name)
        val title = getString(R.string.notif_bar_title, name, level.coerceAtLeast(0))
        val subtitle = when {
            charging -> {
                val remaining = if (Build.VERSION.SDK_INT >= 28) {
                    (getSystemService(BATTERY_SERVICE) as BatteryManager).computeChargeTimeRemaining()
                } else {
                    -1L
                }
                BarModes.formatChargeTime(remaining)?.let { getString(R.string.notif_charging_eta, it) }
                    ?: getString(R.string.notif_charging)
            }
            level <= config.lowThreshold -> getString(R.string.notif_low)
            else -> getString(R.string.notif_on_battery)
        }
        return title to subtitle
    }

    private fun buildBarNotification(): Notification {
        val bar = renderBar()
        val small = Bitmap.createScaledBitmap(bar, bar.width / 2, bar.height / 2, true)
        val (title, subtitle) = barTexts()
        val collapsed = RemoteViews(packageName, R.layout.notification_bar_small).apply {
            setImageViewBitmap(R.id.bar_image, small)
            setTextViewText(R.id.bar_title, title)
        }
        val expanded = RemoteViews(packageName, R.layout.notification_bar_big).apply {
            setImageViewBitmap(R.id.bar_image, bar)
            setTextViewText(R.id.bar_title, title)
            setTextViewText(R.id.bar_subtitle, subtitle)
        }
        return NotificationCompat.Builder(this, COMPANION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_melta)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setContentIntent(openAppIntent())
            .addAction(0, getString(R.string.hide_bar), stopIntent())
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this, 1, Intent(this, BatteryOverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildNotification(companion: String?): Notification {
        val open = openAppIntent()
        val stop = stopIntent()
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
        private const val COMPANION_CHANNEL_ID = "battery_companion"
        private const val MAX_NOTIFICATION_BITMAP_WIDTH = 600
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "com.melta.app.action.STOP_BATTERY_BAR"

        fun start(context: Context) {
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
