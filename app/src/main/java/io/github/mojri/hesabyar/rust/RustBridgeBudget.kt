package io.github.mojri.hesabyar.rust

import io.github.mojri.hesabyar.data.BankLoan

// Budget advisory domain of the RustBridge façade. See RustBridgeCore for the split.

/** Offline advice, forecast, ratio, goal, and health-score calls. */
internal interface RustBridgeBudget : RustBridgeCore {
  /** Offline budget advice text. Empty string when Rust is unavailable. */
  fun getOfflineBudgetAdviceSync(
    transactions: List<Transaction>,
    categories: List<Category>,
    excludedCategoryIds: List<Long> = emptyList()
  ): String = rustCallSync("") { HesabyarCore.getOfflineBudgetAdvice(transactions, categories, excludedCategoryIds) }

  /** Offline forecast text. Empty string when Rust is unavailable. */
  fun getOfflineForecastSync(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    bankLoans: List<BankLoan> = emptyList(),
    excludedCategoryIds: List<Long> = emptyList()
  ): String =
    rustCallSync("") {
      HesabyarCore.getOfflineForecast(
        transactions,
        loans,
        installments,
        RustMappers.mapBankLoans(bankLoans),
        excludedCategoryIds
      )
    }

  /** Monthly debt-to-income ratio. Zero when Rust is unavailable. */
  fun calculateDebtToIncomeRatioSync(
    loans: List<Loan>,
    installments: List<Installment>,
    monthlyIncome: Long,
    bankLoans: List<BankLoan> = emptyList()
  ): Double =
    rustCallSync(0.0) {
      HesabyarCore.calculateDebtToIncomeRatio(
        loans,
        installments,
        monthlyIncome,
        RustMappers.mapBankLoans(bankLoans)
      )
    }

  /** Months needed to reach a savings goal. Zero when Rust is unavailable. */
  fun predictTimeToGoalSync(
    currentSavings: Long,
    monthlySavings: Long,
    goalAmount: Long
  ): Int = rustCallSync(0) { HesabyarCore.predictTimeToGoal(currentSavings, monthlySavings, goalAmount) }

  /** Financial health score (0-100). Zero when Rust is unavailable. */
  fun calculateFinancialHealthScoreSync(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
    bankLoans: List<BankLoan> = emptyList(),
    excludedCategoryIds: List<Long> = emptyList()
  ): Int =
    rustCallSync(0) {
      HesabyarCore.calculateFinancialHealthScore(
        transactions,
        loans,
        installments,
        categories,
        RustMappers.mapBankLoans(bankLoans),
        excludedCategoryIds
      )
    }
}
