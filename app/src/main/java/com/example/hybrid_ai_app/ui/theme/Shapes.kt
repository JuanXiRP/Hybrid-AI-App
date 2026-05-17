package com.example.hybrid_ai_app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Maps the design system radii to Material 3 shapes
val HybridShapes = Shapes(
    small = RoundedCornerShape(12.dp), // Input fields
    medium = RoundedCornerShape(16.dp), // Standard UI elements
    large = RoundedCornerShape(24.dp), // Main Cards (Level 1)
    extraLarge = RoundedCornerShape(9999.dp) // Pill-shaped buttons
)