package io.github.mojri.hesabyar.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.rust.Cell
import io.github.mojri.hesabyar.rust.RustBridge
import io.github.mojri.hesabyar.rust.SheetData
import io.github.mojri.hesabyar.rust.WorkbookData
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import java.util.Calendar

class ExcelExporter(
  private val context: Context
) {
  data class ExportResult(
    val bytes: ByteArray,
    val filename: String,
    val transactionCount: Int,
    val incomeCount: Int,
    val expenseCount: Int,
    val loanCount: Int,
    val installmentCount: Int,
    val bankLoanCount: Int,
    val accountCount: Int
  )

  suspend fun export(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
    bankLoans: List<BankLoan> = emptyList(),
    accounts: List<AccountEntity> = emptyList(),
  ): ExportResult {
    val categoryMap = categories.associateBy { it.id }
    val accountMap = accounts.associateBy { it.id }
    val incomeTransactions = transactions.filter { it.type == TransactionType.INCOME }
    val expenseTransactions = transactions.filter { it.type == TransactionType.EXPENSE }

    val sheets =
      listOf(
        buildAccountsSheet(accounts),
        buildTransactionsSheet(transactions, categoryMap, accountMap),
        buildIncomeSheet(incomeTransactions, categoryMap),
        buildExpensesSheet(expenseTransactions, categoryMap),
        buildLoansSheet(loans),
        buildInstallmentsSheet(installments),
        buildBankLoansSheet(bankLoans)
      )

    val bytes =
      RustBridge.generateExcelSync(WorkbookData(sheets))
        ?: throw IllegalStateException("Rust Excel generation failed or unavailable")

    return ExportResult(
      bytes = bytes,
      filename = generateFilename(),
      transactionCount = transactions.size,
      incomeCount = incomeTransactions.size,
      expenseCount = expenseTransactions.size,
      loanCount = loans.size,
      installmentCount = installments.size,
      bankLoanCount = bankLoans.size,
      accountCount = accounts.size
    )
  }

  // ─── Sheet builders ──────────────────────────────────────────────

  private companion object {
    const val HEADER_CATEGORY = "دسته\u200Cبندی"
    const val HEADER_DESCRIPTION = "توضیحات"
  }

  @VisibleForTesting
  internal fun buildTransactionsSheet(
    transactions: List<Transaction>,
    categoryMap: Map<Long, Category>,
    accountMap: Map<Long, AccountEntity> = emptyMap()
  ): SheetData {
    val headers =
      listOf(
        "ردیف",
        "نوع",
        HEADER_CATEGORY,
        "مبلغ",
        HEADER_DESCRIPTION,
        "تاریخ",
        "حساب مبدأ",
        "حساب مقصد"
      )
    val rows = buildTxRows(transactions, categoryMap, includeType = true, accountMap = accountMap)
    return SheetData(name = "همه تراکنش\u200Cها", headers = headers, rows = rows, summaryRow = null)
  }

  @VisibleForTesting
  internal fun buildSummaryTxSheet(
    name: String,
    transactions: List<Transaction>,
    categoryMap: Map<Long, Category>
  ): SheetData {
    val headers = listOf("ردیف", HEADER_CATEGORY, "مبلغ", HEADER_DESCRIPTION, "تاریخ")
    val rows = buildTxRows(transactions, categoryMap, includeType = false)
    val summary = buildTotalRow(transactions.sumOf { it.amount })
    return SheetData(name = name, headers = headers, rows = rows, summaryRow = summary)
  }

  private fun buildIncomeSheet(
    incomeTransactions: List<Transaction>,
    categoryMap: Map<Long, Category>
  ) = buildSummaryTxSheet("دریافتی\u200Cها", incomeTransactions, categoryMap)

  private fun buildExpensesSheet(
    expenseTransactions: List<Transaction>,
    categoryMap: Map<Long, Category>
  ) = buildSummaryTxSheet("پرداختی\u200Cها", expenseTransactions, categoryMap)

  private fun buildTxRows(
    transactions: List<Transaction>,
    categoryMap: Map<Long, Category>,
    includeType: Boolean,
    accountMap: Map<Long, AccountEntity>? = null
  ): List<List<Cell>> =
    transactions.mapIndexed { index, tx ->
      buildList {
        add(Cell(value = (index + 1).toString(), bold = false))
        if (includeType) {
          add(
            Cell(
              value =
                when (tx.type) {
                  TransactionType.INCOME -> "دریافتی"
                  // Uses transaction_type_transfer string resource for consistency with
                  // TransactionDetailDialog — transfers must not collapse into the
                  // binary income/expense branch.
                  TransactionType.TRANSFER -> context.getString(R.string.transaction_type_transfer)
                  else -> "پرداختی"
                },
              bold = false
            )
          )
        }
        add(Cell(value = categoryMap[tx.categoryId]?.name ?: "سایر", bold = false))
        add(Cell(value = formatAmount(tx.amount), bold = false))
        add(Cell(value = tx.description, bold = false))
        add(Cell(value = formatDate(tx.date), bold = false))
        if (accountMap != null) {
          add(Cell(value = accountMap[tx.accountId]?.name.orEmpty(), bold = false))
          add(Cell(value = tx.destinationAccountId?.let { accountMap[it]?.name }.orEmpty(), bold = false))
        }
      }
    }

  private fun buildTotalRow(total: Long): List<Cell> =
    listOf(
      Cell(value = "", bold = false),
      Cell(value = "مجموع:", bold = true),
      Cell(value = formatAmount(total), bold = true),
      Cell(value = "", bold = false),
      Cell(value = "", bold = false)
    )

  private fun buildLoansSheet(loans: List<Loan>): SheetData {
    val headers = listOf("ردیف", "نام شخص", "نوع", "مبلغ اولیه", "مبلغ باقیمانده", "توضیحات", "تاریخ", "وضعیت")
    val rows =
      loans.mapIndexed { index, loan ->
        listOf(
          Cell(value = (index + 1).toString(), bold = false),
          Cell(value = loan.personName, bold = false),
          Cell(value = if (loan.type == LoanType.DEBTOR) "طلبکار" else "بدهکار", bold = false),
          Cell(value = formatAmount(loan.originalAmount), bold = false),
          Cell(value = formatAmount(loan.remainingAmount), bold = false),
          Cell(value = loan.description, bold = false),
          Cell(value = formatDate(loan.date), bold = false),
          Cell(value = if (loan.isSettled) "تسویه شده" else "باز", bold = false)
        )
      }
    return SheetData(name = "وام\u200Cها و قرض\u200Cها", headers = headers, rows = rows, summaryRow = null)
  }

  private fun buildInstallmentsSheet(installments: List<Installment>): SheetData {
    val headers = listOf("ردیف", "عنوان", "مبلغ", "تاریخ سررسید", "وضعیت", "یادداشت")
    val rows =
      installments.mapIndexed { index, inst ->
        listOf(
          Cell(value = (index + 1).toString(), bold = false),
          Cell(value = inst.title, bold = false),
          Cell(value = formatAmount(inst.amount), bold = false),
          Cell(value = formatDate(inst.dueDate), bold = false),
          Cell(value = if (inst.isPaid) "پرداخت شده" else "پرداخت نشده", bold = false),
          Cell(value = inst.notes, bold = false)
        )
      }
    return SheetData(name = "اقساط", headers = headers, rows = rows, summaryRow = null)
  }

  private fun buildBankLoansSheet(bankLoans: List<BankLoan>): SheetData {
    val headers =
      listOf(
        "ردیف",
        "نام بانک",
        "نام وام",
        "مبلغ دریافتی",
        "قسط ماهانه",
        "تعداد اقساط",
        "مبلغ کل بازپرداخت",
        "سود کل",
        "تاریخ شروع",
        "وضعیت",
        HEADER_DESCRIPTION
      )
    val rows =
      bankLoans.mapIndexed { index, bl ->
        listOf(
          Cell(value = (index + 1).toString(), bold = false),
          Cell(value = bl.bankName, bold = false),
          Cell(value = bl.loanName, bold = false),
          Cell(value = formatAmount(bl.receivedAmount), bold = false),
          Cell(value = formatAmount(bl.monthlyInstallmentAmount), bold = false),
          Cell(value = bl.numberOfInstallments.toString(), bold = false),
          Cell(value = formatAmount(bl.totalRepayableAmount), bold = false),
          Cell(value = formatAmount(bl.totalInterest), bold = false),
          Cell(value = formatDate(bl.startDate), bold = false),
          Cell(value = if (bl.isSettled) "تسویه شده" else "باز", bold = false),
          Cell(value = bl.description, bold = false)
        )
      }
    return SheetData(name = "وام\u200Cهای بانکی", headers = headers, rows = rows, summaryRow = null)
  }

  private fun buildAccountsSheet(accounts: List<AccountEntity>): SheetData {
    val headers = listOf("ردیف", "نام حساب", "نوع حساب", "نام بانک", "موجودی اولیه", "وضعیت")
    val rows =
      accounts.mapIndexed { index, account ->
        listOf(
          Cell(value = (index + 1).toString(), bold = false),
          Cell(value = account.name, bold = false),
          Cell(value = account.type.displayName, bold = false),
          Cell(value = account.bankName.orEmpty(), bold = false),
          Cell(value = formatAmount(account.initialBalance), bold = false),
          Cell(value = if (account.isArchived) "آرشیو" else "فعال", bold = false)
        )
      }
    return SheetData(name = "حساب\u200Cها", headers = headers, rows = rows, summaryRow = null)
  }

  // ─── Helpers ─────────────────────────────────────────────────────

  private fun formatAmount(value: Long): String = CurrencyFormatter.format(value)

  private fun formatDate(timestamp: Long): String {
    val jDate = JalaliCalendarHelper.gregorianToJalali(timestamp)
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    return "${jDate.year}/${jDate.month.toString().padStart(
      2,
      '0'
    )}/${jDate.day.toString().padStart(
      2,
      '0'
    )} - ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
  }

  private fun generateFilename(): String {
    val cal = Calendar.getInstance()
    val jalali =
      io.github.mojri.hesabyar.ui.JalaliCalendarHelper
        .gregorianToJalali(cal.timeInMillis)
    val h = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val min = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
    val s = cal.get(Calendar.SECOND).toString().padStart(2, '0')
    return "hesabyar_report_${jalali.year}${jalali.month.toString().padStart(
      2,
      '0'
    )}${jalali.day.toString().padStart(2, '0')}_${h}${min}$s.xlsx"
  }
}
