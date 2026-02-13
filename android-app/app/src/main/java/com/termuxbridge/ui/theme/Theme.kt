package com.termuxbridge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF1F4E79),
    secondary = Color(0xFF2E75B6),
    tertiary = Color(0xFF4A90D9)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1F4E79),
    secondary = Color(0xFF2E75B6),
    tertiary = Color(0xFF4A90D9)
)

@Composable
fun TermuxBridgeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
