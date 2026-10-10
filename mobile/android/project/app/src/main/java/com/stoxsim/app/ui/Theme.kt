package com.stoxsim.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF087849), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F1E2), onPrimaryContainer = Color(0xFF073D27),
    secondary = Color(0xFF425D50), background = Color(0xFFF4F6F1),
    surface = Color(0xFFF4F6F1), surfaceContainer = Color(0xFFFFFFFF),
    onSurface = Color(0xFF162B22), onBackground = Color(0xFF162B22)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF7CDAA7), onPrimary = Color(0xFF073D27),
    primaryContainer = Color(0xFF125436), onPrimaryContainer = Color(0xFFD8F1E2),
    secondary = Color(0xFFB8CDBF), background = Color(0xFF101C17),
    surface = Color(0xFF101C17), surfaceContainer = Color(0xFF1A2922),
    onSurface = Color(0xFFE0E9E0), onBackground = Color(0xFFE0E9E0)
)

@Composable internal fun StoxSimTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
