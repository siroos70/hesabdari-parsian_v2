package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.core.MathUtils
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.utils.LoansCategoryExclusion
import io.github.mojri.hesabyar.ui.CurrencyFormatter

// Kotlin fallback text builders for the offline budget advice.
//
// BudgetAdvisor keeps the public API and the Rust-first dispatch. This object
// holds the dependency-free local rendering used when the Rust core is
// unavailable, so BudgetAdvisor stays a thin orchestrator.

/** Income, expense, and balance totals for a set of transactions. */
internal data class LocalTxSummary(
  val income: Long,
  val expense: Long
) {
  val balance: Long get() = MathUtils.saturatingSub(income, expense)
}

internal object LocalBudgetAdvice {
  // The 10% savings-rate threshold matching Rust's offline advice (budget.rs saving_rate >= 10.0).
  private const val MIN_SAVINGS_RATE_PERCENT = 10.0
  private const val PERCENT_MULTIPLIER = 100.0
  private const val UNCATEGORIZED_LABEL = "سایر"

  /** Formats a Rial amount for display. */
  fun formatAmount(amount: Long): String = CurrencyFormatter.format(amount)

  /** Totals for income and expense, skipping excluded category ids. */
  fun summarize(
    transactions: List<Transaction>,
    excludedCategoryIds: List<Long> = emptyList()
  ): LocalTxSummary {
    var income = 0L
    var expense = 0L
    for (tx in LoansCategoryExclusion.filterTransactions(transactions, excludedCategoryIds)) {
      when (tx.type) {
        TransactionType.INCOME -> income = MathUtils.saturatingAdd(income, tx.amount)
        TransactionType.EXPENSE -> expense = MathUtils.saturatingAdd(expense, tx.amount)
        else -> Unit
      }
    }
    return LocalTxSummary(income, expense)
  }

  /** One Markdown line per expense category with its total. */
  fun categoryReport(
    transactions: List<Transaction>,
    categories: List<Category>,
    excludedCategoryIds: List<Long> = emptyList()
  ): String {
    val categoryTotals = LinkedHashMap<Long, Long>()
    for (tx in LoansCategoryExclusion.filterTransactions(transactions, excludedCategoryIds)) {
      if (tx.type == TransactionType.EXPENSE) {
        val current = categoryTotals[tx.categoryId] ?: 0L
        categoryTotals[tx.categoryId] = MathUtils.saturatingAdd(current, tx.amount)
      }
    }

    val categoryIndex = categories.associateBy { it.id }
    return categoryTotals.entries.joinToString("\n") { (catId, sum) ->
      val cat = categoryIndex[catId]
      "- ${cat?.name ?: UNCATEGORIZED_LABEL}: ${formatAmount(sum)}"
    }
  }

  /** Local, dependency-free budget advice used when the Rust core is unavailable. */
  fun offlineAdvice(
    transactions: List<Transaction>,
    categories: List<Category>,
    excludedCategoryIds: List<Long> = emptyList()
  ): String {
    // Plan 011 D2: gate on the filtered set so a wallet whose only
    // transactions are excluded (e.g. Loans-only history) gets the no-data
    // message instead of a zero-budget analysis.
    if (transactions.all { LoansCategoryExclusion.isExcluded(it, excludedCategoryIds) }) {
      return "هنوز تراکنشی در حسابیار ثبت نشده است. لطفا چند تراکنش ثبت کنید تا تحلیل بودجه انجام شود."
    }
    val summary = summarize(transactions, excludedCategoryIds)
    val report = categoryReport(transactions, categories, excludedCategoryIds)

    val sb = StringBuilder()
    sb.appendLine("### 📊 تحلیل بودجه محلی (آفلاین)")
    sb.appendLine()
    sb.appendLine("**کل درآمد:** ${formatAmount(summary.income)}")
    sb.appendLine("**کل هزینه‌ها:** ${formatAmount(summary.expense)}")
    sb.appendLine("**تراز باقیمانده:** ${formatAmount(summary.balance)}")
    if (report.isNotEmpty()) {
      sb.appendLine()
      sb.appendLine("**هزینه‌ها به تفکیک دسته‌بندی:**")
      sb.appendLine(report)
    }
    sb.appendLine()
    sb.appendLine(statusLine(summary))
    return sb.toString()
  }

  /** Advice status sentence for a summary. */
  private fun statusLine(summary: LocalTxSummary): String =
    when {
      summary.expense > summary.income ->
        "🚨 **کسری بودجه:** مخارج شما بیش از درآمد است. کاهش هزینه‌های غیرضروری توصیه می‌شود."
      // Mirror the Rust advice threshold: savings rate >= 10% is مطلوب.
      summary.income > 0 &&
        summary.balance >= 0 &&
        summary.balance.toDouble() / summary.income.toDouble() * PERCENT_MULTIPLIER >= MIN_SAVINGS_RATE_PERCENT ->
        "✅ **وضعیت مطلوب:** نرخ پس‌انداز شما مناسب است. ادامه این روند توصیه می‌شود."
      else ->
        "⚖️ **وضعیت متعادل:** تلاش کنید نرخ پس‌انداز خود را افزایش دهید."
    }
}
