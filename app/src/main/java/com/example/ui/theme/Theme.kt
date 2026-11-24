package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

fun buildSkolquikColorScheme(
    darkTheme: Boolean,
    highlightHue: HighlightHue = HighlightHue.SKOLQUIK_BLUE
): ColorScheme {
    return if (darkTheme) {
        darkColorScheme(
            primary = highlightHue.darkPrimary,
            onPrimary = Color(0xFF09121E),
            primaryContainer = highlightHue.darkContainer,
            onPrimaryContainer = highlightHue.darkOnContainer,
            secondary = highlightHue.darkSecondary,
            onSecondary = Color(0xFF09121E),
            secondaryContainer = DarkSubtleContainer,
            onSecondaryContainer = DarkOnSurface,
            tertiary = SkolquikAmberCheck,
            onTertiary = Color(0xFF1B1000),
            tertiaryContainer = DarkElevatedContainer,
            onTertiaryContainer = DarkOnSurface,
            background = DarkObsidianBackground,
            onBackground = DarkOnSurface,
            surface = DarkObsidianSurface,
            onSurface = DarkOnSurface,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkMuted,
            surfaceContainerLow = DarkCardContainer,
            surfaceContainer = DarkSubtleContainer,
            surfaceContainerHigh = DarkElevatedContainer,
            outline = DarkHairlineOutline,
            outlineVariant = DarkHairlineOutline.copy(alpha = 0.7f)
        )
    } else {
        lightColorScheme(
            primary = highlightHue.lightPrimary,
            onPrimary = Color.White,
            primaryContainer = highlightHue.lightContainer,
            onPrimaryContainer = highlightHue.lightOnContainer,
            secondary = highlightHue.lightSecondary,
            onSecondary = Color.White,
            secondaryContainer = NeutralSubtleContainer,
            onSecondaryContainer = NeutralInk,
            tertiary = SkolquikAmberCheck,
            onTertiary = Color.White,
            tertiaryContainer = NeutralElevatedContainer,
            onTertiaryContainer = NeutralInk,
            background = PureWhiteBackground,
            onBackground = NeutralInk,
            surface = PureWhiteSurface,
            onSurface = NeutralInk,
            surfaceVariant = NeutralSurfaceVariant,
            onSurfaceVariant = NeutralMuted,
            surfaceContainerLow = NeutralPorcelainCard,
            surfaceContainer = NeutralSubtleContainer,
            surfaceContainerHigh = NeutralElevatedContainer,
            outline = NeutralHairlineOutline,
            outlineVariant = NeutralHairlineOutline.copy(alpha = 0.8f)
        )
    }
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun resolveIsDarkTheme(
    appearanceMode: ThemeAppearanceMode,
    latitude: Double = 6.4474,
    longitude: Double = 3.4737
): Boolean = when (appearanceMode) {
    ThemeAppearanceMode.WHITE_DAY -> false
    ThemeAppearanceMode.NIGHT_DARK -> true
    ThemeAppearanceMode.SYSTEM -> isSystemInDarkTheme()
    ThemeAppearanceMode.SUNRISE_SUNSET -> SolarThemeCalculator.calculateSchedule(latitude, longitude).isCurrentlyNight
}

@Composable
fun SchoolCompassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highlightHue: HighlightHue = HighlightHue.SKOLQUIK_BLUE,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> buildSkolquikColorScheme(darkTheme = darkTheme, highlightHue = highlightHue)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
