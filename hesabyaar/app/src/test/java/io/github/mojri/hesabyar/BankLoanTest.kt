package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.domain.usecase.FakeRepository
import io.github.mojri.hesabyar.domain.usecase.ManageBankLoanUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankLoanTest {
  private fun buildUseCase(): Pair<ManageBankLoanUseCase, FakeRepository> {
    val repo = FakeRepository()
    return ManageBankLoanUseCase(repo) to repo
  }

  @Test
  fun `quick entry computes total repayable and interest`() =
    runBlocking {
      val (useCase, repo) = buildUseCase()
      val id =
        useCase.addBankLoan(
          bankName = "بانک ملت",
          loanName = "وام خودرو",
          receivedAmount = 100_000_000L,
          monthlyInstallmentAmount = 10_000_000L,
          numberOfInstallments = 12,
          startDate = 1_700_000_000_000L,
          description = ""
        )
      val loan = repo.getBankLoanById(id)!!
      assertEquals(120_000_000L, loan.totalRepayableAmount)
      assertEquals(20_000_000L, loan.totalInterest)
      assertEquals(12, loan.numberOfInstallments)
    }

  @Test
  fun `adding bank loan auto generates one installment per month`() =
    runBlocking {
      val (useCase, repo) = buildUseCase()
      val id =
        useCase.addBankLoan(
          bankName = "بانک ملت",
          loanName = "وام خودرو",
          receivedAmount = 100_000_000L,
          monthlyInstallmentAmount = 10_000_000L,
          numberOfInstallments = 12,
          startDate = 1_700_000_000_000L,
          description = ""
        )
      val installments = repo.getInstallmentsByBankLoanId(id)
      assertEquals(12, installments.size)
      assertEquals(10_000_000L, installments.first().amount)
      installments.forEach { assertEquals(id, it.bankLoanId) }
    }

  @Test
  fun `zero installments falls back to a single loan`() =
    runBlocking {
      val (useCase, repo) = buildUseCase()
      val id =
        useCase.addBankLoan(
          bankName = "صندوق",
          loanName = "قرض الحسنه",
          receivedAmount = 5_000_000L,
          monthlyInstallmentAmount = 1_000_000L,
          numberOfInstallments = 0,
          startDate = 1_700_000_000_000L,
          description = ""
        )
      val installments = repo.getInstallmentsByBankLoanId(id)
      assertEquals(1, installments.size)
      assertEquals(1_000_000L, installments.first().amount)
    }

  @Test
  fun `deleteBankLoan cascades to remove linked installments`() =
    runBlocking {
      val (useCase, repo) = buildUseCase()
      val id =
        useCase.addBankLoan(
          bankName = "بانک ملت",
          loanName = "وام خودرو",
          receivedAmount = 100_000_000L,
          monthlyInstallmentAmount = 10_000_000L,
          numberOfInstallments = 12,
          startDate = 1_700_000_000_000L,
          description = ""
        )
      assertEquals(12, repo.getInstallmentsByBankLoanId(id).size)

      val bankLoan = repo.getBankLoanById(id)!!
      repo.deleteBankLoan(bankLoan)

      assertTrue(repo.getInstallmentsByBankLoanId(id).isEmpty())
    }
}
