package com.cra.cloudreve.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    onPrimary = BlueOnPrimary,
    primaryContainer = BluePrimaryContainer,
    onPrimaryContainer = BlueOnPrimaryContainer,
    secondary = TealSecondary,
    onSecondary = TealOnSecondary,
    secondaryContainer = TealSecondaryContainer,
    onSecondaryContainer = TealOnSecondaryContainer,
    tertiary = AmberTertiary,
    onTertiary = AmberOnTertiary,
    tertiaryContainer = AmberTertiaryContainer,
    onTertiaryContainer = AmberOnTertiaryContainer,
    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorContainerRed,
    onErrorContainer = OnErrorContainerRed,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    background = LightSurface,
    onBackground = LightOnSurface
)

private val DarkColors = darkColorScheme(
    primary = DarkBluePrimary,
    onPrimary = DarkOnBluePrimary,
    primaryContainer = DarkBluePrimaryContainer,
    onPrimaryContainer = DarkOnBluePrimaryContainer,
    secondary = DarkTealSecondary,
    onSecondary = DarkOnTealSecondary,
    secondaryContainer = DarkTealSecondaryContainer,
    onSecondaryContainer = DarkOnTealSecondaryContainer,
    tertiary = DarkAmberTertiary,
    onTertiary = DarkOnAmberTertiary,
    tertiaryContainer = DarkAmberTertiaryContainer,
    onTertiaryContainer = DarkOnAmberTertiaryContainer,
    error = DarkErrorRed,
    onError = DarkOnErrorRed,
    errorContainer = DarkErrorContainerRed,
    onErrorContainer = DarkOnErrorContainerRed,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    background = DarkSurface,
    onBackground = DarkOnSurface
)

val CraShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalDynamicColor = staticCompositionLocalOf { true }

@Composable
fun CraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(LocalDynamicColor provides dynamicColor) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = CraShapes,
            typography = MaterialTheme.typography,
            content = content
        )
    }
}
