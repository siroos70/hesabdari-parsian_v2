package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.flow.Flow

class ManageTransactionUseCase(
  private val repository: HesabyarRepositoryInterface
) {
  val allTransactions: Flow<List<Transaction>> = repository.allTransactions

  fun getTransactionsInRange(
    start: Long,
    end: Long
  ): Flow<List<Transaction>> = repository.getTransactionsInRange(start, end)

  suspend fun addTransaction(
    type: TransactionType,
    categoryId: Long,
    amount: Long,
    description: String,
    personName: String? = null,
    customDate: Long? = null,
    accountId: Long = io.github.mojri.hesabyar.data.DEFAULT_ACCOUNT_ID,
    destinationAccountId: Long? = null
  ): Long =
    repository.insertTransaction(
      Transaction(
        type = type,
        categoryId = categoryId,
        amount = amount,
        description = description,
        personName = personName,
        date = customDate ?: System.currentTimeMillis(),
        accountId = accountId,
        destinationAccountId = destinationAccountId
      )
    )

  suspend fun updateTransaction(transaction: Transaction) = repository.updateTransaction(transaction)

  suspend fun deleteTransaction(transaction: Transaction) = repository.deleteTransaction(transaction)
}
