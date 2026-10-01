package io.github.mojri.hesabyar.ui

import io.github.mojri.hesabyar.api.ParsedResult
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.rust.BankLoanSummary

data class DashboardData(
  val currentBalance: Long = 0L,
  val monthlyExpenses: Long = 0L,
  val monthlyIncome: Long = 0L,
  val debtorsTotal: Long = 0L,
  val creditorsTotal: Long = 0L,
  val upcomingInstallments: List<Installment> = emptyList(),
  val savingsRate: Double = 0.0,
  val debtToIncomeRatio: Double = 0.0,
  val bankLoans: List<BankLoanSummary> = emptyList(),
  val bankLoansTotal: Long = 0L,
  val accounts: List<AccountDashboardSummary> = emptyList(),
  val totalNetWorth: Long = 0L
)

data class AccountDashboardSummary(
  val accountId: Long,
  val accountName: String,
  val accountType: AccountType,
  val balance: Long,
  val monthlyIncome: Long,
  val monthlyExpenses: Long,
  val accountColor: Long = 0xFF4CAF50L,
  /** Month-over-month net change ratio. 0.0 when previous month net is negligible. */
  val monthlyDelta: Double = 0.0
)

sealed interface ParserUIState {
  data object Idle : ParserUIState

  data object Loading : ParserUIState

  data class Success(
    val result: ParsedResult
  ) : ParserUIState

  data class Error(
    val message: String
  ) : ParserUIState

  data class Confirming(
    val result: ParsedResult
  ) : ParserUIState
}

sealed interface UiResult<out T> {
  data object Idle : UiResult<Nothing>

  data object Loading : UiResult<Nothing>

  data class Success<T>(
    val data: T
  ) : UiResult<T>

  data class Error(
    val message: String
  ) : UiResult<Nothing>
}

data class MonthlyData(
  val jalaliYear: Int,
  val jalaliMonth: Int,
  val label: String,
  val income: Long,
  val expense: Long
)

data class CategoryBreakdown(
  val categoryId: Long,
  val categoryName: String,
  val color: Long,
  val total: Long,
  val percentage: Float
)

data class DebtSummary(
  val personName: String,
  val originalAmount: Long,
  val remainingAmount: Long,
  val type: String,
  val progress: Float
)

data class InstallmentProgress(
  val id: Long,
  val title: String,
  val amount: Long,
  val dueDate: Long,
  val isPaid: Boolean
)

data class AnalyticsData(
  val monthlySpending: List<MonthlyData> = emptyList(),
  val monthlyIncome: List<MonthlyData> = emptyList(),
  val categoryBreakdown: List<CategoryBreakdown> = emptyList(),
  val accountBreakdown: List<CategoryBreakdown> = emptyList(),
  val debtors: List<DebtSummary> = emptyList(),
  val creditors: List<DebtSummary> = emptyList(),
  val activeLoans: List<Loan> = emptyList(),
  val installmentProgress: List<InstallmentProgress> = emptyList(),
  val totalInstallments: Int = 0,
  val paidInstallments: Int = 0,
  val totalDebt: Long = 0L,
  val totalCredit: Long = 0L,
  val bankLoans: List<BankLoanSummary> = emptyList(),
  val bankLoansTotalDebt: Long = 0L,
  val accounts: List<AccountAnalytics> = emptyList()
)

data class AccountAnalytics(
  val accountId: Long,
  val accountName: String,
  val monthlyData: List<MonthlyData> = emptyList(),
  val categoryBreakdown: List<CategoryBreakdown> = emptyList()
)

sealed interface BackupOperationState {
  object Idle : BackupOperationState

  object Importing : BackupOperationState

  object Exporting : BackupOperationState

  data class ImportSuccess(
    val message: String
  ) : BackupOperationState

  data class ExportSuccess(
    val message: String
  ) : BackupOperationState

  data class Error(
    val message: String
  ) : BackupOperationState

  data class ValidationFailed(
    val errors: List<String>
  ) : BackupOperationState
}

sealed interface ExportState {
  object Idle : ExportState

  object Exporting : ExportState

  data class Success(
    val summary: String
  ) : ExportState

  data class Error(
    val message: String
  ) : ExportState
}
