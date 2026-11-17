package com.example.ui.theme

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

private val DpsLightColors = lightColorScheme(
    primary = NavyDeep,
    onPrimary = GoldAmber,
    primaryContainer = NavyRoyal,
    onPrimaryContainer = Color.White,
    secondary = GoldAmber,
    onSecondary = NavyDeep,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = NavyDeep,
    tertiary = EmeraldSuccess,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldSuccess,
    background = SurfaceLight,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    error = RoseError,
    errorContainer = RoseContainer,
)

private val DpsDarkColors = darkColorScheme(
    primary = GoldAmber,
    onPrimary = NavyDeep,
    primaryContainer = NavyRoyal,
    onPrimaryContainer = Color.White,
    secondary = GoldAmberDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF451A03),
    onSecondaryContainer = GoldSoft,
    tertiary = EmeraldSuccess,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = EmeraldContainer,
    background = NavySurfaceDark,
    onBackground = Color(0xFFF8FAFC),
    surface = NavyCardDark,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF162033),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF334155),
    error = Color(0xFFFB7185),
    errorContainer = Color(0xFF4C0519),
)

@Composable
fun DpsEtahTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DpsDarkColors
        else -> DpsLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DpsTypography,
        content = content,
    )
}
