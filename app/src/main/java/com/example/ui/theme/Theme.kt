package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = DeepPurplePrimary,
    onPrimary = DeepPurpleOnPrimary,
    primaryContainer = LightPurplePrimaryContainer,
    onPrimaryContainer = DeepPurpleOnPrimaryContainer,
    secondary = PlumSecondary,
    onSecondary = PlumOnSecondary,
    secondaryContainer = LightPlumContainer,
    onSecondaryContainer = PlumOnContainer,
    tertiary = OrchidTertiary,
    onTertiary = OrchidOnTertiary,
    tertiaryContainer = LightOrchidContainer,
    onTertiaryContainer = OrchidOnContainer,
    background = LightPurpleBackground,
    onBackground = InkOnPurpleSurface,
    surface = LightPurpleSurface,
    onSurface = InkOnPurpleSurface,
    surfaceVariant = LightPurpleSurfaceVariant,
    onSurfaceVariant = InkPurpleMuted,
    surfaceContainerLow = LightPurpleSurfaceContainerLow,
    surfaceContainer = LightPurpleSurfaceContainer,
    surfaceContainerHigh = LightPurpleSurfaceContainerHigh,
    outline = LavenderOutline,
    outlineVariant = LavenderOutline.copy(alpha = 0.6f)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkLightPurplePrimary,
    onPrimary = DarkOnPurplePrimary,
    primaryContainer = DarkDeepPurpleContainer,
    onPrimaryContainer = DarkOnPurpleContainer,
    secondary = DarkLightPlum,
    onSecondary = DarkOnLightPlum,
    secondaryContainer = DarkPlumContainer,
    onSecondaryContainer = DarkOnPlumContainer,
    tertiary = DarkLightOrchid,
    onTertiary = DarkOnOrchid,
    tertiaryContainer = DarkOrchidContainer,
    onTertiaryContainer = DarkOnOrchidContainer,
    background = DarkPurpleBackground,
    onBackground = DarkOnPurpleSurface,
    surface = DarkPurpleSurface,
    onSurface = DarkOnPurpleSurface,
    surfaceVariant = DarkPurpleSurfaceVariant,
    onSurfaceVariant = DarkPurpleMuted,
    surfaceContainerLow = DarkPurpleSurfaceContainerLow,
    surfaceContainer = DarkPurpleSurfaceContainer,
    surfaceContainerHigh = DarkPurpleSurfaceContainerHigh,
    outline = DarkPurpleOutline,
    outlineVariant = DarkPurpleOutline.copy(alpha = 0.6f)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SchoolCompassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
