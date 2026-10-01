package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class SubmitManualTransactionUseCase(
  private val manageTransaction: ManageTransactionUseCase,
  private val manageLoan: ManageLoanUseCase,
  private val manageInstallment: ManageInstallmentUseCase
) {
  companion object {
    private const val MAX_SAFE_DISPLAY_AMOUNT = Long.MAX_VALUE / 10
  }

  sealed class ValidationResult {
    data object Valid : ValidationResult()

    data class Error(
      val message: String
    ) : ValidationResult()
  }

  data class SubmitResult(
    val success: Boolean,
    val errorMessage: String? = null
  )

  data class SubmitManualTransactionRequest(
    val amountDisplay: Long,
    val selectedType: String,
    val selectedCategoryId: Long,
    val descriptionText: String,
    val personName: String,
    val title: String,
    val daysFromNowText: String,
    val amountRial: Long,
    val customDate: Long,
    val categories: List<Category>,
    val transactionToEdit: Transaction? = null,
    val accountId: Long = io.github.mojri.hesabyar.data.DEFAULT_ACCOUNT_ID,
    val destinationAccountId: Long? = null
  )

  fun validate(request: SubmitManualTransactionRequest): ValidationResult {
    val error =
      amountError(request)
        ?: categoryError(request)
        ?: loanError(request)
        ?: installmentError(request)
        ?: transferError(request)
    return if (error != null) ValidationResult.Error(error) else ValidationResult.Valid
  }

  private fun installmentError(request: SubmitManualTransactionRequest): String? {
    if (request.selectedType != "INSTALLMENT") return null
    return when {
      request.title.isBlank() -> "لطفا عنوان قسط را وارد کنید"
      request.daysFromNowText.isNotBlank() && request.daysFromNowText.toLongOrNull() == null ->
        "لطفا تعداد روزها را به صورت عدد وارد کنید"
      else -> null
    }
  }

  private fun amountError(request: SubmitManualTransactionRequest): String? {
    val invalid =
      request.amountDisplay <= 0L ||
        request.amountRial <= 0L ||
        request.amountDisplay > MAX_SAFE_DISPLAY_AMOUNT
    return if (invalid) "لطفا مبلغ معتبر و بزرگتر از صفر وارد کنید" else null
  }

  private fun categoryError(request: SubmitManualTransactionRequest): String? =
    if ((request.selectedType == "INCOME" || request.selectedType == "EXPENSE") && request.selectedCategoryId == 0L) {
      "لطفا دسته‌بندی را انتخاب کنید"
    } else {
      null
    }

  private fun loanError(request: SubmitManualTransactionRequest): String? =
    if ((request.selectedType == "LOAN_DEBTOR" || request.selectedType == "LOAN_CREDITOR") &&
      request.personName.isBlank()
    ) {
      "لطفا نام شخص مربوطه را وارد کنید"
    } else {
      null
    }

  private fun transferError(request: SubmitManualTransactionRequest): String? =
    if (request.selectedType == "TRANSFER") {
      when {
        request.destinationAccountId == null -> "لطفا حساب مقصد را انتخاب کنید"
        request.destinationAccountId == request.accountId -> "حساب مبدا و مقصد نمی‌توانند یکسان باشند"
        else -> null
      }
    } else {
      null
    }

  suspend fun submit(request: SubmitManualTransactionRequest): SubmitResult {
    val validationResult = validate(request)
    if (validationResult is ValidationResult.Error) {
      return SubmitResult(success = false, errorMessage = validationResult.message)
    }

    return when (request.selectedType) {
      "INCOME", "EXPENSE" ->
        submitTransaction(
          request.selectedType,
          request.amountRial,
          request.selectedCategoryId,
          request.descriptionText,
          request.customDate,
          request.transactionToEdit,
          request.categories,
          request.accountId
        )

      "LOAN_DEBTOR", "LOAN_CREDITOR" ->
        submitLoan(
          request.selectedType,
          request.amountRial,
          request.personName,
          request.descriptionText,
          request.customDate
        )

      "INSTALLMENT" ->
        submitInstallment(
          request.amountRial,
          request.title,
          request.descriptionText,
          request.daysFromNowText,
          request.customDate
        )

      "TRANSFER" -> {
        val destAccountId = request.destinationAccountId
        if (destAccountId == null) {
          SubmitResult(success = false, errorMessage = "حساب مقصد مشخص نشده است")
        } else {
          submitTransfer(
            request.amountRial,
            request.descriptionText,
            request.customDate,
            request.accountId,
            destAccountId,
            request.transactionToEdit
          )
        }
      }

      else -> SubmitResult(success = false, errorMessage = "نوع تراکنش نامعتبر است")
    }
  }

  private suspend fun submitTransaction(
    selectedType: String,
    finalAmountRial: Long,
    selectedCategoryId: Long,
    descriptionText: String,
    customDate: Long,
    transactionToEdit: Transaction?,
    categories: List<Category>,
    accountId: Long = io.github.mojri.hesabyar.data.DEFAULT_ACCOUNT_ID
  ): SubmitResult {
    val selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name ?: "سایر"
    val desc = descriptionText.trim().ifEmpty { selectedCategoryName }
    if (transactionToEdit != null) {
      // Use the accountId carried by the request. The dialog seeds it from
      // the transaction being edited, so a plain edit keeps the original
      // account while an explicit account change moves the transaction.
      val updated =
        transactionToEdit.copy(
          type = TransactionType.valueOf(selectedType),
          categoryId = selectedCategoryId,
          amount = finalAmountRial,
          description = desc,
          date = customDate,
          accountId = accountId
        )
      withContext(NonCancellable) { manageTransaction.updateTransaction(updated) }
    } else {
      withContext(NonCancellable) {
        manageTransaction.addTransaction(
          type = TransactionType.valueOf(selectedType),
          categoryId = selectedCategoryId,
          amount = finalAmountRial,
          description = desc,
          customDate = customDate,
          accountId = accountId
        )
      }
    }
    return SubmitResult(success = true)
  }

  private suspend fun submitLoan(
    selectedType: String,
    finalAmountRial: Long,
    personName: String,
    descriptionText: String,
    customDate: Long
  ): SubmitResult {
    val defaultDesc = if (selectedType == "LOAN_DEBTOR") "قرض دادن به $personName" else "قرض گرفتن از $personName"
    val desc = descriptionText.trim().ifEmpty { defaultDesc }
    withContext(NonCancellable) {
      manageLoan.addLoan(
        personName = personName,
        type = if (selectedType == "LOAN_DEBTOR") LoanType.DEBTOR else LoanType.CREDITOR,
        amount = finalAmountRial,
        description = desc,
        customDate = customDate
      )
    }
    return SubmitResult(success = true)
  }

  private suspend fun submitInstallment(
    finalAmountRial: Long,
    title: String,
    descriptionText: String,
    daysFromNowText: String,
    customDate: Long
  ): SubmitResult {
    val millisPerDay = 24L * 60 * 60 * 1000
    val daysOffset = daysFromNowText.toLongOrNull() ?: 0L
    val dueDate =
      if (daysOffset > 0) {
        System.currentTimeMillis() + daysOffset * millisPerDay
      } else {
        customDate
      }
    withContext(NonCancellable) {
      manageInstallment.addInstallment(
        title = title,
        amount = finalAmountRial,
        dueDate = dueDate,
        reminderEnabled = true,
        notes = descriptionText.trim()
      )
    }
    return SubmitResult(success = true)
  }

  private suspend fun submitTransfer(
    finalAmountRial: Long,
    descriptionText: String,
    customDate: Long,
    sourceAccountId: Long,
    destinationAccountId: Long,
    transactionToEdit: Transaction? = null
  ): SubmitResult {
    val desc = descriptionText.trim().ifEmpty { "انتقال وجه بین حساب‌ها" }
    withContext(NonCancellable) {
      if (transactionToEdit != null) {
        // Update existing transfer — preserve the original id and explicitly
        // pin the type (matching submitTransaction) so an edit can never
        // silently keep a non-TRANSFER type.
        manageTransaction.updateTransaction(
          transactionToEdit.copy(
            type = TransactionType.TRANSFER,
            amount = finalAmountRial,
            description = desc,
            date = customDate,
            accountId = sourceAccountId,
            destinationAccountId = destinationAccountId
          )
        )
      } else {
        manageTransaction.addTransaction(
          type = TransactionType.TRANSFER,
          categoryId = 0L,
          amount = finalAmountRial,
          description = desc,
          customDate = customDate,
          accountId = sourceAccountId,
          destinationAccountId = destinationAccountId
        )
      }
    }
    return SubmitResult(success = true)
  }
}
