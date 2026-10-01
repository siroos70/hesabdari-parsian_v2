package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.utils.LoansCategoryExclusion
import io.github.mojri.hesabyar.rust.RustBridge
import io.github.mojri.hesabyar.rust.RustMappers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Budget advice entry points for the app.
 *
 * Each metric follows the Rust-first policy: call the native core, and use the
 * Kotlin fallbacks in [LocalBudgetAdvice], [LocalBudgetForecast], and
 * [LocalBudgetMetrics] only when the core is unavailable. [getBudgetAdvice]
 * delegates to [BudgetAdviceGenerator], which is loans/installments-aware.
 *
 * [excludedCategoryIds] carries the category ids to drop from KPI aggregates,
 * such as the "Loans" category. Callers resolve it once and pass it down.
 */
object BudgetAdvisor {
  private const val TAG = "BudgetAdvisor"
  private const val PERCENT_SCALE = 100
  private const val HIGH_DEBT_RATIO = 0.4
  private const val GOOD_SAVINGS_RATE = 0.3
  private const val MAX_PERSONALIZED_INSTALLMENT_LINES = 3

  /**
   * Budget advice entry point used by the app. Delegates to
   * [BudgetAdviceGenerator], which is loans/installments-aware (its offline
   * path surfaces unpaid loans and upcoming installments).
   */
  suspend fun getBudgetAdvice(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
    config: AiProviderConfig? = null,
    bankLoans: List<BankLoan> = emptyList(),
    aiGenerate: suspend (AiProviderConfig, String, String?, Double) -> AiProvider.ApiResult =
      { cfg, prompt, sys, temp -> AiProvider.generateContent(cfg, prompt, sys, temp) }
  ): String =
    BudgetAdviceGenerator.getBudgetAdvice(
      transactions,
      loans,
      installments,
      categories,
      config,
      bankLoans,
      aiGenerate
    )

  /**
   * Resolves the Persian display name for a category key using the production
   * default-category definitions in [Category.DEFAULTS]. Unknown keys fall back
   * to the key itself so no transaction is silently mislabeled.
   */
  fun getPersianCategoryName(key: String): String = Category.DEFAULTS.firstOrNull { it.key == key }?.name ?: key

  // High quality local rules budget advisor for offline mode
  fun getOfflineAdvice(
    transactions: List<Transaction>,
    categories: List<Category>,
    excludedCategoryIds: List<Long> = emptyList()
  ): String {
    if (RustBridge.isAvailable) {
      val rustResult =
        RustBridge.getOfflineBudgetAdviceSync(
          RustMappers.mapTransactions(transactions),
          RustMappers.mapCategories(categories),
          excludedCategoryIds
        )
      if (rustResult.isNotEmpty()) return rustResult
    }

    // Rust unavailable: serve a local, data-driven fallback instead of a false empty-state.
    return LocalBudgetAdvice.offlineAdvice(transactions, categories, excludedCategoryIds)
  }

