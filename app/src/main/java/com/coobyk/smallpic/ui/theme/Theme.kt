package com.coobyk.smallpic.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF732EE4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF25005A),
    inversePrimary = Color(0xFFD2BBFF),
    secondary = Color(0xFF635B70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9DEF8),
    onSecondaryContainer = Color(0xFF1E182B),
    tertiary = Color(0xFF7E525E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E1),
    onTertiaryContainer = Color(0xFF31101B),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1D1B1E),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1D1B1E),
    surfaceVariant = Color(0xFFE7E0EB),
    onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCBC4CF),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF323033),
    inverseOnSurface = Color(0xFFF5EFF4),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceTint = Color(0xFF732EE4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F2F7),
    surfaceContainer = Color(0xFFF2ECF1),
    surfaceContainerHigh = Color(0xFFECE6EB),
    surfaceContainerHighest = Color(0xFFE6E1E6),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFD2BBFF),
    onPrimary = Color(0xFF3F008E),
    primaryContainer = Color(0xFF5A00C6),
    onPrimaryContainer = Color(0xFFEADDFF),
    inversePrimary = Color(0xFF732EE4),
    secondary = Color(0xFFCDC2DB),
    onSecondary = Color(0xFF342D40),
    secondaryContainer = Color(0xFF4B4358),
    onSecondaryContainer = Color(0xFFE9DEF8),
    tertiary = Color(0xFFF0B7C5),
    onTertiary = Color(0xFF4A2530),
    tertiaryContainer = Color(0xFF643B46),
    onTertiaryContainer = Color(0xFFFFD9E1),
    background = Color(0xFF1D1B1E),
    onBackground = Color(0xFFE6E1E6),
    surface = Color(0xFF1D1B1E),
    onSurface = Color(0xFFE6E1E6),
    surfaceVariant = Color(0xFF49454E),
    onSurfaceVariant = Color(0xFFCBC4CF),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454E),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE6E1E6),
    inverseOnSurface = Color(0xFF323033),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFB4AB),
    surfaceTint = Color(0xFFD2BBFF),
    surfaceContainerLowest = Color(0xFF0F0E11),
    surfaceContainerLow = Color(0xFF1D1B1E),
    surfaceContainer = Color(0xFF211F22),
    surfaceContainerHigh = Color(0xFF2B292D),
    surfaceContainerHighest = Color(0xFF363438),
)

/** Expressive shape tokens: rounder across the board. */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val SmallPicTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

/**
 * Material 3 theme with expressive motion and shapes. Follows the system
 * dark-mode setting, and on Android 12+ prefers the wallpaper-derived
 * dynamic color scheme.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SmallPicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        shapes = ExpressiveShapes,
        typography = SmallPicTypography,
        content = content,
    )
}
