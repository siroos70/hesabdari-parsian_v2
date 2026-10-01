package io.github.mojri.hesabyar.ui.screens.dashboard.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.components.icon
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.designsystem.toComposeColor
import io.github.mojri.hesabyar.ui.utils.formatPersianDate

@Suppress("LongMethod")
@Composable
internal fun TransactionDetailDialog(
  transaction: Transaction,
  categories: List<Category>,
  accounts: List<AccountEntity> = emptyList(),
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onDismiss: () -> Unit
) {
  val isIncome = transaction.type == TransactionType.INCOME
  val isTransfer = transaction.type == TransactionType.TRANSFER
  val category = categories.find { it.id == transaction.categoryId }
  val sourceAccount = accounts.find { it.id == transaction.accountId }
  val destAccount =
    if (isTransfer) accounts.find { it.id == transaction.destinationAccountId } else null

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "جزئیات تراکنش",
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Right
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "نوع:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text =
              when (transaction.type) {
                TransactionType.INCOME -> stringResource(R.string.category_type_income)
                TransactionType.TRANSFER -> stringResource(R.string.transaction_type_transfer)
                else -> stringResource(R.string.category_type_expense)
              },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color =
              when (transaction.type) {
                TransactionType.INCOME -> MaterialTheme.colorScheme.primary
                TransactionType.TRANSFER -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
              }
          )
        }

        HorizontalDivider()

        // Account row — color dot + account name as primary+secondary signal
        if (sourceAccount != null) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "حساب:",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
            ) {
              IconCircle(
                icon = sourceAccount.type.icon(),
                tint = sourceAccount.color.toComposeColor(),
                backgroundColor = sourceAccount.color.toComposeColor(),
                iconSize = 10.dp,
                containerSize = 16.dp,
              )
              Text(
                text =
                  if (isTransfer && destAccount != null) {
                    "${sourceAccount.name} → ${destAccount.name}"
                  } else {
                    sourceAccount.name
                  },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
              )
            }
          }
          HorizontalDivider()
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "مبلغ:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = CurrencyFormatter.format(transaction.amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color =
              if (isIncome) {
                MaterialTheme.colorScheme.primary
              } else if (isTransfer) {
                MaterialTheme.colorScheme.tertiary
              } else {
                MaterialTheme.colorScheme.error
              }
          )
        }

        HorizontalDivider()

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "دسته‌بندی:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = category?.name ?: "سایر",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
          )
        }

        HorizontalDivider()

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "تاریخ:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = formatPersianDate(transaction.date),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
          )
        }

        HorizontalDivider()

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "توضیحات:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = transaction.description,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
          )
        }
      }
    },
    confirmButton = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
      ) {
        Button(
          onClick = onEdit,
          modifier = Modifier.weight(1f),
          shape = ShapeTokens.Small
        ) {
          Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = null,
            modifier = Modifier.size(Dimens.IconSmall)
          )
          Spacer(modifier = Modifier.width(SpacingTokens.xs))
          Text("ویرایش")
        }
        Button(
          onClick = onDelete,
          modifier = Modifier.weight(1f),
          shape = ShapeTokens.Small,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
          Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = null,
            modifier = Modifier.size(Dimens.IconSmall)
          )
          Spacer(modifier = Modifier.width(SpacingTokens.xs))
          Text("حذف")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("بستن")
      }
    }
  )
}
