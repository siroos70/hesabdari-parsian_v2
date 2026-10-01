package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.core.MathUtils
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.utils.LoansCategoryExclusion
import java.math.BigInteger
import kotlin.math.max

// Kotlin fallback metrics for the budget advisor.
//
// BudgetAdvisor prefers the Rust core and calls these only when Rust is
// unavailable. The thresholds mirror the Rust scoring so both paths agree.

/** Debt ratio, goal prediction, and financial health score computed in Kotlin. */
internal object LocalBudgetMetrics {
  private const val MONTHS_PER_YEAR = 12L
  private const val MAX_DEBT_TO_INCOME_RATIO = 1.0

  private const val BASELINE_WINDOW_DAYS = 90L
  private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
  private const val DAYS_PER_MONTH = 30L

  private const val BASE_SCORE = 50
  private const val SCORE_MIN = 0
  private const val SCORE_MAX = 100

  private const val STRONG_SAVINGS_RATE = 0.3
  private const val GOOD_SAVINGS_RATE = 0.2
  private const val FAIR_SAVINGS_RATE = 0.1
  private const val STRONG_SAVINGS_POINTS = 25
  private const val GOOD_SAVINGS_POINTS = 20
  private const val FAIR_SAVINGS_POINTS = 10
  private const val SAVINGS_DEFICIT_POINTS = -15

  private const val VERY_LOW_DEBT_RATIO = 0.1
  private const val LOW_DEBT_RATIO = 0.2
  private const val MODERATE_DEBT_RATIO = 0.3
  private const val HIGH_DEBT_RATIO = 0.4
  private const val VERY_LOW_DEBT_POINTS = 15
  private const val LOW_DEBT_POINTS = 10
  private const val MODERATE_DEBT_POINTS = 5
  private const val HIGH_DEBT_PENALTY_POINTS = -10

  private const val DIVERSE_CATEGORY_COUNT = 5
  private const val VARIED_CATEGORY_COUNT = 3
  private const val DIVERSE_CATEGORY_POINTS = 10
  private const val VARIED_CATEGORY_POINTS = 5

  /** Monthly debt-to-income ratio. Income of zero yields the capped ratio. */
  fun debtToIncomeRatio(
    loans: List<Loan>,
    installments: List<Installment>,
    monthlyIncome: Long
  ): Double {
    val unpaidInstallmentsSum =
      installments
        .filter { !it.isPaid }
        .fold(0L) { acc, inst -> MathUtils.saturatingAdd(acc, inst.amount) }
    val unpaidCreditorLoansSum =
      loans
        .filter { !it.isSettled && it.type == LoanType.CREDITOR }
        .fold(0L) { acc, loan ->
          MathUtils.saturatingAdd(acc, loan.remainingAmount / MONTHS_PER_YEAR)
        }
    val monthlyDebtPayments = MathUtils.saturatingAdd(unpaidInstallmentsSum, unpaidCreditorLoansSum)
    return when {
      monthlyIncome <= 0 && monthlyDebtPayments > 0 -> MAX_DEBT_TO_INCOME_RATIO
      monthlyIncome <= 0 -> 0.0
      else -> monthlyDebtPayments.toDouble() / monthlyIncome.toDouble()
    }
  }

  /** Whole months to reach [goalAmount]. Negative when no monthly saving exists. */
  fun timeToGoal(
    currentSavings: Long,
    monthlySavings: Long,
    goalAmount: Long
  ): Int {
    if (monthlySavings <= 0) return -1
    // Saturate like the Rust predict_time_to_goal's saturating_sub: an extreme
    // goal/current pair wraps in plain Long subtraction, Rust clamps instead.
    val remaining =
      when {
        currentSavings < 0 && goalAmount > Long.MAX_VALUE + currentSavings -> Long.MAX_VALUE
        currentSavings > 0 && goalAmount < Long.MIN_VALUE + currentSavings -> Long.MIN_VALUE
        else -> goalAmount - currentSavings
      }
    return if (remaining > 0) {
      val months = (remaining - 1) / monthlySavings + 1L
      months.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    } else {
      0
    }
  }

