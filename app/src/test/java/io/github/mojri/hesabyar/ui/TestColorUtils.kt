package io.github.mojri.hesabyar.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** WCAG 2.0 relative luminance for a Compose Color. */
internal fun Color.wcagLuminance(): Float {
  fun channel(c: Float) = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
  return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}
