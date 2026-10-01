package io.github.mojri.hesabyar.data

import androidx.room.withTransaction
import io.github.mojri.hesabyar.domain.utils.LoansCategoryExclusion
import kotlinx.coroutines.flow.Flow

internal class LoanDelegate(
  private val loanDao: LoanDao,
  private val paymentHistoryDao: PaymentHistoryDao,
  private val transactionDao: TransactionDao,
  private val transactionLinkDao: TransactionLinkDao,
  private val categoryDao: CategoryDao,
  private val database: AppDatabase
) : LoanOps {
  override val allLoans: Flow<List<Loan>> = loanDao.getAllLoans()

  override suspend fun insertLoan(loan: Loan): Long {
    TrackedLedgerHelper.validateTrackedAccount(loan.tracked, loan.accountId)
    val normalizedLoan =
      loan.copy(accountId = TrackedLedgerHelper.normalizeAccountId(loan.tracked, loan.accountId))
    return loanDao.insertLoan(normalizedLoan)
  }

  override suspend fun insertLoanWithInitial(
    loan: Loan,
    recordInitial: Boolean
  ): Long {
    TrackedLedgerHelper.validateTrackedAccount(loan.tracked, loan.accountId)
    val normalizedLoan =
      loan.copy(accountId = TrackedLedgerHelper.normalizeAccountId(loan.tracked, loan.accountId))
    return database.withTransaction {
      val loanId = loanDao.insertLoan(normalizedLoan)
      if (recordInitial && normalizedLoan.tracked) {
        val loansCategory =
          categoryDao.getCategoryByKey(LoansCategoryExclusion.CATEGORY_KEY)
            ?: throw IllegalStateException(
              "Loans category is missing; cannot record the initial loan transaction"
            )
        val accountId = TrackedLedgerHelper.resolveAccountId(normalizedLoan.accountId)
        val stored = loanDao.getLoanById(loanId) ?: normalizedLoan.copy(id = loanId)
        val isDebt = stored.type == LoanType.CREDITOR
        val transactionType = if (isDebt) TransactionType.INCOME else TransactionType.EXPENSE
        transactionDao.insertTransaction(
          Transaction(
            // CREDITOR = "I owe": receiving the money is an inflow, and the
            // description ("دریافت وام") proves it. DEBTOR = "owed to me":
            // handing the money out is the outflow.
            type = transactionType,
            categoryId = loansCategory.id,
            amount = stored.originalAmount,
            description =
              if (isDebt) {
                "دریافت وام از ${stored.personName}"
              } else {
                "پرداخت وام به ${stored.personName}"
              },
            personName = stored.personName,
            personId = stored.personId,
            date = stored.date,
            accountId = accountId
          )
        )
      }
      loanId
    }
  }

  override suspend fun updateLoan(loan: Loan) {
    TrackedLedgerHelper.validateTrackedAccount(loan.tracked, loan.accountId)
    val normalizedLoan =
      loan.copy(accountId = TrackedLedgerHelper.normalizeAccountId(loan.tracked, loan.accountId))
    loanDao.updateLoan(normalizedLoan)
  }

  override suspend fun deleteLoan(loan: Loan) {
    database.withTransaction {
      // Decide from the persisted row when available: the caller may hold a
      // stale snapshot whose tracked flag no longer matches the database.
      val existing = loanDao.getLoanById(loan.id) ?: loan
      // Payments made through addPaymentToLoan each created a standalone
      // expense/income Transaction. Delete them together with the payment
      // history, or reports keep counting money for a loan that no longer
      // exists. Each generated row is identified by the same fields the
      // creator used: personName, Loans category, amount and date.
      val loansCategoryId = categoryDao.getCategoryByKey(LoansCategoryExclusion.CATEGORY_KEY)?.id
      if (loansCategoryId != null) {
        val namesToClean =
          buildSet {
            add(existing.personName)
            add(loan.personName)
          }
        val payments = paymentHistoryDao.getPaymentHistoriesForLoanSync(loan.id)
        for (name in namesToClean) {
          payments.forEach { payment ->
            transactionLinkDao.deleteLoanPaymentTransaction(
              personName = name,
              categoryId = loansCategoryId,
              amount = payment.amount,
              date = payment.date
            )
          }
          if (existing.tracked) {
            transactionLinkDao.deleteLoanPaymentTransaction(
              personName = name,
              categoryId = loansCategoryId,
              amount = existing.originalAmount,
              date = existing.date
            )
            if (loan.originalAmount != existing.originalAmount || loan.date != existing.date) {
              transactionLinkDao.deleteLoanPaymentTransaction(
                personName = name,
                categoryId = loansCategoryId,
                amount = loan.originalAmount,
                date = loan.date
              )
            }
          }
        }
      }
      paymentHistoryDao.deletePaymentHistoryForLoan(loan.id)
      loanDao.deleteLoan(loan)
    }
  }

  override fun getPaymentHistoryForLoan(loanId: Long): Flow<List<PaymentHistory>> =
    paymentHistoryDao.getPaymentHistoryForLoan(loanId)

  override suspend fun addPaymentToLoan(
    loanId: Long,
    amount: Long,
    notes: String,
    customDate: Long?
  ): Boolean {
    if (amount <= 0L) return false
    return database.withTransaction {
      val loan = loanDao.getLoanById(loanId) ?: return@withTransaction false
      // A settled loan must never accept further repayment: a positive
      // remainingAmount on a settled row is stale data, and paying it would
      // resurrect the loan by flipping isSettled back to false.
      if (loan.isSettled || loan.remainingAmount <= 0L) return@withTransaction false
      if (amount > loan.remainingAmount) return@withTransaction false
      val newRemaining = loan.remainingAmount - amount
      val isSettled = newRemaining == 0L
      val date = customDate ?: System.currentTimeMillis()
      // Phase 2 (DECISION 1): untracked loans only reduce the ledger balance
      // and record payment history. Zero transactions are posted — and the
      // Loans category is only required on the tracked path, so untracked
      // repayments keep working after the category was deleted (plan 011 D2).
      // For tracked loans, require the category up front so missing category
      // aborts the transaction cleanly before writing loan/payment mutations.
      val loansCategory =
        if (loan.tracked) {
          categoryDao.getCategoryByKey("Loans")
            ?: throw IllegalStateException(
              "Loans category is missing; cannot record the loan repayment transaction"
            )
        } else {
          null
        }
      val updatedLoan = loan.copy(remainingAmount = newRemaining, isSettled = isSettled)
      val payment = PaymentHistory(loanId = loanId, amount = amount, notes = notes, date = date)
      loanDao.updateLoan(updatedLoan)
      paymentHistoryDao.insertPayment(payment)
      if (loan.tracked && loansCategory != null) {
        val desc =
          if (loan.type == LoanType.CREDITOR) {
            "بازپرداخت بدهی به ${loan.personName} - $notes"
          } else {
            "دریافت بازپرداخت از ${loan.personName} - $notes"
          }
        transactionDao.insertTransaction(
          Transaction(
            type = if (loan.type == LoanType.CREDITOR) TransactionType.EXPENSE else TransactionType.INCOME,
            categoryId = loansCategory.id,
            amount = amount,
            description = desc,
            personName = loan.personName,
            personId = loan.personId,
            date = date,
            accountId = TrackedLedgerHelper.resolveAccountId(loan.tracked, loan.accountId)
          )
        )
      }
      true
    }
  }
}
