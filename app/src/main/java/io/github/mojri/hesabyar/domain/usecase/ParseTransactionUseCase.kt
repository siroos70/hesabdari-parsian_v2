package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.GeminiParser
import io.github.mojri.hesabyar.api.ParsedResult
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType

class ParseTransactionUseCase(
  private val repository: HesabyarRepositoryInterface
) {
  suspend fun parse(
    sentence: String,
    config: AiProviderConfig?
  ): ParsedResult? = GeminiParser.parseSentence(sentence, config)

  suspend fun approveParsedResult(
    result: ParsedResult,
    customDate: Long? = null
  ) {
    val category = repository.getCategoryByKey(result.category)
    val categoryId = category?.id ?: 1L

    when (result.type) {
      "INCOME", "EXPENSE" -> {
        repository.insertTransaction(
          Transaction(
            type = TransactionType.valueOf(result.type),
            categoryId = categoryId,
            amount = result.amount,
            description = result.description,
            personName = result.personName,
            date = customDate ?: System.currentTimeMillis()
          )
        )
      }
      "LOAN_DEBTOR", "LOAN_CREDITOR" -> {
        val loanType = if (result.type == "LOAN_DEBTOR") LoanType.DEBTOR else LoanType.CREDITOR
        repository.insertLoan(
          Loan(
            personName = result.personName ?: "نامشخص",
            type = loanType,
            originalAmount = result.amount,
            remainingAmount = result.amount,
            description = result.description,
            date = customDate ?: System.currentTimeMillis()
          )
        )
      }
      "INSTALLMENT" -> {
        val days = result.daysFromNow ?: 30
        val due = customDate ?: (System.currentTimeMillis() + (days * 24L * 60L * 60L * 1000L))
        repository.insertInstallment(
          Installment(
            title = result.title ?: "قسط دستیار",
            amount = result.amount,
            dueDate = due,
            notes = result.description
          )
        )
      }
    }
  }
}
