package io.github.mojri.hesabyar.rust

import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.BankLoan

// Dashboard and analytics aggregation domain of the RustBridge façade.
// See RustBridgeCore for the split.

/** Aggregation calls behind the dashboard and analytics screens. */
internal interface RustBridgeAnalytics : RustBridgeCore {
  /** Computes the analytics view model. Null when Rust is unavailable or the native computation failed. */
  fun computeAnalyticsSync(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
    bankLoans: List<BankLoan> = emptyList(),
    accounts: List<AccountEntity> = emptyList(),
    accountId: Long? = null,
    includeArchived: Boolean = false,
    excludedCategoryIds: List<Long> = emptyList(),
  ): AnalyticsData? =
    rustCallSync(null) {
      HesabyarCore.computeAnalytics(
        transactions = transactions,
        loans = loans,
        installments = installments,
        categories = categories,
        bankLoans = RustMappers.mapBankLoans(bankLoans),
        accounts = RustMappers.mapAccounts(accounts),
        accountId = accountId,
        includeArchived = includeArchived,
        excludedCategoryIds = excludedCategoryIds,
      )
    }

  /** Computes the dashboard view model. Null when Rust is unavailable or the native computation failed. */
  fun computeDashboardDataSync(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    bankLoans: List<BankLoan> = emptyList(),
    accounts: List<AccountEntity> = emptyList(),
    accountId: Long? = null,
    includeArchived: Boolean = false,
    nowMs: Long = System.currentTimeMillis(),
    excludedCategoryIds: List<Long> = emptyList(),
  ): DashboardData? =
    rustCallSync(null) {
      HesabyarCore.computeDashboardData(
        transactions = transactions,
        loans = loans,
        installments = installments,
        bankLoans = RustMappers.mapBankLoans(bankLoans),
        accounts = RustMappers.mapAccounts(accounts),
        accountId = accountId,
        includeArchived = includeArchived,
        nowMs = nowMs,
        excludedCategoryIds = excludedCategoryIds,
      )
    }
}
