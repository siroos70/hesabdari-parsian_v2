package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Composable
fun InstallmentItem(
  title: String,
  paidAmount: Long,
  remainingAmount: Long,
  dueDate: String,
  progress: Float,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null
) {
  Column(
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
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = dueDate,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    LinearProgressIndicator(
      progress = { progress },
      modifier =
        Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(ShapeTokens.Small),
      color = if (progress >= 1f) FinancialColors.IncomeGreen else FinancialColors.InfoBlue,
      trackColor = MaterialTheme.colorScheme.surfaceVariant
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = "پرداخت شده: ${CurrencyFormatter.format(paidAmount)}",
        style = MaterialTheme.typography.bodySmall,
        color = FinancialColors.IncomeGreen
      )
      Text(
        text = "باقیمانده: ${CurrencyFormatter.format(remainingAmount)}",
        style = MaterialTheme.typography.bodySmall,
        color = FinancialColors.ExpenseRed
      )
    }
  }
}
