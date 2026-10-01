package io.github.mojri.hesabyar.ui.screens.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.CategoryBreakdown
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.screens.DonutChart

/**
 * Donut chart showing per-account expense distribution.
 *
 * Each segment uses the account's configured color. The legend pairs a color
 * dot with the account name text (secondary signal for accessibility).
 */
@Composable
internal fun AccountBreakdownCard(accountBreakdown: List<CategoryBreakdown>) {
  HesabyarCard(
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
      Text(
        text = stringResource(id = io.github.mojri.hesabyar.R.string.expense_distribution_by_account),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
      )

      DonutChart(
        data = accountBreakdown,
        modifier =
          Modifier
            .fillMaxWidth()
            .height(200.dp)
      )

      // Legend — account name + color dot + percentage
      accountBreakdown.forEach { item ->
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = SpacingTokens.xs),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
          ) {
            Box(
              modifier =
                Modifier
                  .size(12.dp)
                  .background(Color(item.color), CircleShape)
            )
            Text(
              text = item.categoryName,
              style = MaterialTheme.typography.bodySmall
            )
          }
          Text(
            text = "${(item.percentage * 100).toInt()}٪ | ${CurrencyFormatter.format(item.total)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
          )
        }
      }
    }
  }
}
