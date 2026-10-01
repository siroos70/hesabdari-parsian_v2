package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageBankLoanUseCaseTest {
  private val fake = FakeRepository()
  private val useCase = ManageBankLoanUseCase(fake)

  @Test
  fun `addBankLoan creates loan plus one installment per count`() =
    runTest {
      val startDate = JalaliCalendarHelper.jalaliToGregorian(1403, 1, 1)!!.timeInMillis
      val id =
        useCase.addBankLoan(
          bankName = "بانک ملی",
          loanName = "خودرو",
          receivedAmount = 100_000_000L,
          monthlyInstallmentAmount = 10_000_000L,
          numberOfInstallments = 3,
          startDate = startDate,
          description = ""
        )

      val loans = fake.allBankLoans.first()
      assertEquals(1, loans.size)
      assertEquals(3, fake.getInstallmentsByBankLoanId(id).size)
      assertTrue(fake.getInstallmentsByBankLoanId(id).all { it.bankLoanId == id })
    }

  @Test
  fun `addBankLoan spaces installments by jalali months`() =
    runTest {
      val startDate = JalaliCalendarHelper.jalaliToGregorian(1403, 11, 15)!!.timeInMillis
      val id =
        useCase.addBankLoan(
          bankName = "b",
          loanName = "l",
          receivedAmount = 100_000_000L,
          monthlyInstallmentAmount = 10_000_000L,
          numberOfInstallments = 3,
          startDate = startDate,
          description = ""
        )
      val insts = fake.getInstallmentsByBankLoanId(id).sortedBy { it.dueDate }
      // Each step must advance roughly one Jalali month (not a fixed 30 days).
      val first = JalaliCalendarHelper.gregorianToJalali(insts[0].dueDate)
      val second = JalaliCalendarHelper.gregorianToJalali(insts[1].dueDate)
      assertEquals(first.month + 1, second.month)
      assertEquals(first.year, second.year)
    }
}
