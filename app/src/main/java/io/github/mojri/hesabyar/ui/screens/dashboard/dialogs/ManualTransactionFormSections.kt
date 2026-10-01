package io.github.mojri.hesabyar.ui.screens.dashboard.dialogs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.components.AmountQuickFillButtons
import io.github.mojri.hesabyar.ui.components.HesabyarChip
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.components.icon
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.designsystem.toComposeColor

@Composable
internal fun TransactionTypeSelector(
  selectedType: String,
  isEditMode: Boolean,
  categories: List<Category>,
  onTypeSelected: (String, Long) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
    Text(
      text = "نوع تراکنش / تعهد مالی:",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
    ) {
      val types =
        if (isEditMode) {
          listOf(Pair("EXPENSE", "هزینه"), Pair("INCOME", "درآمد"), Pair("TRANSFER", "انتقال"))
        } else {
          listOf(
            Pair("EXPENSE", "هزینه"),
            Pair("INCOME", "درآمد"),
            Pair("LOAN_DEBTOR", "طلب (قرض دادم)"),
            Pair("LOAN_CREDITOR", "بدهی (قرض گرفتم)"),
            Pair("INSTALLMENT", "قسط"),
            Pair("TRANSFER", "انتقال")
          )
        }
      types.forEach { (typeKey, typeLabel) ->
        val chipColor = resolveTypeColor(typeKey)
        FilterChip(
          selected = selectedType == typeKey,
          onClick = {
            val categoryId = resolveDefaultCategoryId(typeKey, categories)
            onTypeSelected(typeKey, categoryId)
          },
          label = { Text(text = typeLabel, fontWeight = FontWeight.Bold) },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = chipColor,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )
      }
    }
  }
}

@Composable
internal fun TransactionAmountInput(
  amountValue: TextFieldValue,
  typeColor: androidx.compose.ui.graphics.Color,
  onAmountChanged: (TextFieldValue, Boolean) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
    Text(
      text = "مبلغ (${CurrencyFormatter.unitLabel}):",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
      value = amountValue,
      onValueChange = { onAmountChanged(it, false) },
      modifier =
        Modifier
          .fillMaxWidth()
          .testTag("manual_amount_input"),
      shape = ShapeTokens.Medium,
      leadingIcon = {
        Icon(imageVector = Icons.Filled.Paid, contentDescription = null, tint = typeColor)
      },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      singleLine = true,
      colors =
        OutlinedTextFieldDefaults.colors(
          focusedBorderColor = typeColor,
          focusedLabelColor = typeColor
        )
    )
    AmountQuickFillButtons(
      amountValue = amountValue,
      onValueChanged = { onAmountChanged(it, true) }
    )
    val amtDisplay = amountValue.text.toLongOrNull() ?: 0L
    if (amtDisplay > 0L) {
      val amtRial = CurrencyFormatter.toRial(amtDisplay)
      Text(
        text = "معادل: ${CurrencyFormatter.format(amtRial)}",
        style = MaterialTheme.typography.bodySmall,
        color = typeColor,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = SpacingTokens.xs)
      )
    }
  }
}

@Composable
internal fun TransactionCategorySelector(
  filteredCategories: List<Category>,
  selectedCategoryId: Long,
  onCategorySelected: (Long) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
    Text(
      text = "دسته‌بندی مربوطه:",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
    ) {
      filteredCategories.forEach { cat ->
        FilterChip(
          selected = selectedCategoryId == cat.id,
          onClick = { onCategorySelected(cat.id) },
          label = { Text(text = cat.name, fontWeight = FontWeight.Medium) },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primary,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )
      }
    }
  }
}

@Composable
internal fun LoanPersonNameInput(
  personName: String,
  onPersonNameChanged: (String) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
    Text(
      text = "طرف حساب (شخص مربوطه):",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
      value = personName,
      onValueChange = onPersonNameChanged,
      modifier =
        Modifier
          .fillMaxWidth()
          .testTag("manual_person_input"),
      shape = ShapeTokens.Medium,
      leadingIcon = {
        Icon(
          imageVector = Icons.Filled.Person,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      },
      placeholder = { Text("مثلا: علی محمودی", style = MaterialTheme.typography.bodyMedium) },
      singleLine = true
    )
  }
}

@Composable
internal fun InstallmentFormFields(
  title: String,
  daysFromNow: String,
  onTitleChanged: (String) -> Unit,
  onDaysChanged: (String) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)) {
    Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
      Text(
        text = "عنوان قسط:",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      OutlinedTextField(
        value = title,
        onValueChange = onTitleChanged,
        modifier =
          Modifier
            .fillMaxWidth()
            .testTag("manual_title_input"),
        shape = ShapeTokens.Medium,
        placeholder = { Text("مثلا: قسط بانک مسکن", style = MaterialTheme.typography.bodyMedium) },
        singleLine = true
      )
    }
    Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
      Text(
        text = "فاصله تا موعد پرداخت (روز):",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      OutlinedTextField(
        value = daysFromNow,
        onValueChange = onDaysChanged,
        modifier =
          Modifier
            .fillMaxWidth()
            .testTag("manual_days_input"),
        shape = ShapeTokens.Medium,
        placeholder = { Text("مثلا: ۳۰", style = MaterialTheme.typography.bodyMedium) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
      )
    }
  }
}

@Composable
internal fun TransactionDescriptionInput(
  description: String,
  onDescriptionChanged: (String) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
    Text(
      text = "شرح یا توضیح تراکنش:",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
      value = description,
      onValueChange = onDescriptionChanged,
      modifier =
        Modifier
          .fillMaxWidth()
          .testTag("manual_description_input"),
      shape = ShapeTokens.Medium,
      leadingIcon = {
        Icon(
          imageVector = Icons.Filled.Description,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      },
      singleLine = true
    )
  }
}

@Composable
private fun resolveTypeColor(typeKey: String) =
  when (typeKey) {
    "INCOME", "LOAN_DEBTOR" -> MaterialTheme.colorScheme.primary
    "EXPENSE", "LOAN_CREDITOR" -> MaterialTheme.colorScheme.error
    "TRANSFER" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.tertiary
  }

private fun resolveDefaultCategoryId(
  typeKey: String,
  categories: List<Category>
): Long =
  when (typeKey) {
    "INCOME" -> categories.find { it.key == "Income" }?.id ?: 1L
    "EXPENSE" -> categories.find { it.key == "Expense" }?.id ?: 1L
    "LOAN_DEBTOR", "LOAN_CREDITOR" -> categories.find { it.key == "Loans" }?.id ?: 1L
    "INSTALLMENT" -> categories.find { it.key == "Installments" }?.id ?: 1L
    "TRANSFER" -> 0L
    else -> 1L
  }

@Composable
internal fun DestinationAccountSelector(
  accounts: List<AccountEntity>,
  sourceAccountId: Long,
  selectedDestinationAccountId: Long?,
  onDestinationAccountSelected: (Long?) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
    Text(
      text = "حساب مقصد:",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
    ) {
      accounts.filter { !it.isArchived && it.id != sourceAccountId }.forEach { account ->
        HesabyarChip(
          selected = selectedDestinationAccountId == account.id,
          onClick = { onDestinationAccountSelected(account.id) },
          label = account.name,
          leadingIcon = {
            IconCircle(
              icon = account.type.icon(),
              tint = account.color.toComposeColor(),
              backgroundColor = account.color.toComposeColor(),
              iconSize = 12.dp,
              containerSize = Dimens.IconSmall,
            )
          },
          shape = ShapeTokens.Small,
        )
      }
    }
  }
}
