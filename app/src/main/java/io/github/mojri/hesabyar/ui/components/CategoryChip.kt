package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

/** Text color for a category chip initial on [bg] background. */
internal fun textColorForBackground(bg: Color): Color = if (bg.luminance() > 0.179f) Color.Black else Color.White

/** Selected-label color for [categoryColor] on [surface]. */
internal fun selectedLabelColor(
  surface: Color,
  categoryColor: Color
): Color {
  val lerped = lerp(surface, categoryColor, 0.15f)
  return if (lerped.luminance() > 0.5f) Color.Black else Color.White
}

@Composable
fun CategoryFilterChip(
  category: Category?,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  shape: Shape = ShapeTokens.Full
) {
  val categoryColor = category?.let { Color(it.color) } ?: Color.Gray
  val categoryInitial = category?.name?.firstOrNull()?.toString() ?: ""
  val textColor = textColorForBackground(categoryColor)

  FilterChip(
    selected = selected,
    onClick = onClick,
    modifier = modifier,
    label = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
      ) {
        Box(
          modifier =
            Modifier
              .size(Dimens.IconSmall)
              .clip(CircleShape)
              .background(categoryColor),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = categoryInitial,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
          )
        }
        Text(
          text = category?.name ?: "همه",
          style = MaterialTheme.typography.bodySmall
        )
      }
    },
    shape = shape,
    colors =
      FilterChipDefaults.filterChipColors(
        selectedContainerColor = categoryColor.copy(alpha = 0.15f),
        selectedLabelColor = selectedLabelColor(MaterialTheme.colorScheme.surface, categoryColor)
      )
  )
}
