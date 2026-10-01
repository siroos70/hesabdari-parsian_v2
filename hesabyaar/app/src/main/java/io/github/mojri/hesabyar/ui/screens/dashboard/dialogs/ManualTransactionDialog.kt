package io.github.mojri.hesabyar.ui.screens.dashboard.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.usecase.SubmitManualTransactionUseCase
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.components.AccountSelector
import io.github.mojri.hesabyar.ui.components.HesabyarDialog
import io.github.mojri.hesabyar.ui.components.JalaliDateTimePicker
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
internal fun ManualTransactionDialog(
  onSubmit: suspend (
    SubmitManualTransactionUseCase.SubmitManualTransactionRequest
  ) -> SubmitManualTransactionUseCase.SubmitResult,
  categories: List<Category>,
  transactionToEdit: Transaction? = null,
  onDismiss: () -> Unit,
  accounts: List<AccountEntity> = emptyList(),
  selectedAccountId: Long? = null,
  onAccountSelected: (Long?) -> Unit = {}
) {
  val context = LocalContext.current
  val isEditMode = transactionToEdit != null
  var selectedType by remember { mutableStateOf(transactionToEdit?.type?.name ?: TransactionType.EXPENSE.name) }
  var destinationAccountId by remember {
    mutableStateOf(
      if (isEditMode && transactionToEdit.type == TransactionType.TRANSFER) {
        transactionToEdit.destinationAccountId
      } else {
        null
      }
    )
  }
  // The source-account selection lives in local dialog state so an edit can
  // move the transaction to another account. It is seeded from the caller's
  // selection (falling back to the transaction's own account) and must not be
  // re-derived from the transaction on every recomposition, which would
  // discard the user's choice.
  var sourceAccountId by remember { mutableStateOf(selectedAccountId ?: transactionToEdit?.accountId) }
  val originalAmountRial by remember { mutableStateOf(transactionToEdit?.amount ?: 0L) }
  var amountValue by remember {
    mutableStateOf(
      TextFieldValue(
        if (isEditMode) CurrencyFormatter.fromRial(transactionToEdit.amount).toString() else ""
      )
    )
  }
  var amountModified by remember { mutableStateOf(false) }
  var descriptionText by remember { mutableStateOf(transactionToEdit?.description.orEmpty()) }
  var selectedCategoryId by remember { mutableStateOf(transactionToEdit?.categoryId ?: 0L) }
  var personNameText by remember { mutableStateOf(transactionToEdit?.personName ?: "") }
  var titleText by remember { mutableStateOf(transactionToEdit?.description ?: "") }
  var daysFromNowText by remember { mutableStateOf("30") }
  var customDate by remember { mutableStateOf(transactionToEdit?.date ?: System.currentTimeMillis()) }
  var isSubmitting by remember { mutableStateOf(false) }

  val filteredCategories =
    when (selectedType) {
      TransactionType.INCOME.name ->
        categories.filter { cat ->
          cat.type == CategoryType.INCOME ||
            cat.type == CategoryType.BOTH
        }
      TransactionType.EXPENSE.name ->
        categories.filter { cat ->
          cat.type == CategoryType.EXPENSE ||
            cat.type == CategoryType.BOTH
        }
      "TRANSFER" -> emptyList()
      else -> categories.filter { cat -> cat.key == "Loans" || cat.key == "Installments" || cat.key == "Other" }
    }

  val typeColor = resolveDialogTypeColor(selectedType)

  val coroutineScope = rememberCoroutineScope()

  HesabyarDialog(
    title = if (isEditMode) "ویرایش تراکنش" else "ثبت دستی تراکنش جدید",
    onDismissRequest = onDismiss,
    widthFraction = 0.92f,
    actions = {
      OutlinedButton(
        onClick = onDismiss,
        modifier = Modifier.weight(1f),
        shape = ShapeTokens.Medium
      ) {
        Text("انصراف")
      }

      Button(
        onClick = {
          isSubmitting = true
          coroutineScope.launch {
            try {
              val finalAmountDisplay = amountValue.text.toLongOrNull() ?: 0L
              val finalAmountRial =
                if (isEditMode && !amountModified) {
                  originalAmountRial
                } else {
                  CurrencyFormatter.toRial(finalAmountDisplay)
                }

              // Local snapshot so the null-check below smart-casts cleanly.
              val accountId = sourceAccountId
              if (accountId == null) {
                showToast(context, context.getString(io.github.mojri.hesabyar.R.string.select_source_account))
                isSubmitting = false
                return@launch
              }

              if (selectedType == "TRANSFER" && destinationAccountId == null) {
                showToast(context, context.getString(io.github.mojri.hesabyar.R.string.select_destination_account))
                isSubmitting = false
                return@launch
              }

              if (selectedType == "TRANSFER" &&
                destinationAccountId != null &&
                destinationAccountId == accountId
              ) {
                showToast(
                  context,
                  context.getString(io.github.mojri.hesabyar.R.string.source_dest_accounts_cannot_be_same)
                )
                isSubmitting = false
                return@launch
              }

              val request =
                SubmitManualTransactionUseCase.SubmitManualTransactionRequest(
                  amountDisplay = finalAmountDisplay,
                  selectedType = selectedType,
                  selectedCategoryId = selectedCategoryId,
                  descriptionText = descriptionText,
                  personName = personNameText,
                  title = titleText,
                  daysFromNowText = daysFromNowText,
                  amountRial = finalAmountRial,
                  customDate = customDate,
                  categories = categories,
                  transactionToEdit = transactionToEdit,
                  accountId = accountId,
                  destinationAccountId = destinationAccountId
                )

              val submitResult = onSubmit(request)

              if (submitResult.success) {
                onDismiss()
              } else {
                submitResult.errorMessage?.let { showToast(context, it) }
              }
            } finally {
              isSubmitting = false
            }
          }
        },
        modifier = Modifier.weight(1f),
        shape = ShapeTokens.Medium,
        colors = ButtonDefaults.buttonColors(containerColor = typeColor),
        enabled = !isSubmitting,
      ) {
        Text(
          if (isEditMode) "ذخیره تغییرات" else "ثبت تراکنش",
          color = MaterialTheme.colorScheme.onPrimary
        )
      }
    }
  ) {
    TransactionTypeSelector(
      selectedType = selectedType,
      isEditMode = isEditMode,
      categories = categories,
      onTypeSelected = { type, categoryId ->
        selectedType = type
        selectedCategoryId = categoryId
      }
    )

    if (accounts.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
        Text(
          text = "حساب:",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AccountSelector(
          accounts = accounts,
          selectedAccountId = sourceAccountId,
          onAccountSelected = {
            sourceAccountId = it
            onAccountSelected(it)
          },
          includeAllAccountsOption = false
        )
      }
    }

    if (selectedType == "TRANSFER") {
      DestinationAccountSelector(
        accounts = accounts,
        sourceAccountId = sourceAccountId ?: 0L,
        selectedDestinationAccountId = destinationAccountId,
        onDestinationAccountSelected = { destinationAccountId = it }
      )
    }

    TransactionAmountInput(
      amountValue = amountValue,
      typeColor = typeColor,
      onAmountChanged = { value, fromQuickFill ->
        if (isEditMode && fromQuickFill) amountModified = true
        amountValue = value
      }
    )

    if (selectedType == "EXPENSE" || selectedType == "INCOME") {
      TransactionCategorySelector(
        filteredCategories = filteredCategories,
        selectedCategoryId = selectedCategoryId,
        onCategorySelected = { selectedCategoryId = it }
      )
    }

    if (selectedType == "LOAN_DEBTOR" || selectedType == "LOAN_CREDITOR") {
      LoanPersonNameInput(
        personName = personNameText,
        onPersonNameChanged = { personNameText = it }
      )
    }

    if (selectedType == "INSTALLMENT") {
      InstallmentFormFields(
        title = titleText,
        daysFromNow = daysFromNowText,
        onTitleChanged = { titleText = it },
        onDaysChanged = { daysFromNowText = it }
      )
    }

    JalaliDateTimePicker(
      initialTimestamp = customDate,
      onTimestampChanged = { customDate = it }
    )

    TransactionDescriptionInput(
      description = descriptionText,
      onDescriptionChanged = { descriptionText = it }
    )
  }
}

@Composable
private fun resolveDialogTypeColor(selectedType: String) =
  when (selectedType) {
    "INCOME", "LOAN_DEBTOR" -> MaterialTheme.colorScheme.primary
    "EXPENSE", "LOAN_CREDITOR" -> MaterialTheme.colorScheme.error
    "TRANSFER" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.tertiary
  }

private fun showToast(
  context: android.content.Context,
  message: String
) {
  android.widget.Toast
    .makeText(context, message, android.widget.Toast.LENGTH_SHORT)
    .show()
}
