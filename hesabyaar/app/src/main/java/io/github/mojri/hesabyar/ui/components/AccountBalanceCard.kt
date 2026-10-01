package io.github.mojri.hesabyar.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.AccountDashboardSummary
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.designsystem.toComposeColor
import java.util.Locale
import kotlin.math.abs

private val SELECTED_BACKGROUND_ALPHA = Dimens.ICON_CIRCLE_BACKGROUND_ALPHA
private const val SELECTED_BORDER_WIDTH = 2f
private const val CHECK_ICON_SIZE = 18
private val CARD_HEIGHT = 88.dp

@Composable
fun AccountBalanceCard(
  summary: AccountDashboardSummary,
  modifier: Modifier = Modifier,
  isSelected: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val accentColor = summary.accountColor.toComposeColor()
  val animatedBorderColor by animateColorAsState(
    targetValue = if (isSelected) accentColor else Color.Transparent,
    label = "border_color"
  )
  val animatedBorderWidth by animateDpAsState(
    targetValue = if (isSelected) SELECTED_BORDER_WIDTH.dp else 0.dp,
    label = "border_width"
  )

  val cardLabel =
    if (isSelected) {
      stringResource(
        id = io.github.mojri.hesabyar.R.string.account_selected,
        summary.accountName
      )
    } else {
      stringResource(
        id = io.github.mojri.hesabyar.R.string.account_unselected,
        summary.accountName
      )
    }

  Box(
    modifier =
      modifier
        .widthIn(min = 160.dp)
        .fillMaxWidth()
        .height(CARD_HEIGHT)
        .background(
          color = animatedCardBackground(accentColor, isSelected),
          shape = ShapeTokens.Medium,
        ).border(width = animatedBorderWidth, color = animatedBorderColor, shape = ShapeTokens.Medium)
        .semantics {
          selected = isSelected
          contentDescription = cardLabel
        }.then(
          if (onClick != null) {
            Modifier.selectable(
              selected = isSelected,
              onClick = onClick,
              role = Role.Checkbox,
            )
          } else {
            Modifier
          }
        ),
  ) {
    CardCardContent(summary, accentColor)
    if (isSelected) SelectionBadge(accentColor)
  }
}

@Composable
private fun CardCardContent(
  summary: AccountDashboardSummary,
  accentColor: Color,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = SpacingTokens.md, vertical = SpacingTokens.sm),
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Left: account type icon in tinted circle
    AccountTypeIcon(
      accountType = summary.accountType,
      accountColor = accentColor,
      contentDescription = summary.accountType.displayName,
    )

    // Center: name + type
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.xxs),
    ) {
      Text(
        text = summary.accountName,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = summary.accountType.displayName,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // Right: balance + trend
    Column(
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.xxs),
    ) {
      Text(
        text = CurrencyFormatter.format(summary.balance),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      TrendIndicator(delta = summary.monthlyDelta)
    }
  }
}

/**
 * Month-over-month trend indicator.
 *
 * - `delta > 0` → green text with ▲ arrow (e.g. "+4% ▲")
 * - `delta < 0` → red text with ▼ arrow (e.g. "-12% ▼")
 * - `delta == 0.0`, NaN, or Infinity → no indicator shown
 */
@Composable
private fun TrendIndicator(delta: Double) {
  if (delta == 0.0 || delta.isNaN() || delta.isInfinite()) return

  val isPositive = delta > 0
  val color = if (isPositive) FinancialColors.IncomeGreen else FinancialColors.ExpenseRed
  val arrow = if (isPositive) "▲" else "▼"
  val sign = if (isPositive) "+" else ""
  // Format as integer percentage when whole, else one decimal
  val pct =
    if (abs(delta - delta.toLong()) < 0.005) {
      "${delta.toLong()}"
    } else {
      String.format(Locale.US, "%.1f", delta)
    }

  // Force LTR layout so the sign (±) always appears on the left of the
  // percentage, regardless of the page's RTL direction.
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Text(
      text = "$sign$pct% $arrow",
      style = MaterialTheme.typography.labelMedium,
      color = color,
      textAlign = TextAlign.End,
    )
  }
}

@Composable
private fun BoxScope.SelectionBadge(accentColor: Color) {
  Icon(
    imageVector = Icons.Filled.Check,
    contentDescription = "حساب انتخاب\u200cشده",
    modifier =
      Modifier
        .align(Alignment.TopEnd)
        .padding(SpacingTokens.xs)
        .size(CHECK_ICON_SIZE.dp)
        .background(color = accentColor, shape = ShapeTokens.Small),
    tint = Color.White,
  )
}

@Composable
private fun animatedCardBackground(
  accentColor: Color,
  isSelected: Boolean,
): Color {
  val targetColor =
    if (isSelected) {
      accentColor.copy(alpha = SELECTED_BACKGROUND_ALPHA)
    } else {
      MaterialTheme.colorScheme.surfaceContainerLow
    }
  return animateColorAsState(targetValue = targetColor, label = "card_bg").value
}
