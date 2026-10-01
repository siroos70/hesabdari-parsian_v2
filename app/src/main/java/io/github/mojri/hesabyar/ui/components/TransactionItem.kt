package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Composable
fun TransactionItem(
  title: String,
  amount: Long,
  isIncome: Boolean,
  isTransfer: Boolean = false,
  categoryColor: Color = Color.Unspecified,
  categoryInitial: String = "",
  date: String? = null,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  transactionId: Long? = null
) {
  val resolvedCategoryColor =
    categoryColor.takeUnless { it == Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .then(
          if (onClick != null) {
            Modifier
              .clickable(onClick = onClick)
              .semantics { role = Role.Button }
          } else {
            Modifier
          }
        ).padding(vertical = SpacingTokens.sm),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
  ) {
    TransactionItemCategoryIcon(
      categoryColor = resolvedCategoryColor,
      categoryInitial = categoryInitial
    )

    TransactionItemTitleBlock(
      title = title,
      date = date
    )

    TransactionItemAmount(
      amount = amount,
      isIncome = isIncome,
      isTransfer = isTransfer,
      transactionId = transactionId
    )
  }
}

/** Circular badge showing the category's initial letter on its color. */
@Composable
private fun TransactionItemCategoryIcon(
  categoryColor: Color,
  categoryInitial: String
) {
  Box(
    modifier =
      Modifier
        .size(40.dp)
        .clip(CircleShape)
        .background(categoryColor.copy(alpha = Dimens.ICON_CIRCLE_BACKGROUND_ALPHA)),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = categoryInitial,
      style = MaterialTheme.typography.titleSmall,
      color = categoryColor
    )
  }
}

/** Title and optional date column. Weighted fill is only valid inside a [Row]. */
@Composable
private fun RowScope.TransactionItemTitleBlock(
  title: String,
  date: String?
) {
  Column(
    modifier = Modifier.weight(1f)
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    date?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

/**
 * Signed amount row. The sign and the formatted amount stay in separate Texts so
 * the bidi algorithm cannot reorder the sign across the digits; merging
 * descendants keeps TalkBack announcing the pair as a single node.
 */
@Composable
private fun TransactionItemAmount(
  amount: Long,
  isIncome: Boolean,
  isTransfer: Boolean = false,
  transactionId: Long? = null
) {
  val amountColor =
    if (isIncome) {
      FinancialColors.IncomeGreen
    } else if (isTransfer) {
      MaterialTheme.colorScheme.tertiary
    } else {
      FinancialColors.ExpenseRed
    }
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
        Modifier
          .semantics(mergeDescendants = true) {
          }.testTag("transaction_item_amount_${transactionId ?: "unknown"}")
    ) {
      val (sign, formattedAmount) =
        CurrencyFormatter.formatSignedParts(
          if (isIncome || isTransfer) amount else -amount
        )
      Text(
        text = sign,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = amountColor,
      )
      Text(
        text = formattedAmount,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = amountColor,
        textAlign = TextAlign.End
      )
    }
  }
}
