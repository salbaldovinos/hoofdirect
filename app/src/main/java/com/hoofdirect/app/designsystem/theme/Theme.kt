package com.hoofdirect.app.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Branded colors (fallback for Android 11 and below)
private val HdLightColorScheme = lightColorScheme(
    primary = HdGreen,
    onPrimary = HdOnGreen,
    primaryContainer = HdGreenContainer,
    onPrimaryContainer = HdOnGreenContainer,
    secondary = HdBrown,
    onSecondary = HdOnBrown,
    secondaryContainer = HdBrownContainer,
    onSecondaryContainer = HdOnBrownContainer,
    tertiary = HdBlue,
    onTertiary = HdOnBlue,
    tertiaryContainer = HdBlueContainer,
    onTertiaryContainer = HdOnBlueContainer,
    error = HdError,
    onError = HdOnError,
    errorContainer = HdErrorContainer,
    onErrorContainer = HdOnErrorContainer,
    background = HdBackground,
    onBackground = HdOnBackground,
    surface = HdSurface,
    onSurface = HdOnSurface,
    surfaceVariant = HdSurfaceVariant,
    onSurfaceVariant = HdOnSurfaceVariant,
    outline = HdOutline,
    outlineVariant = HdOutlineVariant,
)

private val HdDarkColorScheme = darkColorScheme(
    primary = HdGreenDark,
    onPrimary = HdOnGreenDark,
    primaryContainer = HdGreenContainerDark,
    onPrimaryContainer = HdOnGreenContainerDark,
    secondary = HdBrownDark,
    onSecondary = HdOnBrownDark,
    secondaryContainer = HdBrownContainerDark,
    onSecondaryContainer = HdOnBrownContainerDark,
    tertiary = HdBlueDark,
    onTertiary = HdOnBlueDark,
    tertiaryContainer = HdBlueContainerDark,
    onTertiaryContainer = HdOnBlueContainerDark,
    error = HdErrorDark,
    onError = HdOnErrorDark,
    errorContainer = HdErrorContainerDark,
    onErrorContainer = HdOnErrorContainerDark,
    background = HdBackgroundDark,
    onBackground = HdOnBackgroundDark,
    surface = HdSurfaceDark,
    onSurface = HdOnSurfaceDark,
    surfaceVariant = HdSurfaceVariantDark,
    onSurfaceVariant = HdOnSurfaceVariantDark,
    outline = HdOutlineDark,
    outlineVariant = HdOutlineVariantDark,
)

@Composable
fun HoofDirectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Dynamic color available on Android 12+
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> HdDarkColorScheme
        else -> HdLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HdTypography,
        content = content
    )
}
