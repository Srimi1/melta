package com.melta.app

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
import com.melta.app.battery.ui.BatteryScreen
import com.melta.app.battery.ui.PatternEditorScreen
import com.melta.app.battery.ui.PokemonPickerScreen
import com.melta.app.home.HomeScreen
import com.melta.app.ui.theme.MeltaTheme

/** Screens in the app. Kept as a tiny enum instead of a navigation library. */
enum class Screen { HOME, BATTERY, PICKER, EDITOR }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeltaTheme {
                MeltaNavHost()
            }
        }
    }
}

@Composable
private fun MeltaNavHost() {
    var stack by rememberSaveable { mutableStateOf(listOf(Screen.HOME)) }
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
