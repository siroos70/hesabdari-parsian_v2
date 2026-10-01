package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.utils.LoansCategoryExclusion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ManageLoanUseCaseTest {
  private val fake = FakeRepository()
  private val useCase = ManageLoanUseCase(fake)
  private val loansCategoryId = 100L

  @Before
  fun setUp() =
    runTest {
      fake.insertCategory(
        Category(
          id = loansCategoryId,
          name = "Loans",
          key = LoansCategoryExclusion.CATEGORY_KEY,
          icon = "loan",
          color = 1L,
          type = CategoryType.BOTH
        )
      )
    }

  @Test
  fun addLoanWithPersonNameUpsertsAndLinksPersonId() =
    runTest {
      val loanId =
        useCase.addLoan(
          personName = "Ali Reza",
          type = LoanType.CREDITOR,
          amount = 10_000_000L,
          description = "loan 1"
        )
      val loans = fake.allLoans.first()
      val storedLoan = loans.first { it.id == loanId }
      assertNotNull(storedLoan.personId)
      val persons = fake.getAllPersonsIncludingArchived()
      assertEquals(1, persons.size)
      assertEquals(storedLoan.personId, persons.first().id)
      assertEquals("Ali Reza", persons.first().name)
    }

  @Test
  fun addLoanWithExplicitPersonIdPreservesProvidedPersonId() =
    runTest {
      val explicitPersonId = 99L
      val loanId =
        useCase.addLoan(
          personName = "Sara",
          type = LoanType.DEBTOR,
          amount = 5_000_000L,
          description = "loan 2",
          personId = explicitPersonId
        )
      val storedLoan = fake.allLoans.first().first { it.id == loanId }
      assertEquals(explicitPersonId, storedLoan.personId)
    }

  @Test
  fun addTrackedLoanWithPersonNameUpsertsAndLinksPersonId() =
    runTest {
      val loanId =
        useCase.addTrackedLoan(
          personName = "Mohammad",
          type = LoanType.CREDITOR,
          amount = 20_000_000L,
          description = "tracked loan",
          tracked = true,
          accountId = 1L
        )
      val storedLoan = fake.allLoans.first().first { it.id == loanId }
      assertTrue(storedLoan.tracked)
      assertEquals(1L, storedLoan.accountId)
      assertNotNull(storedLoan.personId)
      val person = fake.getPersonById(storedLoan.personId!!)
      assertNotNull(person)
      assertEquals("Mohammad", person!!.name)
      val initialTx = fake.allTransactions.first().first { it.amount == 20_000_000L }
      assertEquals(loansCategoryId, initialTx.categoryId)
      assertEquals(TransactionType.INCOME, initialTx.type)
      assertEquals(1L, initialTx.accountId)
    }

  @Test
  fun addLoanUntrackedPostsNoTransactionAndSucceedsWithoutLoansCategory() =
    runTest {
      val category = fake.getCategoryById(loansCategoryId)
      if (category != null) {
        fake.deleteCategory(category)
      }
      val loanId =
        useCase.addLoan(
          personName = "Ali",
          type = LoanType.DEBTOR,
          amount = 10_000_000L,
          description = "untracked loan"
        )
      val storedLoan = fake.allLoans.first().first { it.id == loanId }
      assertFalse(storedLoan.tracked)
      assertNull(storedLoan.accountId)
      val transactions = fake.allTransactions.first()
      assertEquals("no initial transaction must be recorded for untracked loan", 0, transactions.size)
    }

  @Test
  fun addTrackedLoanWithRecordInitialFalsePersistsTrackedStateWithoutInitialTransaction() =
    runTest {
      val loanId =
        useCase.addTrackedLoan(
          personName = "Reza",
          type = LoanType.CREDITOR,
          amount = 30_000_000L,
          description = "ledger only",
          tracked = true,
          accountId = 2L,
          recordInitial = false
        )
      val storedLoan = fake.allLoans.first().first { it.id == loanId }
      assertTrue(storedLoan.tracked)
      assertEquals(2L, storedLoan.accountId)
      val transactions = fake.allTransactions.first()
      assertEquals("no initial transaction must be recorded when recordInitial is false", 0, transactions.size)
    }

  @Test
  fun addTrackedLoanWithExplicitPersonIdPreservesProvidedPersonId() =
    runTest {
      val explicitPersonId = 77L
      val loanId =
        useCase.addTrackedLoan(
          personName = "Zahra",
          type = LoanType.DEBTOR,
          amount = 15_000_000L,
          description = "tracked loan with id",
          tracked = true,
          accountId = 1L,
          personId = explicitPersonId
        )
      val storedLoan = fake.allLoans.first().first { it.id == loanId }
      assertEquals(explicitPersonId, storedLoan.personId)
      val initialTx = fake.allTransactions.first().first { it.amount == 15_000_000L }
      assertEquals(loansCategoryId, initialTx.categoryId)
    }

  @Test
  fun addTrackedLoanThrowsWhenLoansCategoryMissing() =
    runTest {
      val unseededFake = FakeRepository()
      val unseededUseCase = ManageLoanUseCase(unseededFake)
      try {
        unseededUseCase.addTrackedLoan(
          personName = "Missing Cat",
          type = LoanType.CREDITOR,
          amount = 5_000_000L,
          description = "test missing category",
          tracked = true,
          accountId = 1L
        )
        fail("Expected IllegalStateException when loans category is missing")
      } catch (e: IllegalStateException) {
        assertTrue(e.message!!.contains("Loans category is missing"))
      }
    }

  @Test
  fun addLoanWithEmptyPersonNameLeavesPersonIdNullWithoutUpsertingPerson() =
    runTest {
      val loanId =
        useCase.addLoan(
          personName = "   ",
          type = LoanType.DEBTOR,
          amount = 1_000_000L,
          description = "no person"
        )
      val storedLoan = fake.allLoans.first().first { it.id == loanId }
      assertEquals(null, storedLoan.personId)
      assertEquals(0, fake.getAllPersonsIncludingArchived().size)
    }

  @Test
  fun addTrackedLoanWithEmptyPersonNameLeavesPersonIdNullWithoutUpsertingPerson() =
    runTest {
      val loanId =
        useCase.addTrackedLoan(
          personName = "",
          type = LoanType.CREDITOR,
          amount = 1_000_000L,
          description = "no person tracked",
          tracked = true,
          accountId = 1L
        )
      val storedLoan = fake.allLoans.first().first { it.id == loanId }
      assertEquals(null, storedLoan.personId)
      assertEquals(0, fake.getAllPersonsIncludingArchived().size)
    }

  @Test
  fun addTrackedLoanCleansUpNewlyCreatedPersonWhenInsertFails() =
    runTest {
      val failingFake =
        object : HesabyarRepositoryInterface by fake {
          override suspend fun insertLoanWithInitial(
            loan: Loan,
            recordInitial: Boolean
          ): Long = throw IllegalStateException("Loans category is missing")
        }
      val failingUseCase = ManageLoanUseCase(failingFake)

      try {
        failingUseCase.addTrackedLoan(
          personName = "Orphan Candidate",
          type = LoanType.CREDITOR,
          amount = 5_000_000L,
          description = "fails",
          tracked = true,
          accountId = 1L
        )
        fail("insertLoanWithInitial failure must propagate")
      } catch (expected: IllegalStateException) {
        assertEquals("Loans category is missing", expected.message)
      }

      assertEquals(
        "newly created person must be rolled back on insert failure",
        0,
        failingFake.getAllPersonsIncludingArchived().size
      )
    }

  @Test
  fun addTrackedLoanRequiresValidAccountIdWhenTracked() =
    runTest {
      listOf(null, 0L, -5L).forEach { invalidAccountId ->
        try {
          useCase.addTrackedLoan(
            personName = "Test",
            type = LoanType.CREDITOR,
            amount = 1_000_000L,
            description = "",
            tracked = true,
            accountId = invalidAccountId
          )
          fail("tracked loan with accountId=$invalidAccountId must throw IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
          // Expected
        }
      }
    }

  @Test
  fun addTrackedLoanWithRequestDataClassInsertsTrackedLoanSuccessfully() =
    runTest {
      val request =
        TrackedLoanRequest(
          personName = "Khosrow",
          type = LoanType.CREDITOR,
          amount = 15_000_000L,
          description = "request data class test",
          tracked = true,
          accountId = 1L
        )
      val loanId = useCase.addTrackedLoan(request)
      val storedLoan = fake.allLoans.first().first { it.id == loanId }
      assertTrue("loan must be tracked", storedLoan.tracked)
      assertEquals(1L, storedLoan.accountId)
      assertEquals("Khosrow", storedLoan.personName)
      assertEquals(15_000_000L, storedLoan.originalAmount)
      val txs = fake.allTransactions.first()
      assertEquals("initial transaction must be recorded", 1, txs.size)
      assertEquals(15_000_000L, txs.single().amount)
      assertEquals(1L, txs.single().accountId)
    }
}
