package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import io.github.mojri.hesabyar.ui.designsystem.Dimens

/**
 * Reusable circular icon badge: a tinted icon centered inside a filled circle
 * whose background is a semi-transparent color.
 *
 * Consolidates the `Box(size).background(color.copy(alpha), CircleShape){ Icon }`
 * pattern that was repeated ~16 times across the dashboard, loan, installment and
 * category management screens.
 *
 * @param icon The icon to render.
 * @param modifier Modifier applied to the outer box.
 * @param tint Color of the icon; defaults to the icon background color.
 * @param backgroundColor Base color of the circular background (before [backgroundAlpha]).
 * @param backgroundAlpha Opacity applied to [backgroundColor]; defaults to 0.15f.
 * @param iconSize Size of the rendered icon.
 * @param containerSize Size of the circular badge.
 * @param contentDescription Accessibility label for the icon; `null` when decorative.
 */
@Composable
fun IconCircle(
  icon: ImageVector,
  modifier: Modifier = Modifier,
  tint: Color = MaterialTheme.colorScheme.primary,
  backgroundColor: Color = MaterialTheme.colorScheme.primary,
  backgroundAlpha: Float = Dimens.ICON_CIRCLE_BACKGROUND_ALPHA,
  iconSize: Dp = Dimens.IconSmall,
  containerSize: Dp = Dimens.AvatarSmall,
  contentDescription: String? = null
) {
  Box(
    modifier =
      modifier
        .size(containerSize)
        .background(backgroundColor.copy(alpha = backgroundAlpha), CircleShape),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = tint,
      modifier = Modifier.size(iconSize)
    )
  }
}
