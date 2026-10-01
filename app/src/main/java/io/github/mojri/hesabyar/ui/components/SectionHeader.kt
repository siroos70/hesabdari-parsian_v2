package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Composable
fun SectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  action: @Composable (() -> Unit)? = null
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(
          horizontal = SpacingTokens.lg,
          vertical = SpacingTokens.md
        ),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = if (action != null) Modifier.weight(1f) else Modifier
    )
    action?.invoke()
  }
}
