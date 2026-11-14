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

// Shared web brand colours, with neutral native surfaces and a restrained type scale.
private val Light = lightColorScheme(
    primary = Color(0xFF087A4B), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F7EE), onPrimaryContainer = Color(0xFF087143),
    secondary = Color(0xFF596A62), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F7EE), onSecondaryContainer = Color(0xFF087143),
    background = Color(0xFFF7F8FA), surfaceTint = Color(0xFF087A4B),
    surface = Color(0xFFFFFFFF), surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFF0F2F5), surfaceVariant = Color(0xFFF0F2F5),
    onSurface = Color(0xFF17211D), onBackground = Color(0xFF17211D), onSurfaceVariant = Color(0xFF596A62),
    outline = Color(0xFF596A62), outlineVariant = Color(0xFFDFE5DE),
    error = Color(0xFFC33C3C), errorContainer = Color(0xFFFFF0EF), onErrorContainer = Color(0xFFC33C3C)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF5BD6A2), onPrimary = Color(0xFF0C2418),
    primaryContainer = Color(0xFF17332B), onPrimaryContainer = Color(0xFF5BD6A2),
    secondary = Color(0xFFA9B8AE), onSecondary = Color(0xFF101513),
    secondaryContainer = Color(0xFF17332B), onSecondaryContainer = Color(0xFF5BD6A2),
    background = Color(0xFF101513), surfaceTint = Color(0xFF5BD6A2),
    surface = Color(0xFF18201C), surfaceContainer = Color(0xFF18201C),
    surfaceContainerLowest = Color(0xFF101513), surfaceContainerLow = Color(0xFF181818),
    surfaceContainerHigh = Color(0xFF222222), surfaceContainerHighest = Color(0xFF262626), surfaceVariant = Color(0xFF222222),
    onSurface = Color(0xFFEEF3EF), onBackground = Color(0xFFEEF3EF), onSurfaceVariant = Color(0xFFA9B8AE),
    outline = Color(0xFFA9B8AE), outlineVariant = Color(0xFF303E36),
    error = Color(0xFFEF6464), errorContainer = Color(0xFF331F1F), onErrorContainer = Color(0xFFFFAAAA)
)
private val Type = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.7).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 1.sp)
)
@Composable internal fun StoxSimTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(28.dp)), content = content)
}
