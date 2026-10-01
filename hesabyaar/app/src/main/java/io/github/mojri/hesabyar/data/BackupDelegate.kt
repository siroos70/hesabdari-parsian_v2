package io.github.mojri.hesabyar.data

import androidx.room.withTransaction
import io.github.mojri.hesabyar.domain.utils.PersonNameNormalizer

internal class BackupDelegate(
  private val transactionDao: TransactionDao,
  private val loanDao: LoanDao,
  private val installmentDao: InstallmentDao,
  private val paymentHistoryDao: PaymentHistoryDao,
  private val bankLoanDao: BankLoanDao,
  private val categoryDao: CategoryDao,
  private val accountDao: AccountDao,
  private val personDao: PersonDao,
  private val database: AppDatabase
) : BackupOps {
  override suspend fun importBackup(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    paymentHistories: List<PaymentHistory>,
    bankLoans: List<BankLoan>
  ) = database.withTransaction {
    transactionDao.deleteAllTransactions()
    loanDao.deleteAllLoans()
    installmentDao.deleteAllInstallments()
    paymentHistoryDao.deleteAllPaymentHistory()
    bankLoanDao.deleteAllBankLoans()
    transactions.forEach { transactionDao.insertTransaction(it) }
    loans.forEach { loanDao.insertLoan(normalizeLoanForRestore(it)) }
    installments.forEach { installmentDao.insertInstallment(normalizeInstallmentForRestore(it)) }
    paymentHistories.forEach { paymentHistoryDao.insertPayment(it) }
    bankLoans.forEach { bankLoanDao.insertBankLoan(normalizeBankLoanForRestore(it)) }
  }

  override suspend fun getAllPaymentHistories(): List<PaymentHistory> = paymentHistoryDao.getAllPaymentHistories()

  override suspend fun replaceAllFromBackup(backup: BackupPayload) =
    database.withTransaction {
      backupClearAllTables(
        transactionDao,
        loanDao,
        installmentDao,
        paymentHistoryDao,
        bankLoanDao,
        accountDao,
        personDao
      )
      backupReseedDefaultAccountIfNeeded(accountDao, backup.accounts.isEmpty())
      // The backup always carries the full category set, so clear local
      // categories first: REPLACE must mirror the backup exactly and not keep
      // stale custom or renamed categories behind.
      categoryDao.deleteAllCategories()
      backup.categories.forEach { categoryDao.insertCategory(it) }
      val personsToInsert = withRecoveredReferencedPersons(backup)
      val personMaps =
        backupInsertPersonsForReplace(personsToInsert, personDao, backup.persons.isEmpty())
      backupInsertLoansWithPersonRemap(backup.loans, personMaps, loanDao)
      backupInsertTransactionsWithPersonRemap(backup.transactions, personMaps, transactionDao)
      backup.installments.forEach { installmentDao.insertInstallment(normalizeInstallmentForRestore(it)) }
      backup.paymentHistories.forEach { paymentHistoryDao.insertPayment(it) }
      backup.bankLoans.forEach { bankLoanDao.insertBankLoan(normalizeBankLoanForRestore(it)) }
      backup.accounts.forEach { accountDao.insert(it) }
    }

  override suspend fun mergeFromBackup(backup: BackupPayload) =
    database.withTransaction {
      val categoryIdMap = backupMergeCategories(backup.categories, categoryDao)
      // Legacy backups predate the persons array; referenced-only identities
      // (a loan/transaction pointing at a person id the persons list never
      // carried) are absent from it. Both cases are healed here so every
      // referenced name resolves during the merge below.
      val personsToMerge = withRecoveredReferencedPersons(backup)
      val personKeyToId = backupMergePersons(personsToMerge, personDao)
      // Map every referenced source person id — not only the rows carried in
      // backup.persons — so loans/transactions pointing at an id present in
      // the persons list always resolve. Referenced ids with no person entry
      // map to an empty key and fall back to name resolution.
      val sourceIdToKey =
        personsToMerge.associate {
          it.id to PersonNameNormalizer.normalize(PersonNameNormalizer.displayForm(it.name))
        } +
          (
            backup.loans.mapNotNull { it.personId } +
              backup.transactions.mapNotNull { it.personId }
          ).associateWith { id ->
            personsToMerge
              .firstOrNull { p -> p.id == id }
              ?.let { p -> PersonNameNormalizer.normalize(PersonNameNormalizer.displayForm(p.name)) }
              .orEmpty()
          }
      val personMaps =
        PersonKeyMaps(
          sourceIdToKey = sourceIdToKey,
          keyToLocalId = personKeyToId,
          allowNullIdNameFallback = backup.persons.isEmpty()
        )

      fun resolveForMerge(
        sourcePersonId: Long?,
        fallbackName: String?
      ): Long? = resolvePersonId(sourcePersonId, fallbackName, personMaps)
      val accountIdMap = backupMergeAccounts(backup.accounts, accountDao)
      val loanIdMap = backupMergeLoans(backup.loans, loanDao, accountIdMap, ::resolveForMerge)
      val bankLoanIdMap = backupMergeBankLoans(backup.bankLoans, bankLoanDao, accountIdMap)
      val installmentIdMap =
        backupMergeInstallments(
          backup.installments,
          installmentDao,
          bankLoanIdMap,
          accountIdMap
        )
      backupMergeTransactions(
        backup.transactions,
        categoryIdMap,
        installmentIdMap,
        accountIdMap,
        categoryDao,
        accountDao,
        transactionDao,
        ::resolveForMerge
      )
      backupMergePaymentHistories(backup.paymentHistories, loanIdMap, paymentHistoryDao)
    }
}
