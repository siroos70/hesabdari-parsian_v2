package io.github.mojri.hesabyar.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ManageInstallmentUseCaseTest {
  private val fake = FakeRepository()
  private val useCase = ManageInstallmentUseCase(fake)

  @Test
  fun addTrackedInstallmentNormalizesUntrackedInstallmentAccountIdToNull() =
    runTest {
      val id =
        useCase.addTrackedInstallment(
          title = "Untracked",
          amount = 1_000_000L,
          dueDate = 1000L,
          reminderEnabled = true,
          notes = "",
          tracked = false,
          accountId = 5L
        )
      val stored = fake.allInstallments.first().first { it.id == id }
      assertFalse(stored.tracked)
      assertNull(stored.accountId)
    }

  @Test
  fun addTrackedInstallmentPreservesValidAccountIdWhenTracked() =
    runTest {
      val id =
        useCase.addTrackedInstallment(
          title = "Tracked",
          amount = 2_000_000L,
          dueDate = 1000L,
          reminderEnabled = true,
          notes = "",
          tracked = true,
          accountId = 3L
        )
      val stored = fake.allInstallments.first().first { it.id == id }
      assertTrue(stored.tracked)
      assertEquals(3L, stored.accountId)
    }

  @Test
  fun addTrackedInstallmentThrowsWhenTrackedButBankLoanIdIsPresent() =
    runTest {
      try {
        useCase.addTrackedInstallment(
          title = "Bank loan installment",
          amount = 2_000_000L,
          dueDate = 1000L,
          reminderEnabled = false,
          notes = "",
          tracked = true,
          accountId = 1L,
          bankLoanId = 10L
        )
        fail("tracked bank loan installment must be rejected per Plan 011 Decision 2")
      } catch (expected: IllegalArgumentException) {
        // Expected
      }
    }
}
