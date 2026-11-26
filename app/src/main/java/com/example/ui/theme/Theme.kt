package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val TripBuddyLightColorScheme = lightColorScheme(
    primary = OceanBlue,
    onPrimary = PureWhite,
    primaryContainer = SkyMist,
    onPrimaryContainer = OceanInk,
    secondary = SeaTeal,
    onSecondary = PureWhite,
    secondaryContainer = SeaTealContainer,
    onSecondaryContainer = OceanInk,
    tertiary = WarmAmber,
    onTertiary = PureWhite,
    tertiaryContainer = WarmAmberContainer,
    onTertiaryContainer = OceanInk,
    background = CloudBackground,
    onBackground = OceanInk,
    surface = PureWhite,
    onSurface = OceanInk,
    surfaceVariant = SkyTint,
    onSurfaceVariant = MutedSlate,
    outline = SoftBorder,
    outlineVariant = SoftBorder,
    error = SoftCoral,
    onError = PureWhite,
    errorContainer = SoftCoralContainer,
    onErrorContainer = SoftCoral
)

private val TripBuddyDarkColorScheme = darkColorScheme(
    primary = DarkOceanPrimary,
    onPrimary = DarkOceanBackground,
    primaryContainer = DarkOceanPrimaryContainer,
    onPrimaryContainer = DarkOceanText,
    secondary = SeaTeal,
    onSecondary = PureWhite,
    secondaryContainer = ColorTokens.DarkTealContainer,
    onSecondaryContainer = DarkOceanText,
    tertiary = WarmAmber,
    onTertiary = DarkOceanBackground,
    tertiaryContainer = ColorTokens.DarkAmberContainer,
    onTertiaryContainer = DarkOceanText,
    background = DarkOceanBackground,
    onBackground = DarkOceanText,
    surface = DarkOceanSurface,
    onSurface = DarkOceanText,
    surfaceVariant = DarkOceanElevated,
    onSurfaceVariant = DarkOceanMuted,
    outline = DarkSoftBorder,
    outlineVariant = DarkSoftBorder,
    error = SoftCoral,
    onError = PureWhite
)

private object ColorTokens {
    val DarkTealContainer = androidx.compose.ui.graphics.Color(0xFF114D4A)
    val DarkAmberContainer = androidx.compose.ui.graphics.Color(0xFF4D3208)
}

val TripBuddyShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun TripBuddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TripBuddyDarkColorScheme else TripBuddyLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TripBuddyTypography,
        shapes = TripBuddyShapes,
        content = content
    )
}
