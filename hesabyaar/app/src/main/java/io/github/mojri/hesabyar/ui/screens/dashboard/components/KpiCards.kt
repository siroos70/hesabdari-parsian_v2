package io.github.mojri.hesabyar.ui.screens.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.DashboardData
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Suppress("LongMethod")
@Composable
internal fun KpiCards(dashboardData: DashboardData) {
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
          containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconCircle(
            icon = Icons.Filled.Savings,
            tint = MaterialTheme.colorScheme.primary,
            backgroundColor = MaterialTheme.colorScheme.primary,
            containerSize = 28.dp
          )
          Spacer(modifier = Modifier.width(SpacingTokens.sm))
          Text(
            text = "نرخ پس‌انداز",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        val savingsPct = (dashboardData.savingsRate * 100).toInt()
        Text(
          text = "$savingsPct%",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color =
            when {
              savingsPct >= 20 -> MaterialTheme.colorScheme.primary
              savingsPct >= 0 -> MaterialTheme.colorScheme.tertiary
              else -> MaterialTheme.colorScheme.error
            }
        )
      }
    }

    HesabyarCard(
      modifier = Modifier.weight(1f),
      shape = ShapeTokens.Large,
      cardColors =
        CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconCircle(
            icon = Icons.Filled.AccountBalance,
            tint = MaterialTheme.colorScheme.secondary,
            backgroundColor = MaterialTheme.colorScheme.secondary,
            containerSize = 28.dp
          )
          Spacer(modifier = Modifier.width(SpacingTokens.sm))
          Text(
            text = "نسبت بدهی",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        val debtPct = (dashboardData.debtToIncomeRatio * 100).toInt()
        Text(
          text = "$debtPct%",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color =
            when {
              debtPct > 40 -> MaterialTheme.colorScheme.error
              debtPct > 20 -> MaterialTheme.colorScheme.tertiary
              else -> MaterialTheme.colorScheme.secondary
            }
        )
      }
    }
  }
}
