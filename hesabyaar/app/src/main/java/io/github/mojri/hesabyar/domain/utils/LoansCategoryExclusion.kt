package io.github.mojri.hesabyar.domain.utils

import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Transaction

/**
 * Resolves the seeded `"Loans"` category for KPI exclusion.
 *
 * Category ids differ per install. Kotlin resolves the id from `Category.key`.
 * A missing category logs a warning and produces an empty exclusion list.
 */
object LoansCategoryExclusion {
  const val CATEGORY_KEY = "Loans"

  /** Returns the dynamic Loans id, or an empty list if resolution fails. */
  fun resolve(
    categories: List<Category>,
    logTag: String,
  ): List<Long> {
    val loansCategory = categories.find { it.key == CATEGORY_KEY }
    if (loansCategory != null) {
      return listOf(loansCategory.id)
    }

    AppLogger.w(
      logTag,
      "Loans category not found by key; defaulting to empty exclusion list"
    )
    return emptyList()
  }

  /** Returns true when the category belongs in the KPI exclusion list. */
  fun isExcluded(
    categoryId: Long,
    excludedCategoryIds: List<Long>,
  ): Boolean = categoryId in excludedCategoryIds

  /** Returns true when the transaction belongs in the KPI exclusion list. */
  fun isExcluded(
    transaction: Transaction,
    excludedCategoryIds: List<Long>,
  ): Boolean = isExcluded(transaction.categoryId, excludedCategoryIds)

  /** Removes transactions in [excludedCategoryIds] from a KPI calculation. */
  fun filterTransactions(
    transactions: List<Transaction>,
    excludedCategoryIds: List<Long>,
  ): List<Transaction> {
    if (excludedCategoryIds.isEmpty()) return transactions
    return transactions.filter { !isExcluded(it, excludedCategoryIds) }
  }
}
