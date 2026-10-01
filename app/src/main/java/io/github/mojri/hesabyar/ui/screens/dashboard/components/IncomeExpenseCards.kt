package io.github.mojri.hesabyar.ui.screens.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.DashboardData
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Suppress("LongMethod")
@Composable
internal fun IncomeExpenseCards(dashboardData: DashboardData) {
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.md),
    maxItemsInEachRow = 2
  ) {
    HesabyarCard(
      modifier = Modifier.weight(1f),
      shape = ShapeTokens.Large,
      cardColors =
        CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconCircle(
            icon = Icons.Filled.TrendingUp,
            tint = MaterialTheme.colorScheme.primary,
            backgroundColor = MaterialTheme.colorScheme.primary,
            containerSize = 28.dp
          )
          Spacer(modifier = Modifier.width(SpacingTokens.sm))
          Text(
            text = "درآمد ۳۰ روزه",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Text(
          text = CurrencyFormatter.format(dashboardData.monthlyIncome),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    HesabyarCard(
      modifier = Modifier.weight(1f),
      shape = ShapeTokens.Large,
      cardColors =
        CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconCircle(
            icon = Icons.Filled.TrendingDown,
            tint = MaterialTheme.colorScheme.error,
            backgroundColor = MaterialTheme.colorScheme.error,
            containerSize = 28.dp
          )
          Spacer(modifier = Modifier.width(SpacingTokens.sm))
          Text(
            text = "مخارج ۳۰ روزه",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Text(
          text = CurrencyFormatter.format(dashboardData.monthlyExpenses),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.error,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
