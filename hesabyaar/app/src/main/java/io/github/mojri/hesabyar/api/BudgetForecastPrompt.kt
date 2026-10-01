package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction

// Prompt assembly for the online AI budget forecast in BudgetAdvisor.
// Kept separate so BudgetAdvisor stays a thin Rust/AI orchestrator.

/** Aggregated numbers the forecast prompt reports to the model. */
internal data class ForecastFacts(
  val totalIncome: Long,
  val totalExpense: Long,
  val activeLoansCount: Int,
  val upcomingInstallments: List<Installment>,
  val totalUpcomingAmount: Long,
  val categoryReport: String
) {
  companion object {
    /** 30-day upcoming-obligation window, matching the Rust offline forecast. */
    private const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L

    /**
     * Builds the prompt facts. The income, expense, and category figures are
     * KPI aggregates, so they drop [excludedCategoryIds] exactly like the
     * advice-prompt summary and the offline substitutes (plan 011 D2).
     */
    fun of(
      transactions: List<Transaction>,
      loans: List<Loan>,
      installments: List<Installment>,
      categories: List<Category>,
      bankLoans: List<BankLoan>,
      excludedCategoryIds: List<Long> = emptyList()
    ): ForecastFacts {
      // Mirror the Rust offline forecast's 30-day window so the model sees
      // "upcoming" obligations the same way, and sort by due date so the
      // 15-line cap below lists the nearest installments instead of an
      // arbitrary slice of the unpaid backlog.
      val nowMs = System.currentTimeMillis()
      val windowEndMs = nowMs + THIRTY_DAYS_MS
      val upcomingInstallments =
        installments
          .filter { !it.isPaid && it.dueDate >= nowMs && it.dueDate <= windowEndMs }
          .sortedBy { it.dueDate }
      val totals = LocalBudgetAdvice.summarize(transactions, excludedCategoryIds)
      return ForecastFacts(
        totalIncome = totals.income,
        totalExpense = totals.expense,
        activeLoansCount = loans.count { !it.isSettled } + bankLoans.count { !it.isSettled },
        upcomingInstallments = upcomingInstallments,
        totalUpcomingAmount = upcomingInstallments.sumOf { it.amount },
        categoryReport = LocalBudgetAdvice.categoryReport(transactions, categories, excludedCategoryIds)
      )
    }
  }
}

/** Builds the Persian forecast prompt and carries its AI call settings. */
internal object BudgetForecastPrompt {
  private const val MAX_LISTED_INSTALLMENTS = 15

  /** Sampling temperature for the forecast call. */
  const val TEMPERATURE = 0.7

  /** System instruction that keeps the forecast voice and language stable. */
  const val SYSTEM_INSTRUCTION =
    "You are Hesabyar's Elite Financial Advisor. Analyze the user's Persian transactions " +
      "carefully and provide smart, structured financial recommendations in beautiful Persian. " +
      "Be friendly, polite, action-oriented, and encouraging."

  /** Shown when there is nothing to forecast from. */
  const val NO_DATA_MESSAGE =
    "تراکنش یا قسطی در سیستم ثبت نشده است. برای پیش‌بینی دقیق بودجه ماه آینده، لازم است " +
      "تراکنش‌ها یا تعهدات مالی خود را در حسابیار وارد کنید."

  fun build(facts: ForecastFacts): String =
    """
      |سلام. من یک حسابدار شخصی ایرانی دارم به نام «حسابیار».
      |لطفاً تراکنش‌های مالی اخیر و تعهدات مالی پیش‌روی مرا تحلیل کرده و پیش‌بینی وضعیت بودجه و تراز مالی ماه آینده مرا به همراه یک هشدار هوشمند (Smart Alert) صمیمی و روان به زبان فارسی ارائه دهی.
      |
      |داده‌های کلی من:
      |- پایش درآمد کل جاری: ${LocalBudgetAdvice.formatAmount(facts.totalIncome)}
      |- پایش مخارج کل جاری: ${LocalBudgetAdvice.formatAmount(facts.totalExpense)}
      |- اقساط پرداخت نشده در آینده نزدیک: ${facts.upcomingInstallments.size} مورد با مبلغ کل تعهد ${
      LocalBudgetAdvice.formatAmount(facts.totalUpcomingAmount)
    }
      |- تعداد وام‌های فعال: ${facts.activeLoansCount} مورد
      |
      |خلاصه دسته‌بندی مخارج من:
      |${facts.categoryReport}
      |
      |لیست اقساط پرداخت نشده آینده:
      |${installmentLines(facts.upcomingInstallments)}
      |
      |لطفا با لحن صمیمانه، دلسوزانه و انگیزه‌بخش تحلیل کن:
      |1. یک پیش‌بینی واقع‌بینانه از تراز نقدی من در ۳۰ روز آینده (با توجه به میانگین دخل و خرج و اقساط پیش رو).
      |2. یک «هشدار هوشمند» ارزشمند (بسته به میزان ریسک یا آرامش مالی من در ماه بعد).
      |3. حداقل ۲ راهکار کاملاً کاربردی و اختصاصی برای بهبود وضعیت بودجه ماه بعد.
      |از ساختار مارک‌داون روان با ایموجی‌های مناسب استفاده کن. در متن نهایی از کلمه‌های انگلیسی استفاده نکن و همه چیز کاملاً فارسی و روان باشد.
    """.trimMargin()

  /** One Markdown line per upcoming installment, capped for prompt size. */
  private fun installmentLines(upcomingInstallments: List<Installment>): String =
    upcomingInstallments.take(MAX_LISTED_INSTALLMENTS).joinToString("\n") { inst ->
      "- قسط: ${inst.title} | مبلغ: ${LocalBudgetAdvice.formatAmount(inst.amount)}"
    }
}