  /**
   * Financial health score (0-100) for the local fallback path.
   * `nowMs` is injectable so the debt baseline window is deterministic in tests.
   */
  fun financialHealthScore(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    bankLoans: List<BankLoan>,
    excludedCategoryIds: List<Long> = emptyList(),
    nowMs: Long = System.currentTimeMillis(),
  ): Int {
    val filteredTransactions =
      LoansCategoryExclusion.filterTransactions(transactions, excludedCategoryIds)
    // Plan 011 D2 parity with Rust: no included transactions means no data to
    // score, not a zero-budget snapshot of raw rows that are all excluded.
    if (filteredTransactions.isEmpty()) return SCORE_MIN
    var totalIncome = 0L
    var totalExpense = 0L
    for (tx in filteredTransactions) {
      when (tx.type) {
        TransactionType.INCOME -> totalIncome = MathUtils.saturatingAdd(totalIncome, tx.amount)
        TransactionType.EXPENSE -> totalExpense = MathUtils.saturatingAdd(totalExpense, tx.amount)
        else -> Unit
      }
    }
    val balance = MathUtils.saturatingSub(totalIncome, totalExpense)

    val debtRatio =
      BudgetAdvisor.calculateDebtToIncomeRatio(
        loans,
        installments,
        monthlyIncomeBaseline(filteredTransactions, nowMs),
        bankLoans
      )

    val score =
      BASE_SCORE +
        savingsScore(totalIncome, balance) +
        debtScore(debtRatio) +
        diversificationScore(filteredTransactions.expenseCategoryCount())
    return score.coerceIn(SCORE_MIN, SCORE_MAX)
  }

  /** Savings-rate contribution to the health score. */
  private fun savingsScore(
    totalIncome: Long,
    balance: Long
  ): Int {
    if (totalIncome <= 0) return 0
    val savingsRate = balance.toDouble() / totalIncome.toDouble()
    return when {
      savingsRate >= STRONG_SAVINGS_RATE -> STRONG_SAVINGS_POINTS
      savingsRate >= GOOD_SAVINGS_RATE -> GOOD_SAVINGS_POINTS
      savingsRate >= FAIR_SAVINGS_RATE -> FAIR_SAVINGS_POINTS
      savingsRate >= 0 -> 0
      else -> SAVINGS_DEFICIT_POINTS
    }
  }

  /** Debt-to-income contribution to the health score. */
  private fun debtScore(debtRatio: Double): Int =
    when {
      debtRatio <= VERY_LOW_DEBT_RATIO -> VERY_LOW_DEBT_POINTS
      debtRatio <= LOW_DEBT_RATIO -> LOW_DEBT_POINTS
      debtRatio <= MODERATE_DEBT_RATIO -> MODERATE_DEBT_POINTS
      debtRatio <= HIGH_DEBT_RATIO -> 0
      else -> HIGH_DEBT_PENALTY_POINTS
    }

  /** Distinct expense categories in the list. */
  private fun List<Transaction>.expenseCategoryCount(): Int =
    filter { it.type == TransactionType.EXPENSE }.map { it.categoryId }.distinct().size

  /** Category-diversification contribution to the health score. */
  private fun diversificationScore(expenseCategories: Int): Int =
    when {
      expenseCategories >= DIVERSE_CATEGORY_COUNT -> DIVERSE_CATEGORY_POINTS
      expenseCategories >= VARIED_CATEGORY_COUNT -> VARIED_CATEGORY_POINTS
      else -> 0
    }

  /**
   * Trailing-90-day income baseline, mirroring the Rust core's
   * `monthly_income_baseline` so the debt-to-income ratio uses current income.
   * [nowMs] is injectable for deterministic, non-flaky tests.
   */
  fun monthlyIncomeBaseline(
    transactions: List<Transaction>,
    nowMs: Long = System.currentTimeMillis()
  ): Long {
    val windowStart = nowMs - BASELINE_WINDOW_DAYS * MILLIS_PER_DAY
    val recent =
      transactions.filter {
        it.type == TransactionType.INCOME && it.date >= windowStart && it.date <= nowMs
      }
    if (recent.isEmpty()) return 0L
    val oldest = recent.minOf { it.date }
    // Ceiling division into whole days, minimum 1 — matches Rust's integer ceiling
    // path and keeps all arithmetic in Long (no f64 precision loss above 2^53).
    val days = max(1L, (nowMs - oldest + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY)
    val normalizationDays = days.coerceAtLeast(DAYS_PER_MONTH)
    // Accumulate in BigInteger to avoid Long wrap-around in sumOf. Saturate
    // the sum at Long.MAX_VALUE to match Rust's saturating_add. Because the
    // sum is capped, the baseline never exceeds Long.MAX_VALUE, so .toLong()
    // is safe.
    val sumBig =
      recent.fold(BigInteger.ZERO) { acc, tx ->
        val next = acc.add(BigInteger.valueOf(tx.amount))
        when {
          next > BigInteger.valueOf(Long.MAX_VALUE) -> BigInteger.valueOf(Long.MAX_VALUE)
          next < BigInteger.valueOf(Long.MIN_VALUE) -> BigInteger.valueOf(Long.MIN_VALUE)
          else -> next
        }
      }
    return sumBig
      .multiply(BigInteger.valueOf(DAYS_PER_MONTH))
      .divide(BigInteger.valueOf(normalizationDays))
      .toLong()
  }
}
