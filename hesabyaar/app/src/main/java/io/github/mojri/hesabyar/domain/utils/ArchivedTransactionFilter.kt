package io.github.mojri.hesabyar.domain.utils

import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.Transaction

/**
 * Shared transaction filtering for archived accounts.
 * Decouples use cases (dashboard and analytics) so neither depends on the other.
 */
object ArchivedTransactionFilter {
  /**
   * Excludes transactions whose source or destination account is archived
   * unless [includeArchived] is true.
   */
  fun filter(
    transactions: List<Transaction>,
    accounts: List<AccountEntity>,
    includeArchived: Boolean
  ): List<Transaction> {
    if (includeArchived) return transactions
    val archivedAccountIds = accounts.filter { it.isArchived }.map { it.id }.toSet()
    return if (archivedAccountIds.isEmpty()) {
      transactions
    } else {
      transactions.filter { tx ->
        val sourceArchived = tx.accountId != null && tx.accountId in archivedAccountIds
        val destArchived =
          tx.destinationAccountId != null && tx.destinationAccountId in archivedAccountIds
        !sourceArchived && !destArchived
      }
    }
  }
}
