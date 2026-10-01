package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.core.MathUtils
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.domain.utils.LoansCategoryExclusion
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper

// Kotlin fallback forecast builder for the offline budget advisor.
// See LocalBudgetAdvice for the advice counterpart.

/** Local, dependency-free baseline forecast used when the Rust core is unavailable. */
internal object LocalBudgetForecast {
  /** Window end offset in Jalali days. 31 makes an exclusive end at the start of
   *  day 31, so obligations through the end of day 30 are included. */
  private const val FORECAST_WINDOW_DAYS = 31
  private const val MONTHS_PER_YEAR = 12L
  private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
  private const val LAST_JALALI_MONTH = 12

  fun forecast(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    bankLoans: List<BankLoan>,
    excludedCategoryIds: List<Long> = emptyList()
  ): String {
    val summary = LocalBudgetAdvice.summarize(transactions, excludedCategoryIds)
    val nowMs = System.currentTimeMillis()
    val windowEndMs = jalaliPlusDaysMs(nowMs, FORECAST_WINDOW_DAYS)
    val upcomingInstallments =
      installments.filter { !it.isPaid && it.dueDate >= nowMs && it.dueDate < windowEndMs }
    val totalUpcoming =
      upcomingInstallments.fold(0L) { total, installment ->
        MathUtils.saturatingAdd(total, installment.amount)
      }
    val activeBankLoans = bankLoans.filter { !it.isSettled }
    val activeLoans = loans.filter { !it.isSettled }
    val totalDebt = computeTotalDebt(activeLoans, activeBankLoans)
    val activeDebtCount = activeLoans.size + activeBankLoans.size

    val unsettledCreditorMonthlyObligation = computeUnsettledCreditorObligation(loans)
    val hasNoData =
      transactions.none { !LoansCategoryExclusion.isExcluded(it, excludedCategoryIds) } &&
        upcomingInstallments.isEmpty() &&
        unsettledCreditorMonthlyObligation == 0L &&
        activeBankLoans.isEmpty()
    if (hasNoData) {
      return "تراکنش یا قسطی برای پیش‌بینی ثبت نشده است. لطفا اطلاعات مالی خود را وارد کنید."
    }

    val projectedBalance =
      MathUtils.saturatingSub(
        MathUtils.saturatingSub(summary.balance, totalUpcoming),
        unsettledCreditorMonthlyObligation
      )
    return renderForecastReport(
      currentBalance = summary.balance,
      upcomingCount = upcomingInstallments.size,
      totalUpcoming = totalUpcoming,
      activeDebtCount = activeDebtCount,
      totalDebt = totalDebt,
      projectedBalance = projectedBalance
    )
  }

  private fun computeTotalDebt(
    activeLoans: List<Loan>,
    activeBankLoans: List<BankLoan>
  ): Long =
    MathUtils.saturatingAdd(
      activeLoans.fold(0L) { total, loan -> MathUtils.saturatingAdd(total, loan.remainingAmount) },
      activeBankLoans.fold(0L) { total, bankLoan ->
        MathUtils.saturatingAdd(total, bankLoan.totalRepayableAmount)
      }
    )

  private fun computeUnsettledCreditorObligation(loans: List<Loan>): Long =
    loans
      .filter { !it.isSettled && it.type == LoanType.CREDITOR }
      .fold(0L) { total, loan ->
        val monthly = loan.remainingAmount / MONTHS_PER_YEAR
        MathUtils.saturatingAdd(total, monthly)
      }

  private fun renderForecastReport(
    currentBalance: Long,
    upcomingCount: Int,
    totalUpcoming: Long,
    activeDebtCount: Int,
    totalDebt: Long,
    projectedBalance: Long
  ): String {
    val sb = StringBuilder()
    sb.appendLine("### 🔮 پیش‌بینی بودجه محلی (آفلاین)")
    sb.appendLine()
    sb.appendLine("**تراز فعلی:** ${LocalBudgetAdvice.formatAmount(currentBalance)}")
    sb.appendLine(
      "**اقساط پیش‌رو:** $upcomingCount مورد به مبلغ " +
        LocalBudgetAdvice.formatAmount(totalUpcoming)
    )
    if (activeDebtCount > 0) {
      sb.appendLine(
        "**بدهی‌های فعال:** $activeDebtCount مورد به مبلغ ${LocalBudgetAdvice.formatAmount(totalDebt)}"
      )
    }
    sb.appendLine()
    sb.appendLine("**تراز پیش‌بینی‌شده (۳۰ روز آینده):** ${LocalBudgetAdvice.formatAmount(projectedBalance)}")
    sb.appendLine()
    sb.appendLine(
      if (projectedBalance < 0) {
        "⚠️ **هشدار هوشمند:** تراز پیش‌بینی منفی است. تعدیل هزینه‌ها یا مدیریت اقساط پیش‌رو ضروری است."
      } else {
        "✅ **وضعیت پایدار:** تراز پیش‌بینی مثبت است. ادامه روند فعلی توصیه می‌شود."
      }
    )
    return sb.toString()
  }

  // Adds [days] Jalali days to the date represented by [fromMs] and returns the
  // resulting day's local-midnight timestamp. Uses JalaliCalendarHelper for all
  // calendar arithmetic (month lengths differ across Jalali months) so the
  // 30-day forecast window tracks the Iranian calendar instead of a fixed
  // millisecond span. Falls back to a millisecond offset if the conversion is
  // unavailable.
  private fun jalaliPlusDaysMs(
    fromMs: Long,
    days: Int
  ): Long {
    val today = JalaliCalendarHelper.gregorianToJalali(fromMs)
    var year = today.year
    var month = today.month
    var day = today.day
    var remaining = days
    while (remaining > 0) {
      val daysInMonth = JalaliCalendarHelper.getDaysInMonth(year, month)
      val daysLeftInMonth = daysInMonth - day
      if (remaining <= daysLeftInMonth) {
        day += remaining
        remaining = 0
      } else {
        remaining -= daysLeftInMonth + 1
        day = 1
        if (month == LAST_JALALI_MONTH) {
          month = 1
          year += 1
        } else {
          month += 1
        }
      }
    }
    return JalaliCalendarHelper.jalaliToGregorian(year, month, day)?.timeInMillis
      ?: fromMs + days.toLong() * MILLIS_PER_DAY
  }
}
