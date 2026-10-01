package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.DEFAULT_ACCOUNT_ID
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ManageTransactionUseCaseTest {
  private val fake = FakeRepository()
  private val useCase = ManageTransactionUseCase(fake)

  // ── addTransaction ──────────────────────────────────────────────────────

  @Test
  fun addTransactionExplicitTransferStoresBothAccountIds() =
    runTest {
      val id =
        useCase.addTransaction(
          type = TransactionType.TRANSFER,
          categoryId = 0L,
          amount = 5_000L,
          description = "Transfer desc",
          accountId = 10L,
          destinationAccountId = 20L
        )

      val stored = fake.allTransactions.first()
      assertEquals("exactly one transaction must be stored", 1, stored.size)
      assertEquals("stored transaction must carry the returned id", id, stored.first().id)
      assertEquals("stored type must be TRANSFER", TransactionType.TRANSFER, stored.first().type)
      assertEquals("source accountId must be preserved", 10L, stored.first().accountId)
      assertEquals("destination accountId must be preserved", 20L, stored.first().destinationAccountId)
    }

  @Test
  fun addTransactionExplicitIncomeStoresSourceAccountOnly() =
    runTest {
      val id =
        useCase.addTransaction(
          type = TransactionType.INCOME,
          categoryId = 1L,
          amount = 3_000L,
          description = "Salary",
          accountId = 10L,
          destinationAccountId = null
        )

      val stored = fake.allTransactions.first()
      assertEquals("exactly one transaction must be stored", 1, stored.size)
      assertEquals("stored transaction must carry the returned id", id, stored.first().id)
      assertEquals("stored type must be INCOME", TransactionType.INCOME, stored.first().type)
      assertEquals("source accountId must be preserved", 10L, stored.first().accountId)
      assertEquals(
        "non-transfer transaction must store no destination account",
        null,
        stored.first().destinationAccountId
      )
    }

  @Test
  fun addTransactionExplicitExpenseStoresSourceAccountOnly() =
    runTest {
      val id =
        useCase.addTransaction(
          type = TransactionType.EXPENSE,
          categoryId = 2L,
          amount = 1_500L,
          description = "Groceries",
          accountId = 10L,
          destinationAccountId = null
        )

      val stored = fake.allTransactions.first()
      assertEquals("exactly one transaction must be stored", 1, stored.size)
      assertEquals("stored transaction must carry the returned id", id, stored.first().id)
      assertEquals("stored type must be EXPENSE", TransactionType.EXPENSE, stored.first().type)
      assertEquals("source accountId must be preserved", 10L, stored.first().accountId)
      assertEquals(
        "non-transfer transaction must store no destination account",
        null,
        stored.first().destinationAccountId
      )
    }

  @Test
  fun addTransactionDefaultAccountIdWhenOmitted() =
    runTest {
      val id =
        useCase.addTransaction(
          type = TransactionType.INCOME,
          categoryId = 1L,
          amount = 2_000L,
          description = "Default account income"
        )

      val stored = fake.allTransactions.first()
      assertEquals("exactly one transaction must be stored", 1, stored.size)
      assertEquals("stored transaction must carry the returned id", id, stored.first().id)
      assertEquals("accountId must resolve to the default account", DEFAULT_ACCOUNT_ID, stored.first().accountId)
      assertEquals(
        "non-transfer transaction must store no destination account",
        null,
        stored.first().destinationAccountId
      )
    }

  @Test
  fun addTransactionDefaultAccountIdWhenExplicitlyPassed() =
    runTest {
      val id =
        useCase.addTransaction(
          type = TransactionType.EXPENSE,
          categoryId = 2L,
          amount = 500L,
          description = "Explicit default account expense",
          accountId = DEFAULT_ACCOUNT_ID,
          destinationAccountId = null
        )

      val stored = fake.allTransactions.first()
      assertEquals("exactly one transaction must be stored", 1, stored.size)
      assertEquals("stored transaction must carry the returned id", id, stored.first().id)
      assertEquals("accountId must resolve to the default account", DEFAULT_ACCOUNT_ID, stored.first().accountId)
    }

  @Test
  fun addTransactionTransferWithDefaultSourceAndExplicitDest() =
    runTest {
      val id =
        useCase.addTransaction(
          type = TransactionType.TRANSFER,
          categoryId = 0L,
          amount = 1_000L,
          description = "Default to other",
          accountId = DEFAULT_ACCOUNT_ID,
          destinationAccountId = 20L
        )

      val stored = fake.allTransactions.first()
      assertEquals("exactly one transaction must be stored", 1, stored.size)
      assertEquals("stored transaction must carry the returned id", id, stored.first().id)
      assertEquals("accountId must resolve to the default account", DEFAULT_ACCOUNT_ID, stored.first().accountId)
      assertEquals("destination accountId must be preserved", 20L, stored.first().destinationAccountId)
    }

  @Test
  fun addTransactionUninvolvedAccountNeutralForOtherAccount() =
    runTest {
      useCase.addTransaction(
        type = TransactionType.TRANSFER,
        categoryId = 0L,
        amount = 4_000L,
        description = "Between other accounts",
        accountId = 5L,
        destinationAccountId = 15L
      )

      val stored = fake.allTransactions.first()
      assertEquals("exactly one transaction must be stored", 1, stored.size)
      assertEquals("source accountId must be preserved", 5L, stored.first().accountId)
      assertEquals("destination accountId must be preserved", 15L, stored.first().destinationAccountId)
    }

  // ── updateTransaction ───────────────────────────────────────────────────

  @Test
  fun updateTransactionPreservesAccountAssociations() =
    runTest {
      val original =
        Transaction(
          id = 10L,
          type = TransactionType.TRANSFER,
          categoryId = 0L,
          amount = 3_000L,
          description = "Original",
          accountId = 5L,
          destinationAccountId = 8L
        )
      fake.insertTransaction(original)

      val updated = original.copy(description = "Updated", accountId = 7L, destinationAccountId = 12L)
      useCase.updateTransaction(updated)

      val stored = fake.allTransactions.first()
      assertEquals("exactly one transaction must be stored", 1, stored.size)
      assertEquals("transaction id must be preserved on update", 10L, stored.first().id)
      assertEquals("description must be updated", "Updated", stored.first().description)
      assertEquals("accountId must be updated", 7L, stored.first().accountId)
      assertEquals("destinationAccountId must be updated", 12L, stored.first().destinationAccountId)
    }

  @Test
  fun updateTransactionChangesTransferToIncomeClearsDestination() =
    runTest {
      val original =
        Transaction(
          id = 10L,
          type = TransactionType.TRANSFER,
          categoryId = 0L,
          amount = 3_000L,
          description = "Original",
          accountId = 5L,
          destinationAccountId = 8L
        )
      fake.insertTransaction(original)

      val updated =
        original.copy(type = TransactionType.INCOME, destinationAccountId = null, accountId = 3L)
      useCase.updateTransaction(updated)

      val stored = fake.allTransactions.first()
      assertEquals("stored type must be INCOME", TransactionType.INCOME, stored.first().type)
      assertEquals("accountId must be updated", 3L, stored.first().accountId)
      assertEquals(
        "non-transfer transaction must store no destination account",
        null,
        stored.first().destinationAccountId
      )
    }

  // ── deleteTransaction ───────────────────────────────────────────────────

  /**
   * FakeRepository.deleteTransaction is a no-op (matching the Room DAO's behavior
   * is not needed here), so use a tracking repository to verify the use case
   * delegates the full transaction — including account associations — verbatim.
   */
  private class TrackingRepository : HesabyarRepositoryInterface by FakeRepository() {
    var deleted: Transaction? = null

    override suspend fun deleteTransaction(transaction: Transaction) {
      deleted = transaction
    }
  }

  @Test
  fun deleteTransactionDelegatesFullTransactionToRepository() =
    runTest {
      val repo = TrackingRepository()
      val deleteUseCase = ManageTransactionUseCase(repo)
      val tx =
        Transaction(
          id = 10L,
          type = TransactionType.TRANSFER,
          categoryId = 0L,
          amount = 3_000L,
          description = "To delete",
          accountId = 5L,
          destinationAccountId = 8L
        )

      deleteUseCase.deleteTransaction(tx)

      assertEquals("full transaction must be delegated to the repository", tx, repo.deleted)
    }

  // ── Error handling ──────────────────────────────────────────────────────

  /**
   * The shared FakeRepository only simulates failures for account CRUD; the
   * transaction methods never throw. Delegate to it and override the transaction
   * methods so the use case's error propagation can be verified in isolation.
   */
  private class ThrowingRepository : HesabyarRepositoryInterface by FakeRepository() {
    override suspend fun insertTransaction(transaction: Transaction): Long =
      throw IllegalStateException("Simulated DB failure")

    override suspend fun updateTransaction(transaction: Transaction) =
      throw IllegalStateException("Simulated DB failure")

    override suspend fun deleteTransaction(transaction: Transaction) =
      throw IllegalStateException("Simulated DB failure")
  }

  @Test(expected = IllegalStateException::class)
  fun addTransactionPropagatesRepositoryInsertFailure() =
    runTest {
      ManageTransactionUseCase(ThrowingRepository())
        .addTransaction(
          type = TransactionType.INCOME,
          categoryId = 1L,
          amount = 1_000L,
          description = "Will fail"
        )
    }

  @Test(expected = IllegalStateException::class)
  fun updateTransactionPropagatesRepositoryUpdateFailure() =
    runTest {
      val tx =
        Transaction(
          id = 10L,
          type = TransactionType.INCOME,
          categoryId = 1L,
          amount = 1_000L,
          description = "Will fail update"
        )
      ManageTransactionUseCase(ThrowingRepository()).updateTransaction(tx)
    }

  @Test(expected = IllegalStateException::class)
  fun deleteTransactionPropagatesRepositoryDeleteFailure() =
    runTest {
      val tx =
        Transaction(
          id = 10L,
          type = TransactionType.INCOME,
          categoryId = 1L,
          amount = 1_000L,
          description = "Will fail delete"
        )
      ManageTransactionUseCase(ThrowingRepository()).deleteTransaction(tx)
    }
}
