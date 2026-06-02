package com.example.hybrid_ai_app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Applies the Botanical Green palette mapping
private val LightColorScheme = lightColorScheme(
    primary = PrimaryBrand,
    onPrimary = OnPrimaryText,
    primaryContainer = PrimaryContainerSage,
    onPrimaryContainer = OnPrimaryContainerText,
    secondary = SecondaryBrand,
    onSecondary = OnSecondaryText,
    secondaryContainer = SecondaryContainer,
    background = SurfaceBase,
    onBackground = OnSurfaceText,
    surface = SurfaceCards,
    onSurface = OnSurfaceText,
    surfaceVariant = SurfaceBase,
    onSurfaceVariant = OnSurfaceVariantText,
    error = ErrorRed,
    errorContainer = ErrorContainer,
    outline = OutlineBorder
)

// Premium Dark Mode mapping
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBrand, // Keeps brand identity
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1A2E20), // Deep muted green for containers
    onPrimaryContainer = PrimaryBrand,
    secondary = SecondaryBrand,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2D3748),
    background = Color(0xFF121212), // Deep premium black
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E), // Slightly elevated for cards
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2C2C2C), // Filter chips and secondary surfaces
    onSurfaceVariant = Color(0xFFBDBDBD),
    error = ErrorRed,
    errorContainer = Color(0xFF4D0000), // Muted dark red for error cards
    outline = Color(0xFF424242)
)

@Composable
fun HybridTrainingTheme(
    // Added dynamic switch defaulting to system preference
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    // Dynamic status bar adapting to light/dark modes
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Will configure Plus Jakarta Sans later
        shapes = HybridShapes,
        content = content
    )
}