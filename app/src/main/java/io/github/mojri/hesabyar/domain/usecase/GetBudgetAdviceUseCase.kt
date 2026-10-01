package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.BudgetAdvisor
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction

class GetBudgetAdviceUseCase {
  suspend fun getAdvice(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
    config: AiProviderConfig?,
    bankLoans: List<BankLoan> = emptyList()
  ): String = BudgetAdvisor.getBudgetAdvice(transactions, loans, installments, categories, config, bankLoans)
}