  /**
   * Budget forecast entry point. The [aiGenerate] seam lets tests capture the
   * prompt without a network call; production keeps the default provider.
   */
  suspend fun getBudgetForecast(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
    config: AiProviderConfig? = null,
    bankLoans: List<BankLoan> = emptyList(),
    aiGenerate: suspend (AiProviderConfig, String, String?, Double) -> AiProvider.ApiResult =
      { cfg, prompt, sys, temp -> AiProvider.generateContent(cfg, prompt, sys, temp) }
  ): String =
    withContext(Dispatchers.IO) {
      AppLogger.d(
        TAG,
        "getBudgetForecast: config=${config?.let {
          "provider=${it.providerType}, isConfigured=${it.isConfigured}"
        } ?: "null"}"
      )
      // Every aggregate in this method is a KPI number. The offline
      // substitutes below and the online ForecastFacts both exclude the same
      // category ids the dashboard and analytics views exclude (plan 011 D2).
      // The advice prompt in BudgetAdviceGenerator applies the same rule.
      val excludedCategoryIds = LoansCategoryExclusion.resolve(categories, TAG)
      val providerConfig = config ?: AiProviderConfig()
      if (!providerConfig.isConfigured) {
        AppLogger.w(TAG, "AI provider not configured, using offline local budget forecast")
        return@withContext getOfflineForecast(transactions, loans, installments, bankLoans, excludedCategoryIds)
      }

      // "No data" must also respect unsettled obligations: the offline
      // substitute renders loan/bank-loan debt lines, so suppressing them
      // here with a NO_DATA message would diverge from the offline path for
      // a user whose only records are loans.
      val hasNoActivities = transactions.isEmpty() && installments.isEmpty()
      val hasNoUnsettled = loans.none { !it.isSettled } && bankLoans.none { !it.isSettled }
      if (hasNoActivities && hasNoUnsettled) {
        return@withContext BudgetForecastPrompt.NO_DATA_MESSAGE
      }

      val facts = ForecastFacts.of(transactions, loans, installments, categories, bankLoans, excludedCategoryIds)
      when (
        val result =
          aiGenerate(
            providerConfig,
            BudgetForecastPrompt.build(facts),
            BudgetForecastPrompt.SYSTEM_INSTRUCTION,
            BudgetForecastPrompt.TEMPERATURE
          )
      ) {
        is AiProvider.ApiResult.Success ->
          validatedForecast(result.text, transactions, loans, installments, bankLoans, excludedCategoryIds)
        is AiProvider.ApiResult.Failure -> {
          AppLogger.e(TAG, "AI forecast failed: ${result.error}")
          "⚠️ اتصال به سرور ابری انجام نشد یا کلید معتبر نیست. پیش‌بینی محلی شما به شرح زیر است:\n\n" +
            getOfflineForecast(transactions, loans, installments, bankLoans, excludedCategoryIds)
        }
      }
    }

  /** Keeps a valid AI forecast, otherwise explains the offline substitute. */
  private suspend fun validatedForecast(
    text: String,
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    bankLoans: List<BankLoan>,
    excludedCategoryIds: List<Long>
  ): String {
    val validation = RustBridge.validateAiAdvice(text)
    // When the local Rust validator is uninitialized, trust the cloud
    // forecast instead of discarding it on an unavailable engine.
    if (AdviceValidationPolicy.shouldDiscardOnValidationFailure(validation, RustBridge.isAvailable)) {
      AppLogger.w(TAG, "AI forecast failed validation, using offline: ${validation.warnings}")
      return "⚠️ پیش‌بینی هوش مصنوعی نامعتبر بود. پیش‌بینی محلی شما:\n\n" +
        getOfflineForecast(transactions, loans, installments, bankLoans, excludedCategoryIds)
    }
    if (validation.wasTruncated) {
      AppLogger.d(TAG, "AI forecast truncated: ${validation.warnings}")
    }
    return validation.sanitizedText
  }

  // Local predictive forecasting offline rules fallback
  fun getOfflineForecast(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    bankLoans: List<BankLoan> = emptyList(),
    excludedCategoryIds: List<Long> = emptyList()
  ): String {
    if (RustBridge.isAvailable) {
      val rustResult =
        RustBridge.getOfflineForecastSync(
          RustMappers.mapTransactions(transactions),
          RustMappers.mapLoans(loans),
          RustMappers.mapInstallments(installments),
          bankLoans,
          excludedCategoryIds
        )
      if (rustResult.isNotEmpty()) return rustResult
    }

    // Rust unavailable: serve a baseline forecast from local data instead of a false "insufficient data" message.
    return LocalBudgetForecast.forecast(transactions, loans, installments, bankLoans, excludedCategoryIds)
  }

  fun calculateDebtToIncomeRatio(
    loans: List<Loan>,
    installments: List<Installment>,
    monthlyIncome: Long,
    bankLoans: List<BankLoan> = emptyList()
  ): Double =
    if (RustBridge.isAvailable) {
      RustBridge.calculateDebtToIncomeRatioSync(
        RustMappers.mapLoans(loans),
        RustMappers.mapInstallments(installments),
        monthlyIncome,
        bankLoans
      )
    } else {
      LocalBudgetMetrics.debtToIncomeRatio(loans, installments, monthlyIncome)
    }

  fun predictTimeToGoal(
    currentSavings: Long,
    monthlySavings: Long,
    goalAmount: Long
  ): Int =
    if (RustBridge.isAvailable) {
      RustBridge.predictTimeToGoalSync(currentSavings, monthlySavings, goalAmount)
    } else {
      LocalBudgetMetrics.timeToGoal(currentSavings, monthlySavings, goalAmount)
    }

  /** Rule-based personal advice summary for the local analytics screen. */
  fun getPersonalizedAdvice(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    bankLoans: List<BankLoan> = emptyList()
  ): String {
    val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val balance = totalIncome - totalExpense
    val debtToIncome = calculateDebtToIncomeRatio(loans, installments, totalIncome, bankLoans)
    val savingsRate = if (totalIncome > 0) balance.toDouble() / totalIncome.toDouble() else 0.0
    val upcomingInstallments = installments.filter { !it.isPaid }

    val sb = StringBuilder()
    sb.appendLine("### 📊 تحلیل مالی شخصی شما")
    sb.appendLine()
    sb.appendLine("**نسبت بدهی به درآمد:** ${(debtToIncome * PERCENT_SCALE).toInt()}٪")
    sb.appendLine("**نرخ پس‌انداز:** ${(savingsRate * PERCENT_SCALE).toInt()}٪")
    sb.appendLine()
    sb.appendLine(personalizedStatus(debtToIncome, savingsRate))

    if (upcomingInstallments.isNotEmpty()) {
      val totalUpcoming = upcomingInstallments.sumOf { it.amount }
      sb.appendLine()
      sb.appendLine(
        "📅 **اقساط در انتظار پرداخت:** ${upcomingInstallments.size} مورد (" +
          "${LocalBudgetAdvice.formatAmount(totalUpcoming)})"
      )
      upcomingInstallments.take(MAX_PERSONALIZED_INSTALLMENT_LINES).forEach { inst ->
        sb.appendLine("- ${inst.title}: ${LocalBudgetAdvice.formatAmount(inst.amount)}")
      }
    }

    return sb.toString()
  }

  /** Status block of the personal summary, chosen by debt and savings rates. */
  private fun personalizedStatus(
    debtToIncome: Double,
    savingsRate: Double
  ): String =
    when {
      debtToIncome > HIGH_DEBT_RATIO ->
        "⚠️ **هشدار:** نسبت بدهی به درآمد شما بالا است. توصیه می‌شود:\n" +
          "- پرداخت بدهی‌های با نرخ سود بالا را در اولویت قرار دهید\n" +
          "- از گرفتن وام جدید خودداری کنید"
      savingsRate > GOOD_SAVINGS_RATE ->
        "✅ **تبریک!** نرخ پس‌انداز شما عالی است. توصیه می‌شود:\n" +
          "- بخشی از پس‌انداز را سرمایه‌گذاری کنید\n" +
          "- اهداف مالی بلندمدت تعیین کنید"
      savingsRate < 0 ->
        "🚨 **کسری بودجه:** مخارج شما بیش از درآمد است!\n" +
          "- خریدهای غیرضروری را کاهش دهید\n" +
          "- فوراً یک برنامه کاهش هزینه تنظیم کنید"
      else ->
        "⚖️ **وضعیت نسبتاً متعادل:** پس‌انداز شما قابل قبول است.\n" +
          "- تلاش کنید نرخ پس‌انداز را به بالای ۲۰٪ برسانید"
    }

  fun calculateFinancialHealthScore(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
    bankLoans: List<BankLoan> = emptyList(),
    excludedCategoryIds: List<Long> = emptyList()
  ): Int =
    if (RustBridge.isAvailable) {
      RustBridge.calculateFinancialHealthScoreSync(
        RustMappers.mapTransactions(transactions),
        RustMappers.mapLoans(loans),
        RustMappers.mapInstallments(installments),
        RustMappers.mapCategories(categories),
        bankLoans,
        excludedCategoryIds
      )
    } else {
      LocalBudgetMetrics.financialHealthScore(transactions, loans, installments, bankLoans, excludedCategoryIds)
    }
}
