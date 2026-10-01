package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Composable
fun EmptyState(
  icon: ImageVector,
  title: String,
  description: String? = null,
  actionText: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(SpacingTokens.xl),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(Dimens.AvatarLarge),
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      modifier = Modifier.semantics { heading() }
    )

    description?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
      )
    }

    if (actionText != null && onAction != null) {
      Spacer(modifier = Modifier.height(SpacingTokens.sm))
      HesabyarButton(
        onClick = onAction,
        text = actionText
      )
    }
  }
}
