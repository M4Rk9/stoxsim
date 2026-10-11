package com.stoxsim.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Source of truth: frontend/app/globals.css and frontend/app/theme.css.
private val Light = lightColorScheme(
    primary = Color(0xFF0B8F55), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F7EE), onPrimaryContainer = Color(0xFF087143),
    secondary = Color(0xFF6D7C75), background = Color(0xFFF4F6F1),
    surface = Color(0xFFFFFFFF), surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAF7), surfaceVariant = Color(0xFFEEF2ED),
    onSurface = Color(0xFF17211D), onBackground = Color(0xFF17211D), onSurfaceVariant = Color(0xFF6D7C75),
    outline = Color(0xFF6D7C75), outlineVariant = Color(0xFFDFE5DE),
    error = Color(0xFFC33C3C), errorContainer = Color(0xFFFFF0EF), onErrorContainer = Color(0xFFC33C3C)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF00D09C), onPrimary = Color(0xFF06150D),
    primaryContainer = Color(0xFF17332B), onPrimaryContainer = Color(0xFF00D09C),
    secondary = Color(0xFFA3A3A3), background = Color(0xFF121212),
    surface = Color(0xFF1B1B1B), surfaceContainer = Color(0xFF1B1B1B),
    surfaceContainerLow = Color(0xFF181818), surfaceVariant = Color(0xFF222222),
    onSurface = Color(0xFFF1F1F1), onBackground = Color(0xFFF1F1F1), onSurfaceVariant = Color(0xFFA3A3A3),
    outline = Color(0xFFA3A3A3), outlineVariant = Color(0xFF303030),
    error = Color(0xFFEF6464), errorContainer = Color(0xFF331F1F), onErrorContainer = Color(0xFFFFAAAA)
)
private val Type = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.7).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 1.sp)
)
@Composable internal fun StoxSimTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(22.dp)), content = content)
}
