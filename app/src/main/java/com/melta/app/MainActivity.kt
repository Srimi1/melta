package com.melta.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.melta.app.battery.service.BatteryOverlayService
import com.melta.app.battery.ui.BatteryScreen
import com.melta.app.battery.ui.PatternEditorScreen
import com.melta.app.battery.ui.PokemonPickerScreen
import com.melta.app.core.prefs.SettingsStore
import com.melta.app.home.HomeScreen
import com.melta.app.ui.theme.MeltaTheme
import kotlinx.coroutines.launch

/** Screens in the app. Kept as a tiny enum instead of a navigation library. */
enum class Screen { HOME, BATTERY, PICKER, EDITOR }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val start = startStack(intent)
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEMO, false)) enableBarForDemo()
        setContent {
            MeltaTheme {
                MeltaNavHost(start)
            }
        }
    }

    /**
     * Debug builds can open straight on a screen, e.g. `--es melta.screen picker`.
     * Used by the screenshots workflow so it never has to tap by coordinates.
     */
    private fun startStack(intent: Intent?): List<Screen> {
        if (!BuildConfig.DEBUG) return listOf(Screen.HOME)
        return when (intent?.getStringExtra(EXTRA_SCREEN)) {
            "battery" -> listOf(Screen.HOME, Screen.BATTERY)
            "picker" -> listOf(Screen.HOME, Screen.BATTERY, Screen.PICKER)
            "editor" -> listOf(Screen.HOME, Screen.BATTERY, Screen.EDITOR)
            else -> listOf(Screen.HOME)
        }
    }

    /** Debug only: switch the bar on without tapping, for automated screenshots. */
    private fun enableBarForDemo() {
        lifecycleScope.launch {
            SettingsStore.get(this@MainActivity).updateBatteryBar { it.copy(enabled = true) }
            BatteryOverlayService.start(this@MainActivity)
        }
    }

    private companion object {
        const val EXTRA_SCREEN = "melta.screen"
        const val EXTRA_DEMO = "melta.demo"
    }
}

@Composable
private fun MeltaNavHost(start: List<Screen>) {
    var stack by rememberSaveable { mutableStateOf(start) }
    val current = stack.last()
    val go: (Screen) -> Unit = { stack = stack + it }
    val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }

    BackHandler(enabled = stack.size > 1, onBack = back)

    when (current) {
        Screen.HOME -> HomeScreen(onOpenBattery = { go(Screen.BATTERY) })
        Screen.BATTERY -> BatteryScreen(
            onBack = back,
            onPickCompanion = { go(Screen.PICKER) },
            onCreatePattern = { go(Screen.EDITOR) },
        )
        Screen.PICKER -> PokemonPickerScreen(onBack = back, onCreate = { go(Screen.EDITOR) })
        Screen.EDITOR -> PatternEditorScreen(onBack = back)
    }
}
