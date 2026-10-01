package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmitManualTransactionTransferTest {
  private val fake = FakeRepository()
  private val manageTransaction = ManageTransactionUseCase(fake)
  private val manageLoan = ManageLoanUseCase(fake)
  private val manageInstallment = ManageInstallmentUseCase(fake)
  private val useCase =
    SubmitManualTransactionUseCase(manageTransaction, manageLoan, manageInstallment)

  @Test
  fun submitEditTransferTypePinnedToTransfer() =
    runTest {
      val sourceAccount = account(id = 5L, name = "Source")
      val destAccount = account(id = 9L, name = "Dest")
      fake.insertAccount(sourceAccount)
      fake.insertAccount(destAccount)
      val original =
        Transaction(
          id = 10L,
          type = TransactionType.EXPENSE,
          categoryId = 1L,
          amount = 3000L,
          description = "old expense",
          date = System.currentTimeMillis(),
          accountId = 5L
        )
      fake.insertTransaction(original)

      val result =
        useCase.submit(
          SubmitManualTransactionUseCase.SubmitManualTransactionRequest(
            amountDisplay = 4000L,
            selectedType = "TRANSFER",
            selectedCategoryId = 0L,
            descriptionText = "",
            personName = "",
            title = "",
            daysFromNowText = "",
            amountRial = 4000L,
            customDate = System.currentTimeMillis(),
            categories = emptyList(),
            transactionToEdit = original,
            accountId = 5L,
            destinationAccountId = 9L
          )
        )
      assertTrue("submit should succeed for a transfer edit", result.success)

      val stored = fake.allTransactions.first()
      assertEquals("exactly one transaction must be stored", 1, stored.size)
      assertEquals("transaction id must be preserved", 10L, stored.first().id)
      assertEquals(
        "edit through transfer path must pin type to TRANSFER",
        TransactionType.TRANSFER,
        stored.first().type
      )
      assertEquals("destination accountId must be set", 9L, stored.first().destinationAccountId)
    }

  private fun account(
    id: Long,
    name: String,
    type: AccountType = AccountType.BANK,
    color: Long = 0L
  ) = AccountEntity(id = id, name = name, type = type, color = color, icon = "", isArchived = false, displayOrder = 0)
}
