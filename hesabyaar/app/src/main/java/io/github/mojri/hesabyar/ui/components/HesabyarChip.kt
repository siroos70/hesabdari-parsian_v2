package io.github.mojri.hesabyar.ui.components

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens

@Composable
fun HesabyarChip(
  selected: Boolean,
  onClick: () -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  leadingIcon: @Composable (() -> Unit)? = null,
  enabled: Boolean = true,
  shape: Shape = ShapeTokens.Full,
  labelStyle: TextStyle = MaterialTheme.typography.labelLarge,
  unselectedContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
  unselectedLabelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    modifier = modifier,
    label = { Text(label, style = labelStyle) },
    leadingIcon = leadingIcon,
    enabled = enabled,
    shape = shape,
    colors =
      FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        containerColor = unselectedContainerColor,
        labelColor = unselectedLabelColor,
      )
  )
}
