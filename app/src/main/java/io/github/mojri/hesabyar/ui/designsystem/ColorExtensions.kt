package io.github.mojri.hesabyar.ui.designsystem

import androidx.compose.ui.graphics.Color

/** Converts a signed-Long-stored ARGB colour (as used by Room entities) to Compose [Color]. */
fun Long.toComposeColor(): Color = Color(this.toInt())
