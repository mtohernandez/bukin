package com.buk.bukin.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val BukShapes: Shapes = Shapes(
    // Small pills — the `Ticket` badge.
    extraSmall = RoundedCornerShape(percent = 50),
    small = RoundedCornerShape(12.dp),
    // Check In button.
    medium = RoundedCornerShape(20.dp),
    // Ticket card, outlined cards.
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
