package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Composable
fun LoanItem(
  personName: String,
  amount: Long,
  isDebt: Boolean,
  date: String,
  isSettled: Boolean = false,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null
) {
  val statusColor = if (isSettled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
  val statusText = if (isSettled) "تسویه شده" else "در انتظار"
  val amountColor = if (isDebt) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .then(
          if (onClick != null) {
            Modifier.clickable(onClick = onClick)
          } else {
            Modifier
          }
        ).padding(vertical = SpacingTokens.sm),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
  ) {
    Column(
      modifier = Modifier.weight(1f)
    ) {
      Text(
        text = personName,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = date,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Column(
      horizontalAlignment = Alignment.End
    ) {
      Text(
        text = CurrencyFormatter.format(amount),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = amountColor,
        textAlign = TextAlign.End
      )
      Text(
        text = statusText,
        style = MaterialTheme.typography.bodySmall,
        color = statusColor
      )
    }
  }
}
