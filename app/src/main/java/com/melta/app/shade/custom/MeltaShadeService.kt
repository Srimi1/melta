package com.melta.app.shade.custom

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.melta.app.core.prefs.BatteryBarConfig
import com.melta.app.core.prefs.SettingsStore
import com.melta.app.core.prefs.TriggerZone
import com.melta.app.ui.theme.MeltaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Melta Shade. An accessibility service is the only way Android lets an app catch a swipe on
 * the top edge and draw above everything. It never reads screen content: it listens to no
 * accessibility events and only adds two windows of its own, a thin trigger strip and the panel.
 */
class MeltaShadeService : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var config = BatteryBarConfig()
    private var strip: View? = null
    private var panel: View? = null
    private var panelVisible by mutableStateOf(false)

    // A small lifecycle so Compose can run inside a service window.
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null as Bundle?)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Melta Shade service connected")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        scope.launch {
            SettingsStore.get(this@MeltaShadeService).batteryBar.collect { c ->
                val zoneChanged = c.triggerZone != config.triggerZone
                config = c
                if (!c.customShade) removeStrip() else if (strip == null || zoneChanged) addStrip()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    private fun teardown() {
        if (instance === this) instance = null
        scope.cancel()
        removeStrip()
        removePanel()
    }

    // ---------------------------------------------------------------- trigger strip

    private fun statusBarHeight(): Int {
        @SuppressLint("DiscouragedApi", "InternalInsetResource")
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else (24 * resources.displayMetrics.density).toInt()
    }

    private fun baseParams(width: Int, height: Int, flags: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addStrip() {
        removeStrip()
        val screenWidth = resources.displayMetrics.widthPixels
        val width = if (config.triggerZone == TriggerZone.FULL) WindowManager.LayoutParams.MATCH_PARENT else screenWidth / 2
        val lp = baseParams(width, statusBarHeight(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE).apply {
            gravity = Gravity.TOP or if (config.triggerZone == TriggerZone.RIGHT) Gravity.END else Gravity.START
            title = "Melta Shade trigger"
        }
        val v = View(this)
        var downY = 0f
        var tracker: VelocityTracker? = null
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = e.rawY
                    tracker = VelocityTracker.obtain().also { it.addMovement(e) }
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.addMovement(e)
                    tracker?.computeCurrentVelocity(1000)
                    val vy = tracker?.yVelocity ?: 0f
                    if (ShadeLogic.shouldOpen(e.rawY - downY, vy, resources.displayMetrics.density)) {
                        tracker?.recycle()
                        tracker = null
                        openPanel()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.recycle()
                    tracker = null
                }
            }
            true
        }
        runCatching { windowManager.addView(v, lp) }
            .onSuccess { strip = v }
            .onFailure { Log.e(TAG, "Could not add the trigger strip", it) }
    }

    private fun removeStrip() {
        strip?.let { runCatching { windowManager.removeView(it) } }
        strip = null
    }

    // ---------------------------------------------------------------- panel

    /** Opens Melta Shade. Also used by the "Test it" button and the screenshot automation. */
    fun openPanel() {
        if (panel != null) return
        val root = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    closePanel()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
        val compose = ComposeView(this).apply {
            setContent {
                MeltaTheme {
                    ShadePanel(
                        visible = panelVisible,
                        config = config,
                        onClose = ::closePanel,
                        onAction = ::runAction,
                    )
                }
            }
        }
        root.addView(compose)

        var flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        if (Build.VERSION.SDK_INT >= 31) flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        val lp = baseParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, flags).apply {
            gravity = Gravity.TOP
            title = "Melta Shade"
            if (Build.VERSION.SDK_INT >= 31) blurBehindRadius = (24 * resources.displayMetrics.density).toInt()
        }
        runCatching { windowManager.addView(root, lp) }
            .onSuccess {
                panel = root
                panelVisible = true
                Log.i(TAG, "Melta Shade opened")
            }
            .onFailure { Log.e(TAG, "Could not open Melta Shade", it) }
    }

    fun closePanel() {
        if (panel == null || !panelVisible) return
        panelVisible = false
        scope.launch {
            delay(ShadePanelAnimMs.toLong())
            removePanel()
        }
    }

    private fun removePanel() {
        panel?.let { runCatching { windowManager.removeView(it) } }
        panel = null
        panelVisible = false
    }

    // ---------------------------------------------------------------- quick actions

    private fun runAction(action: ShadeAction) {
        when (action) {
            is ShadeAction.Launch -> {
                closePanel()
                runCatching { startActivity(action.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            is ShadeAction.Global -> {
                closePanel()
                // Let the panel slide away first, so it is not in the screenshot.
                scope.launch {
                    delay(ShadePanelAnimMs.toLong() + 150)
                    performGlobalAction(action.id)
                }
            }
            ShadeAction.Close -> closePanel()
        }
    }

    companion object {
        private const val TAG = "MeltaShade"

        @Volatile
        var instance: MeltaShadeService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val me = "${context.packageName}/${MeltaShadeService::class.java.name}"
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}

/** Things the panel asks the service to do. */
sealed interface ShadeAction {
    data class Launch(val intent: Intent) : ShadeAction
    data class Global(val id: Int) : ShadeAction
    data object Close : ShadeAction
}
