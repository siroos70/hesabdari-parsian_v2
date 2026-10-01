package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.utils.ArchivedTransactionFilter
import io.github.mojri.hesabyar.domain.utils.LoansCategoryExclusion
import io.github.mojri.hesabyar.rust.BankLoanSummary
import io.github.mojri.hesabyar.ui.AccountDashboardSummary
import io.github.mojri.hesabyar.ui.DashboardData
import kotlinx.coroutines.flow.Flow

class GetDashboardDataUseCase(
  private val repository: HesabyarRepositoryInterface
) {
  val transactions: Flow<List<Transaction>> = repository.allTransactions
  val loans: Flow<List<Loan>> = repository.allLoans
  val installments: Flow<List<Installment>> = repository.allInstallments
  val categories: Flow<List<Category>> = repository.allCategories
  val bankLoans: Flow<List<BankLoan>> = repository.allBankLoans
  val accounts: Flow<List<AccountEntity>> = repository.allAccounts

  fun computeDashboardData(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    bankLoans: List<BankLoan> = emptyList(),
    accounts: List<AccountEntity> = emptyList(),
    accountId: Long? = null,
    includeArchived: Boolean = false,
    nowMs: Long = System.currentTimeMillis(),
    excludedCategoryIds: List<Long> = emptyList(),
  ): DashboardData {
    val rustResult =
      io.github.mojri.hesabyar.rust.RustBridge.computeDashboardDataSync(
        io.github.mojri.hesabyar.rust.RustMappers
          .mapTransactions(transactions),
        io.github.mojri.hesabyar.rust.RustMappers
          .mapLoans(loans),
        io.github.mojri.hesabyar.rust.RustMappers
          .mapInstallments(installments),
        bankLoans,
        accounts,
        accountId,
        includeArchived,
        nowMs,
        excludedCategoryIds,
      )

    // Use the Rust result unless it failed (null) or came back as an all-zero
    // placeholder while real data exists. In those cases fall back to a local
    // computation so the UI never shows misleading blank zeros.
    val hasData =
      transactions.isNotEmpty() ||
        loans.isNotEmpty() ||
        installments.isNotEmpty() ||
        bankLoans.isNotEmpty() ||
        accounts.isNotEmpty()
    if (rustResult != null && !(hasData && rustResult.isBlank())) {
      return io.github.mojri.hesabyar.rust.RustMappers
        .mapDashboardData(rustResult, installments, accounts)
    }

    // Kotlin fallback when Rust FFI is unavailable, panicked, or returned
    // empty/invalid data. Computed directly from the local DB lists.
    return computeFallbackDashboardData(
      transactions,
      loans,
      installments,
      bankLoans,
      accounts,
      now = nowMs,
      includeArchived = includeArchived,
      accountId = accountId,
      excludedCategoryIds = excludedCategoryIds,
    )
  }

  /** True when every field is at its zero/default, i.e. the Rust result is a
   *  blank placeholder rather than a real computation. */
  private fun io.github.mojri.hesabyar.rust.DashboardData.isBlank(): Boolean =
    currentBalance == 0L &&
      monthlyExpenses == 0L &&
      monthlyIncome == 0L &&
      debtorsTotal == 0L &&
      creditorsTotal == 0L &&
      savingsRate == 0.0 &&
      debtToIncomeRatio == 0.0 &&
      totalNetWorth == 0L

  companion object {
    /** Kotlin-only dashboard computation.  Extracted so unit tests can verify
     *  the fallback logic without requiring the Rust native library. */
    internal fun computeFallbackDashboardData(
      transactions: List<Transaction>,
      loans: List<Loan>,
      installments: List<Installment>,
      bankLoans: List<BankLoan> = emptyList(),
      accounts: List<AccountEntity> = emptyList(),
      now: Long = System.currentTimeMillis(),
      includeArchived: Boolean = false,
      accountId: Long? = null,
      excludedCategoryIds: List<Long> = emptyList(),
    ): DashboardData {
      val effectiveTransactions =
        filterArchivedTransactions(transactions, accounts, includeArchived).let { txs ->
          if (accountId != null) {
            txs.filter { it.accountId == accountId || it.destinationAccountId == accountId }
          } else {
            txs
          }
        }

      // Current Jalali month boundaries in UTC, half-open [start, endExclusive),
      // matching the Rust core's compute_dashboard_data (which interprets
      // timestamps in UTC). Centralized in JalaliCalendarHelper so the fallback
      // and Rust paths assign transactions/installments to the same Jalali month.
      val (jalaliMonthStart, jalaliMonthEndExclusive) =
        io.github.mojri.hesabyar.ui.JalaliCalendarHelper
          .getUtcJalaliMonthBoundaries(now)

      val deltas = dashboardDeltas(effectiveTransactions, accountId)
      val monthlyAggregates =
        monthlyAggregates(
          effectiveTransactions,
          deltas,
          jalaliMonthStart,
          jalaliMonthEndExclusive,
          excludedCategoryIds
        )
      val monthlyIncome = monthlyAggregates.income
      val monthlyExpenses = monthlyAggregates.expenses

      val unsettledLoans = loans.filter { !it.isSettled }
      val debtors = unsettledLoans.filter { it.type == LoanType.DEBTOR }.sumOf { it.remainingAmount }
      val creditors = unsettledLoans.filter { it.type == LoanType.CREDITOR }.sumOf { it.remainingAmount }

      val initialBalanceSum = initialBalanceSum(accounts, includeArchived, accountId)

      // currentBalance from all effective transactions (lifetime), not just the filtered month.
      val currentBalance = deltas.sumOf { it.balanceDelta } + initialBalanceSum

      val debtToIncome =
        debtToIncomeRatio(
          installments,
          unsettledLoans,
          monthlyIncome,
          jalaliMonthStart,
          jalaliMonthEndExclusive
        )

      val upcomingIns = installments.filter { !it.isPaid }.sortedBy { it.dueDate }

      return DashboardData(
        currentBalance = currentBalance,
        monthlyExpenses = monthlyExpenses,
        monthlyIncome = monthlyIncome,
        debtorsTotal = debtors,
        creditorsTotal = creditors,
        upcomingInstallments = upcomingIns,
        savingsRate = monthlyAggregates.savingsRate,
        debtToIncomeRatio = debtToIncome,
        bankLoans = toBankLoanSummaries(bankLoans, installments),
        bankLoansTotal = bankLoans.filter { !it.isSettled }.sumOf { it.totalRepayableAmount },
        accounts =
          computeAccountSummaries(
            accounts,
            effectiveTransactions,
            jalaliMonthStart,
            jalaliMonthEndExclusive,
            excludedCategoryIds
          ),
        totalNetWorth = currentBalance
      )
    }

    private fun toBankLoanSummaries(
      bankLoans: List<BankLoan>,
      installments: List<Installment>
    ): List<BankLoanSummary> =
      bankLoans
        .filter { !it.isSettled }
        .map { loan ->
          val paidAmount = installments.filter { it.bankLoanId == loan.id && it.isPaid }.sumOf { it.amount }
          BankLoanSummary(
            bankName = loan.bankName,
            loanName = loan.loanName,
            receivedAmount = loan.receivedAmount,
            totalRepayableAmount = loan.totalRepayableAmount,
            totalInterest = loan.totalInterest,
            numberOfInstallments = loan.numberOfInstallments,
            isSettled = loan.isSettled,
            remainingDebt = if (loan.isSettled) 0 else (loan.totalRepayableAmount - paidAmount).coerceAtLeast(0L)
          )
        }

    /** Excludes transactions whose source or destination account is archived
     *  unless [includeArchived] is true. Delegated to [ArchivedTransactionFilter]. */
    internal fun filterArchivedTransactions(
      transactions: List<Transaction>,
      accounts: List<AccountEntity>,
      includeArchived: Boolean,
    ): List<Transaction> = ArchivedTransactionFilter.filter(transactions, accounts, includeArchived)

    /** Sum of initial balances for the selected account, or all accounts when
     *  [accountId] is null, applying the same archive gate as the Rust core:
     *  when [includeArchived] is false, an archived account's opening balance —
     *  just like its transactions — is excluded. This keeps the fallback from
     *  leaking an archived account's opening balance into currentBalance. */
    private fun initialBalanceSum(
      accounts: List<AccountEntity>,
      includeArchived: Boolean,
      accountId: Long?,
    ): Long =
      if (accountId != null) {
        accounts
          .firstOrNull { it.id == accountId && (includeArchived || !it.isArchived) }
          ?.initialBalance ?: 0L
      } else {
        accounts
          .filter { includeArchived || !it.isArchived }
          .sumOf { it.initialBalance }
      }

    /** Noise threshold for previous-month net (Rial). When abs(prevNet) is
     *  below this, the delta is set to 0.0 to avoid misleading percentages. */
    private const val DELTA_PREV_NET_THRESHOLD = 1_000L
    private const val MONTHS_PER_YEAR = 12L
    private const val MAX_DEBT_TO_INCOME_RATIO = 1.0

    /** Monthly debt obligations mirror the Rust core's calculate_debt_to_income_ratio. */
    private fun debtToIncomeRatio(
      installments: List<Installment>,
      unsettledLoans: List<Loan>,
      monthlyIncome: Long,
      monthStartMs: Long,
      monthEndMs: Long,
    ): Double {
      val installmentDebt =
        installments
          .filter { !it.isPaid && it.dueDate >= monthStartMs && it.dueDate < monthEndMs }
          .sumOf { it.amount }
      val creditorLoanDebt =
        unsettledLoans
          .filter { it.type == LoanType.CREDITOR }
          .sumOf { it.remainingAmount / MONTHS_PER_YEAR }
      val monthlyDebt = installmentDebt + creditorLoanDebt
      return when {
        monthlyIncome > 0 -> monthlyDebt.toDouble() / monthlyIncome
        monthlyDebt > 0 -> MAX_DEBT_TO_INCOME_RATIO
        else -> 0.0
      }
    }

    private fun computeAccountSummaries(
      accounts: List<AccountEntity>,
      transactions: List<Transaction>,
      monthStartMs: Long,
      monthEndMs: Long,
      excludedCategoryIds: List<Long> = emptyList(),
    ): List<AccountDashboardSummary> {
      if (accounts.isEmpty()) return emptyList()

      val (prevMonthStart, prevMonthEnd) =
        io.github.mojri.hesabyar.ui.JalaliCalendarHelper
          .getUtcJalaliPreviousMonthBoundaries(monthStartMs)

      val activeAccounts = accounts.filter { !it.isArchived }
      val activeAccountIds = activeAccounts.map { it.id }.toSet()
      val txsByAccount = indexTransactionsByAccount(transactions, activeAccountIds)
      val eligibleTxs =
        LoansCategoryExclusion.filterTransactions(transactions, excludedCategoryIds).toSet()

      return activeAccounts.map { account ->
        val accountTxs = txsByAccount[account.id] ?: emptyList()
        var balance = account.initialBalance
        var monthlyIncome = 0L
        var monthlyExpenses = 0L
        var prevIncome = 0L
        var prevExpenses = 0L
        for (tx in accountTxs) {
          val inMonth = tx.date >= monthStartMs && tx.date < monthEndMs
          val inPrev = tx.date >= prevMonthStart && tx.date < prevMonthEnd
          val delta = balanceDeltaForAccount(tx, account.id)
          balance += delta.balanceDelta
          val isEligible = tx in eligibleTxs
          if (inMonth && isEligible) {
            monthlyIncome += delta.incomeDelta
            monthlyExpenses += delta.expenseDelta
          }
          if (inPrev && isEligible) {
            prevIncome += delta.incomeDelta
            prevExpenses += delta.expenseDelta
          }
        }

        val currentNet = monthlyIncome - monthlyExpenses
        val prevNet = prevIncome - prevExpenses
        val monthlyDelta =
          if (prevNet == 0L || kotlin.math.abs(prevNet) < DELTA_PREV_NET_THRESHOLD) {
            0.0
          } else {
            (currentNet - prevNet).toDouble() / kotlin.math.abs(prevNet).toDouble()
          }

        AccountDashboardSummary(
          accountId = account.id,
          accountName = account.name,
          accountType = account.type,
          balance = balance,
          monthlyIncome = monthlyIncome,
          monthlyExpenses = monthlyExpenses,
          accountColor = account.color,
          monthlyDelta = monthlyDelta
        )
      }
    }

    private fun indexTransactionsByAccount(
      transactions: List<Transaction>,
      activeAccountIds: Set<Long>
    ): Map<Long, List<Transaction>> {
      val map = mutableMapOf<Long, MutableList<Transaction>>()
      for (tx in transactions) {
        val srcId = tx.accountId
        if (srcId != null && srcId in activeAccountIds) {
          map.getOrPut(srcId) { mutableListOf() }.add(tx)
        }
        val dstId = tx.destinationAccountId
        if (dstId != null && dstId in activeAccountIds && dstId != srcId) {
          map.getOrPut(dstId) { mutableListOf() }.add(tx)
        }
      }
      return map
    }

    /** Per-transaction debit/credit deltas for the dashboard header aggregates.
     *  Transfers are balance-neutral only in the all-accounts view (money stays
     *  within the system). When a single account is selected, a transfer out is
     *  an expense for the source and a transfer in is income for the destination
     *  — matching the per-account summaries (balanceDeltaForAccount). */
    private fun dashboardDeltas(
      transactions: List<Transaction>,
      accountId: Long?,
    ): List<BalanceDelta> =
      transactions.map { tx ->
        if (accountId != null) {
          balanceDeltaForAccount(tx, accountId)
        } else {
          when (tx.type) {
            TransactionType.INCOME -> BalanceDelta(tx.amount, tx.amount, 0L)
            TransactionType.EXPENSE -> BalanceDelta(-tx.amount, 0L, tx.amount)
            else -> BalanceDelta(0L, 0L, 0L)
          }
        }
      }

    /** Monthly income, expenses, and savings rate for the current Jalali month. */
    private fun monthlyAggregates(
      transactions: List<Transaction>,
      deltas: List<BalanceDelta>,
      monthStartMs: Long,
      monthEndMs: Long,
      excludedCategoryIds: List<Long>,
    ): MonthlyAggregates {
      val monthlyDeltas =
        monthlyDeltas(transactions, deltas, monthStartMs, monthEndMs, excludedCategoryIds)
      val income = monthlyDeltas.sumOf { it.incomeDelta }
      val expenses = monthlyDeltas.sumOf { it.expenseDelta }
      val savingsRate =
        if (income > 0) {
          ((income - expenses).toDouble() / income).coerceIn(0.0, 1.0)
        } else {
          0.0
        }
      return MonthlyAggregates(income, expenses, savingsRate)
    }

    private data class MonthlyAggregates(
      val income: Long,
      val expenses: Long,
      val savingsRate: Double,
    )

    /** Deltas of only the transactions inside the current Jalali month window. */
    private fun monthlyDeltas(
      transactions: List<Transaction>,
      deltas: List<BalanceDelta>,
      monthStartMs: Long,
      monthEndMs: Long,
      excludedCategoryIds: List<Long> = emptyList(),
    ): List<BalanceDelta> {
      val eligible =
        LoansCategoryExclusion.filterTransactions(transactions, excludedCategoryIds).toSet()
      return transactions
        .zip(deltas)
        .filter { (tx, _) ->
          tx in eligible && tx.date >= monthStartMs && tx.date < monthEndMs
        }.map { it.second }
    }

    private data class BalanceDelta(
      val balanceDelta: Long,
      val incomeDelta: Long,
      val expenseDelta: Long,
    )

    private fun balanceDeltaForAccount(
      tx: Transaction,
      accountId: Long
    ): BalanceDelta {
      val isSource = tx.accountId == accountId
      val isDest = tx.destinationAccountId == accountId
      return when (tx.type) {
        TransactionType.INCOME ->
          if (isSource) BalanceDelta(tx.amount, tx.amount, 0L) else BalanceDelta(0L, 0L, 0L)
        TransactionType.EXPENSE ->
          if (isSource) BalanceDelta(-tx.amount, 0L, tx.amount) else BalanceDelta(0L, 0L, 0L)
        TransactionType.TRANSFER -> {
          var bal = 0L
          var inc = 0L
          var exp = 0L
          if (isSource) {
            bal -= tx.amount
            exp += tx.amount
          }
          if (isDest) {
            bal += tx.amount
            inc += tx.amount
          }
          BalanceDelta(bal, inc, exp)
        }
        else -> BalanceDelta(0L, 0L, 0L)
      }
    }
  }
}
