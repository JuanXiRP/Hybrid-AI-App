package com.example.hybrid_ai_app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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

@Composable
fun HybridTrainingTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current

    // Configures the system status bar to match the clean aesthetic
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SurfaceBase.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography, // Will configure Plus Jakarta Sans later
        shapes = HybridShapes,
        content = content
    )
}