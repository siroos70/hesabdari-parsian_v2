package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

/**
 * Lightweight empty-state hint shown inside cards when a list or dataset is empty.
 *
 * Replaces the repeated inline `Text` blocks (centered, muted, padded) that appeared
 * in the analytics cards of
 * [io.github.mojri.hesabyar.ui.screens.AnalyticsScreen] (e.g. `MonthlyTrendCard`,
 * `CategoryBreakdownCard`, `LoanStatusCard`, `BankLoanStatusCard`, ...).
 *
 * Prefer [EmptyState] when an icon and/or an action button is desired; use this
 * compact variant for plain in-card messages.
 *
 * @param message The hint text to display.
 * @param modifier Modifier applied to the text container.
 */
@Composable
fun CardEmptyHint(
  message: String,
  modifier: Modifier = Modifier
) {
  Text(
    text = message,
    modifier =
      modifier
        .fillMaxWidth()
        .padding(SpacingTokens.lg),
    textAlign = TextAlign.Center,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}
