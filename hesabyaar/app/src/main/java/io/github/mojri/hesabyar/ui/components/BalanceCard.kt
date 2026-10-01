package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Composable
fun BalanceCard(
  balance: Long,
  income: Long,
  expense: Long,
  modifier: Modifier = Modifier,
  shape: Shape = ShapeTokens.XLarge,
  onClick: (() -> Unit)? = null
) {
  val gradientBrush =
    remember(shape) {
      Brush.verticalGradient(
        colors =
          listOf(
            FinancialColors.PurpleAccent.copy(alpha = 0.2f),
            Color.Transparent
          )
      )
    }

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .then(
          if (onClick != null) {
            Modifier.clickable(onClick = onClick)
          } else {
            Modifier
          }
        ).background(
          brush = gradientBrush,
          shape = shape
        ).padding(SpacingTokens.lg),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
    ) {
      Text(
        text = "موجودی",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = CurrencyFormatter.format(balance),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Row(
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xl)
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "درآمد",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = CurrencyFormatter.format(income),
            style = MaterialTheme.typography.bodyMedium,
            color = FinancialColors.IncomeGreen
          )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "هزینه",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = CurrencyFormatter.format(expense),
            style = MaterialTheme.typography.bodyMedium,
            color = FinancialColors.ExpenseRed
          )
        }
      }
    }
  }
}
