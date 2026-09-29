package com.imagepreptool.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Material のカラースキームに無いアプリ固有の色 */
@Immutable
data class ExtendedColors(
    val canvas: Color,
    val canvasContent: Color,
    val success: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3657D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3FF),
    onPrimaryContainer = Color(0xFF0E2A8C),
    secondary = Color(0xFF585E71),
    secondaryContainer = Color(0xFFE2E5EE),
    onSecondaryContainer = Color(0xFF1D2130),
    background = Color(0xFFF4F5F8),
    onBackground = Color(0xFF1A1C21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C21),
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF5B6070),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9FB),
    surfaceContainer = Color(0xFFF1F2F6),
    surfaceContainerHigh = Color(0xFFEAECF1),
    surfaceContainerHighest = Color(0xFFE3E5EB),
    outline = Color(0xFFC4C8D2),
    outlineVariant = Color(0xFFE1E3E9),
    error = Color(0xFFC62B2B),
    errorContainer = Color(0xFFFDE3E1),
    onErrorContainer = Color(0xFF7A1111),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA9B9FF),
    onPrimary = Color(0xFF0B2375),
    primaryContainer = Color(0xFF26409E),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFFC1C6D8),
    secondaryContainer = Color(0xFF363B4A),
    onSecondaryContainer = Color(0xFFDFE2EE),
    background = Color(0xFF111316),
    onBackground = Color(0xFFE3E4E9),
    surface = Color(0xFF181A1E),
    onSurface = Color(0xFFE3E4E9),
    surfaceVariant = Color(0xFF24272D),
    onSurfaceVariant = Color(0xFFA5AAB8),
    surfaceContainerLowest = Color(0xFF0E1013),
    surfaceContainerLow = Color(0xFF16181C),
    surfaceContainer = Color(0xFF1C1F24),
    surfaceContainerHigh = Color(0xFF24272D),
    surfaceContainerHighest = Color(0xFF2E3138),
    outline = Color(0xFF4A4F5B),
    outlineVariant = Color(0xFF2E3138),
    error = Color(0xFFFFB3AC),
    errorContainer = Color(0xFF5C1512),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightExtended = ExtendedColors(
    canvas = Color(0xFFE6E8EE),
    canvasContent = Color(0xFF5B6070),
    success = Color(0xFF1F8A4C),
    warning = Color(0xFFB26A00),
    warningContainer = Color(0xFFFFF1D6),
    onWarningContainer = Color(0xFF5E3900),
)

private val DarkExtended = ExtendedColors(
    canvas = Color(0xFF0B0C0E),
    canvasContent = Color(0xFF8B91A0),
    success = Color(0xFF6FD69B),
    warning = Color(0xFFFFC266),
    warningContainer = Color(0xFF3D2A07),
    onWarningContainer = Color(0xFFFFDDA6),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 14.sp, lineHeight = 21.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
        labelLarge = base.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.2.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors: ColorScheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}

object AppTheme {
    /** 適用中のカラースキームに合わせた拡張色 */
    val extended: ExtendedColors
        @Composable get() = if (MaterialTheme.colorScheme.background == DarkScheme.background) DarkExtended else LightExtended
}

val MonoNumberStyle = TextStyle(fontFeatureSettings = "tnum")
