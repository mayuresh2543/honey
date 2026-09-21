package com.honeyfile.security.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Shape Scale
 * Provides rounded, expressive container curves that emphasize hierarchy and touch targets.
 */
val HoneyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

// Convenient Expressive Shape Tokens
val FullPillShape = RoundedCornerShape(50)
val StatCardShape = RoundedCornerShape(24.dp)
val ActionTileShape = RoundedCornerShape(22.dp)
val ContainerSurfaceShape = RoundedCornerShape(28.dp)
val FloatingNavShape = RoundedCornerShape(32.dp)
val DialogSurfaceShape = RoundedCornerShape(28.dp)
