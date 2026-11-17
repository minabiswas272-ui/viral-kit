package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = ViralCoral,
    onPrimary = Color.White,
    primaryContainer = ViralCoralDarkContainer,
    onPrimaryContainer = Color(0xFFFFD9DE),
    secondary = GrowthMint,
    onSecondary = Color(0xFF002117),
    secondaryContainer = GrowthMintDarkContainer,
    onSecondaryContainer = Color(0xFF9BF7DC),
    tertiary = ScoreGold,
    onTertiary = Color(0xFF281C00),
    tertiaryContainer = ScoreGoldDarkContainer,
    onTertiaryContainer = Color(0xFFFFE59E),
    background = StudioObsidian,
    onBackground = StudioTextPrimaryDark,
    surface = StudioSurfaceDark,
    onSurface = StudioTextPrimaryDark,
    surfaceVariant = StudioCardDark,
    onSurfaceVariant = StudioTextMutedDark,
    surfaceContainerLow = StudioSurfaceDark,
    surfaceContainer = StudioCardDark,
    surfaceContainerHigh = StudioCardElevatedDark,
    outline = StudioOutlineDark,
    error = StudioErrorRed,
    onError = Color.White
)

private val StudioLightColorScheme = lightColorScheme(
    primary = Color(0xFFE02446),
    onPrimary = Color.White,
    primaryContainer = ViralCoralLightContainer,
    onPrimaryContainer = Color(0xFF41000F),
    secondary = Color(0xFF008F6B),
    onSecondary = Color.White,
    secondaryContainer = GrowthMintLightContainer,
    onSecondaryContainer = Color(0xFF002116),
    tertiary = Color(0xFFC28400),
    onTertiary = Color.White,
    tertiaryContainer = ScoreGoldLightContainer,
    onTertiaryContainer = Color(0xFF291B00),
    background = StudioPaperLight,
    onBackground = StudioTextPrimaryLight,
    surface = StudioSurfaceLight,
    onSurface = StudioTextPrimaryLight,
    surfaceVariant = StudioCardLight,
    onSurfaceVariant = StudioTextMutedLight,
    surfaceContainerLow = StudioSurfaceLight,
    surfaceContainer = StudioCardLight,
    surfaceContainerHigh = Color(0xFFE9E6F0),
    outline = StudioOutlineLight,
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

val ViralKitShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ViralKitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ViralKitTypography,
        shapes = ViralKitShapes,
        content = content
    )
}
