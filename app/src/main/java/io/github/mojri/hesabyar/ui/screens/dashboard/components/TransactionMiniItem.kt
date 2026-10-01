package io.github.mojri.hesabyar.ui.screens.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.components.icon
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.designsystem.toComposeColor
import io.github.mojri.hesabyar.ui.utils.formatPersianDate
import io.github.mojri.hesabyar.ui.utils.resolveCategoryIcon

@Suppress("LongMethod")
@Composable
internal fun TransactionMiniItem(
  transaction: Transaction,
  categories: List<Category> = emptyList(),
  accounts: List<AccountEntity> = emptyList(),
  onClick: () -> Unit = {},
  onDelete: () -> Unit = {}
) {
  val isIncome = transaction.type == TransactionType.INCOME
  val isTransfer = transaction.type == TransactionType.TRANSFER
  val category = categories.find { it.id == transaction.categoryId }
  val categoryColor =
    category?.let { Color(it.color) }
      ?: if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
  val icon = resolveCategoryIcon(category?.icon)

  val sourceAccount = accounts.find { it.id == transaction.accountId }
  val destAccount =
    if (isTransfer) accounts.find { it.id == transaction.destinationAccountId } else null
  val sourceColor = sourceAccount?.color?.toComposeColor() ?: MaterialTheme.colorScheme.primary
  val destColor = destAccount?.color?.toComposeColor()

  HesabyarCard(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick),
    shape = ShapeTokens.Medium,
    cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    contentPadding = PaddingValues(SpacingTokens.md)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        AccountColorIndicator(
          sourceAccount = sourceAccount,
          destAccount = destAccount,
          sourceColor = sourceColor,
          destColor = destColor,
        )
        Spacer(modifier = Modifier.width(SpacingTokens.md))
        Column {
          Text(
            text = transaction.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = buildSubtitle(transaction, category, sourceAccount, destAccount, isTransfer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            val signedAmount = if (isIncome || isTransfer) transaction.amount else -transaction.amount
            val (sign, amount) =
              CurrencyFormatter.formatSignedParts(signedAmount)
            val amountColor =
              if (isIncome) {
                MaterialTheme.colorScheme.primary
              } else if (isTransfer) {
                MaterialTheme.colorScheme.tertiary
              } else {
                MaterialTheme.colorScheme.error
              }
            Text(text = sign, style = MaterialTheme.typography.bodyMedium, color = amountColor)
            Text(text = amount, style = MaterialTheme.typography.bodyMedium, color = amountColor)
          }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
          Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "حذف تراکنش",
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

/** Builds the subtitle line: date | category · account → destAccount. */
private fun buildSubtitle(
  transaction: Transaction,
  category: Category?,
  sourceAccount: AccountEntity?,
  destAccount: AccountEntity?,
  isTransfer: Boolean,
): String =
  buildString {
    append(formatPersianDate(transaction.date))
    append(" | ")
    append(category?.name ?: "سایر")
    sourceAccount?.let {
      append(" · ")
      append(it.name)
    }
    if (isTransfer && destAccount != null) {
      append(" → ")
      append(destAccount.name)
    }
  }

/**
 * Circular icon badge indicating the source account (and destination for transfers).
 *
 * Uses [IconCircle] from the design system with a 15% tinted background,
 * showing the account type icon (bank, wallet, etc.) instead of a plain dot.
 * For transfers, two stacked badges (source + destination) are shown.
 */
@Composable
private fun AccountColorIndicator(
  sourceAccount: AccountEntity?,
  destAccount: AccountEntity?,
  sourceColor: Color,
  destColor: Color?,
  modifier: Modifier = Modifier,
) {
  val sourceType = sourceAccount?.type ?: AccountType.OTHER
  if (destColor != null) {
    val destType = destAccount?.type ?: AccountType.OTHER
    Column(
      modifier = modifier,
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      IconCircle(
        icon = sourceType.icon(),
        tint = sourceColor,
        backgroundColor = sourceColor,
        iconSize = 12.dp,
        containerSize = 20.dp,
      )
      Spacer(modifier = Modifier.height(1.dp))
      IconCircle(
        icon = destType.icon(),
        tint = destColor,
        backgroundColor = destColor,
        iconSize = 12.dp,
        containerSize = 20.dp,
      )
    }
  } else {
    IconCircle(
      icon = sourceType.icon(),
      modifier = modifier,
      tint = sourceColor,
      backgroundColor = sourceColor,
      iconSize = 14.dp,
      containerSize = 24.dp,
    )
  }
}
