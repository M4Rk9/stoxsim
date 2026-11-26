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
    primary = Color(0xFF087649), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F5EC), onPrimaryContainer = Color(0xFF065D39),
    secondary = Color(0xFF52685D), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5F5EC), onSecondaryContainer = Color(0xFF065D39),
    background = Color(0xFFF4F7F5), surfaceTint = Color(0xFF087649),
    surface = Color(0xFFFFFFFF), surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4F7F5),
    surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFEDF3EF), surfaceVariant = Color(0xFFEDF3EF),
    onSurface = Color(0xFF14251E), onBackground = Color(0xFF14251E), onSurfaceVariant = Color(0xFF52685D),
    outline = Color(0xFF52685D), outlineVariant = Color(0xFFDCE6DF),
    error = Color(0xFFC33C3C), errorContainer = Color(0xFFFFF0EF), onErrorContainer = Color(0xFFC33C3C)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF10E886), onPrimary = Color(0xFF07110E),
    primaryContainer = Color(0xFF123E2A), onPrimaryContainer = Color(0xFF10E886),
    secondary = Color(0xFFA4B8AF), onSecondary = Color(0xFF050B09),
    secondaryContainer = Color(0xFF123E2A), onSecondaryContainer = Color(0xFF10E886),
    background = Color(0xFF050B09), surfaceTint = Color(0xFF10E886),
    surface = Color(0xFF10241B), surfaceContainer = Color(0xFF10241B),
    surfaceContainerLowest = Color(0xFF050B09), surfaceContainerLow = Color(0xFF0A1511),
    surfaceContainerHigh = Color(0xFF142A20), surfaceContainerHighest = Color(0xFF203A30), surfaceVariant = Color(0xFF142A20),
    onSurface = Color(0xFFF2F7F5), onBackground = Color(0xFFF2F7F5), onSurfaceVariant = Color(0xFFA4B8AF),
    outline = Color(0xFFA4B8AF), outlineVariant = Color(0xFF2C473B),
    error = Color(0xFFFF758B), errorContainer = Color(0xFF331F1F), onErrorContainer = Color(0xFFFFAAAA)
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
        shapes = Shapes(small = RoundedCornerShape(9.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp)), content = content)
}
