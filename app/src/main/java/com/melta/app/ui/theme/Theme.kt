package com.melta.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val MeltaYellow = Color(0xFFFFC93C)
val MeltaOrange = Color(0xFFFF7A45)
val MeltaViolet = Color(0xFF5B2A86)
val MeltaNight = Color(0xFF1A1030)

private val LightColors = lightColorScheme(
    primary = MeltaViolet,
    onPrimary = Color.White,
    secondary = MeltaOrange,
    tertiary = MeltaYellow,
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD7B8FF),
    onPrimary = Color(0xFF3B0F66),
    secondary = MeltaOrange,
    tertiary = MeltaYellow,
    background = MeltaNight,
    surface = MeltaNight,
)

@Composable
fun MeltaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
