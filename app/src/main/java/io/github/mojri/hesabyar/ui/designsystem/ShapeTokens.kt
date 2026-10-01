package io.github.mojri.hesabyar.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object ShapeTokens {
  val Small = RoundedCornerShape(8.dp)
  val Medium = RoundedCornerShape(12.dp)
  val Large = RoundedCornerShape(16.dp)
  val XLarge = RoundedCornerShape(24.dp)
  val Full = RoundedCornerShape(9999.dp)
}

// Canonical Material 3 Shapes backed by ShapeTokens, wired into MaterialTheme.
val AppShapes =
  Shapes(
    extraSmall = ShapeTokens.Small,
    small = ShapeTokens.Small,
    medium = ShapeTokens.Medium,
    large = ShapeTokens.Large,
    extraLarge = ShapeTokens.XLarge
  )
