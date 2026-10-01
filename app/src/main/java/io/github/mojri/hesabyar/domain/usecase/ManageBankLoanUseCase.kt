package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ManageBankLoanUseCase(
  private val repository: HesabyarRepositoryInterface
) {
  val allBankLoans: Flow<List<BankLoan>> = repository.allBankLoans

  /**
   * Quick entry: only received amount, monthly installment, and count are entered;
   * total repayable and interest are computed.
   */
  suspend fun addBankLoan(
    bankName: String,
    loanName: String,
    receivedAmount: Long,
    monthlyInstallmentAmount: Long,
    numberOfInstallments: Int,
    startDate: Long,
    description: String
  ): Long {
    require(bankName.isNotBlank()) { "bankName must not be blank" }
    require(receivedAmount > 0) { "receivedAmount must be positive" }
    require(monthlyInstallmentAmount > 0) { "monthlyInstallmentAmount must be positive" }
    require(numberOfInstallments >= 0) { "numberOfInstallments must not be negative" }
    require(startDate > 0) { "startDate must be positive" }

    // A count of 0 is an interest-free single-payment loan; fall back to one installment.
    val count = if (numberOfInstallments > 0) numberOfInstallments else 1
    // Checked multiplication so an overflowed repayable amount can never be
    // persisted; the interest subtraction is plain (negative = subsidized loan).
    val totalRepayable = Math.multiplyExact(monthlyInstallmentAmount, count.toLong())
    // Plain subtraction: receivedAmount above total repayable (a negative
    // interest, i.e. a subsidized loan) is domain-legal data, not a crash.
    val totalInterest = totalRepayable - receivedAmount
    val bankLoan =
      BankLoan(
        bankName = bankName,
        loanName = loanName,
        receivedAmount = receivedAmount,
        monthlyInstallmentAmount = monthlyInstallmentAmount,
        numberOfInstallments = count,
        totalRepayableAmount = totalRepayable,
        totalInterest = totalInterest,
        startDate = startDate,
        description = description,
        isSettled = false
      )

    val installments = buildInstallments(count, startDate, loanName, monthlyInstallmentAmount)

    // Insert loan + its installments atomically so a failure can't leave
    // orphaned installments referencing a missing loan.
    return repository.addBankLoanWithInstallments(bankLoan, installments)
  }

  /**
   * Phase 2 atomic create: bank loan + installments + optional one-time
   * disbursement INCOME post in one withTransaction. tracked=true with
   * recordInitial=true records receivedAmount to accountId; otherwise
   * ledger-only. Installment rows keep ledger-only defaults; each
   * installment's own tracked flag governs its future paid toggles.
   */
  suspend fun addTrackedBankLoan(
    bankName: String,
    loanName: String,
    receivedAmount: Long,
    monthlyInstallmentAmount: Long,
    numberOfInstallments: Int,
    startDate: Long,
    description: String,
    tracked: Boolean,
    accountId: Long? = null,
    recordInitial: Boolean = true
  ): Long {
    io.github.mojri.hesabyar.data.TrackedLedgerHelper
      .validateTrackedAccount(tracked, accountId)
    require(bankName.isNotBlank()) { "bankName must not be blank" }
    require(receivedAmount > 0) { "receivedAmount must be positive" }
    require(monthlyInstallmentAmount > 0) { "monthlyInstallmentAmount must be positive" }
    require(numberOfInstallments >= 0) { "numberOfInstallments must not be negative" }
    require(startDate > 0) { "startDate must be positive" }
    val count = if (numberOfInstallments > 0) numberOfInstallments else 1
    val totalRepayable = Math.multiplyExact(monthlyInstallmentAmount, count.toLong())
    val totalInterest = totalRepayable - receivedAmount
    val bankLoan =
      BankLoan(
        bankName = bankName,
        loanName = loanName,
        receivedAmount = receivedAmount,
        monthlyInstallmentAmount = monthlyInstallmentAmount,
        numberOfInstallments = count,
        totalRepayableAmount = totalRepayable,
        totalInterest = totalInterest,
        startDate = startDate,
        description = description,
        isSettled = false,
        tracked = tracked,
        accountId = accountId
      )
    val installments = buildInstallments(count, startDate, loanName, monthlyInstallmentAmount)
    return repository.addBankLoanWithInstallmentsAndInitial(
      bankLoan,
      installments,
      recordInitial = recordInitial && tracked
    )
  }

  private fun buildInstallments(
    count: Int,
    startDate: Long,
    loanName: String,
    monthlyInstallmentAmount: Long,
  ): List<Installment> {
    val jStart = JalaliCalendarHelper.gregorianToJalali(startDate)
    val monthsPerYear = 12
    return (1..count).map { i ->
      var jYear = jStart.year
      var jMonth = jStart.month + (i - 1)
      while (jMonth > monthsPerYear) {
        jMonth -= monthsPerYear
        jYear += 1
      }
      val dueDay =
        minOf(
          jStart.day,
          JalaliCalendarHelper.getDaysInMonth(jYear, jMonth)
        )
      val dueDate =
        requireNotNull(
          JalaliCalendarHelper.jalaliToGregorian(jYear, jMonth, dueDay)
        ).timeInMillis
      Installment(
        title = "قسط $i از $count - $loanName",
        amount = monthlyInstallmentAmount,
        dueDate = dueDate,
        reminderEnabled = true,
        notes = "",
        bankLoanId = null
      )
    }
  }

  suspend fun updateBankLoan(bankLoan: BankLoan) = repository.updateBankLoan(bankLoan)

  suspend fun deleteBankLoan(bankLoan: BankLoan) = repository.deleteBankLoan(bankLoan)

  suspend fun toggleSettled(id: Long) {
    val loan = repository.getBankLoanById(id) ?: return
    repository.updateBankLoan(loan.copy(isSettled = !loan.isSettled))
  }

  fun installmentsByBankLoan(bankLoanId: Long): Flow<List<Installment>> =
    flow { emit(repository.getInstallmentsByBankLoanId(bankLoanId)) }
}
