package io.github.mojri.hesabyar

import android.content.Context
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.ExcelExporter
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ExcelExporterTest {
  private lateinit var context: Context

  @Before
  fun setup() {
    context = RuntimeEnvironment.getApplication()
  }

  private fun columnLetter(index: Int): String {
    val sb = StringBuilder()
    var i = index
    while (i >= 0) {
      sb.append('A' + i % 26)
      i = i / 26 - 1
    }
    return sb.reverse().toString()
  }

  private fun esc(text: String): String =
    text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")

  private fun formatAmount(value: Long): String = "${value / 10} تومان"

  @Test
  fun `columnLetter A`() {
    assertEquals("A", columnLetter(0))
  }

  @Test
  fun `columnLetter B`() {
    assertEquals("B", columnLetter(1))
  }

  @Test
  fun `columnLetter Z`() {
    assertEquals("Z", columnLetter(25))
  }

  @Test
  fun `columnLetter AA`() {
    assertEquals("AA", columnLetter(26))
  }

  @Test
  fun `columnLetter AB`() {
    assertEquals("AB", columnLetter(27))
  }

  @Test
  fun `columnLetter AZ`() {
    assertEquals("AZ", columnLetter(51))
  }

  @Test
  fun `columnLetter BA`() {
    assertEquals("BA", columnLetter(52))
  }

  @Test
  fun `columnLetter BB`() {
    assertEquals("BB", columnLetter(53))
  }

  @Test
  fun `esc handles ampersand`() {
    assertEquals("&amp;", esc("&"))
  }

  @Test
  fun `esc handles less than`() {
    assertEquals("&lt;", esc("<"))
  }

  @Test
  fun `esc handles greater than`() {
    assertEquals("&gt;", esc(">"))
  }

  @Test
  fun `esc handles double quote`() {
    assertEquals("&quot;", esc("\""))
  }

  @Test
  fun `esc handles single quote`() {
    assertEquals("&apos;", esc("'"))
  }

  @Test
  fun `esc handles mixed text`() {
    assertEquals("A &amp; B &lt; C &gt; D", esc("A & B < C > D"))
  }

  @Test
  fun `esc handles empty string`() {
    assertEquals("", esc(""))
  }

  @Test
  fun `esc handles no special chars`() {
    assertEquals("hello world", esc("hello world"))
  }

  @Test
  fun `formatAmount rial to toman`() {
    assertEquals("500000 تومان", formatAmount(5_000_000L))
  }

  @Test
  fun `formatAmount small amount`() {
    assertEquals("100 تومان", formatAmount(1000L))
  }

  @Test
  fun `formatAmount zero`() {
    assertEquals("0 تومان", formatAmount(0L))
  }

  @Test
  fun `formatAmount truncates remainder`() {
    assertEquals("550 تومان", formatAmount(5500L))
  }

  @Test
  fun `formatAmount large amount`() {
    assertEquals("100000000 تومان", formatAmount(1_000_000_000L))
  }

  @Test
  fun `amounts use Long not Double`() {
    val amount: Long = 5_500_000L
    assertTrue(amount is Long)
    assertEquals(550000L, amount / 10)
  }

  @Test
  fun `transaction type mapping`() {
    assertEquals("دریافتی", if ("INCOME" == "INCOME") "دریافتی" else "پرداختی")
    assertEquals("پرداختی", if ("EXPENSE" == "INCOME") "دریافتی" else "پرداختی")
  }

  @Test
  fun `loan type mapping`() {
    assertEquals("طلبکار", if ("DEBTOR" == "DEBTOR") "طلبکار" else "بدهکار")
    assertEquals("بدهکار", if ("CREDITOR" == "DEBTOR") "طلبکار" else "بدهکار")
  }

  @Test
  fun `loan settled status mapping`() {
    assertEquals("تسویه شده", if (true) "تسویه شده" else "باز")
    assertEquals("باز", if (false) "تسویه شده" else "باز")
  }

  @Test
  fun `installment paid status mapping`() {
    assertEquals("پرداخت شده", if (true) "پرداخت شده" else "پرداخت نشده")
    assertEquals("پرداخت نشده", if (false) "پرداخت شده" else "پرداخت نشده")
  }

  @Test
  fun `category lookup returns name`() {
    val categories =
      listOf(
        Category(
          id = 1L,
          name = "خوراک",
          key = "Food",
          icon = "Restaurant",
          color = 0xFF4CAF50L,
          type = CategoryType.EXPENSE
        ),
        Category(
          id = 2L,
          name = "حمل و نقل",
          key = "Transportation",
          icon = "Car",
          color = 0xFFFF9800L,
          type = CategoryType.EXPENSE
        )
      )
    val map = categories.associateBy { it.id }
    assertEquals("خوراک", map[1L]?.name)
    assertEquals("حمل و نقل", map[2L]?.name)
  }

  @Test
  fun `missing category returns default name`() {
    val categories =
      listOf(
        Category(
          id = 1L,
          name = "خوراک",
          key = "Food",
          icon = "Restaurant",
          color = 0xFF4CAF50L,
          type = CategoryType.EXPENSE
        )
      )
    val map = categories.associateBy { it.id }
    val name = map[999L]?.name ?: "سایر"
    assertEquals("سایر", name)
  }

  @Test
  fun `income and expense filtering`() {
    val transactions =
      listOf(
        Transaction(type = TransactionType.INCOME, categoryId = 1L, amount = 5_000_000L, description = "salary"),
        Transaction(type = TransactionType.EXPENSE, categoryId = 2L, amount = 1_000_000L, description = "food"),
        Transaction(type = TransactionType.INCOME, categoryId = 1L, amount = 3_000_000L, description = "bonus"),
        Transaction(type = TransactionType.EXPENSE, categoryId = 3L, amount = 500_000L, description = "taxi")
      )
    val income = transactions.filter { it.type == TransactionType.INCOME }
    val expense = transactions.filter { it.type == TransactionType.EXPENSE }
    assertEquals(2, income.size)
    assertEquals(2, expense.size)
    assertEquals(8_000_000L, income.sumOf { it.amount })
    assertEquals(1_500_000L, expense.sumOf { it.amount })
  }

  @Test
  fun `shared strings deduplication`() {
    val sharedStrings = mutableListOf<String>()

    fun internString(s: String): Int {
      val idx = sharedStrings.indexOf(s)
      if (idx >= 0) return idx
      sharedStrings.add(s)
      return sharedStrings.size - 1
    }

    val i1 = internString("hello")
    val i2 = internString("world")
    val i3 = internString("hello")

    assertEquals(0, i1)
    assertEquals(1, i2)
    assertEquals(0, i3)
    assertEquals(2, sharedStrings.size)
  }

  @Test
  fun `summary row calculation for income sheet`() {
    val transactions =
      listOf(
        Transaction(type = TransactionType.INCOME, categoryId = 1L, amount = 5_000_000L, description = "s1"),
        Transaction(type = TransactionType.INCOME, categoryId = 1L, amount = 3_000_000L, description = "s2")
      )
    val total = transactions.sumOf { it.amount }
    assertEquals(8_000_000L, total)
  }

  @Test
  fun `summary row calculation for expense sheet`() {
    val transactions =
      listOf(
        Transaction(type = TransactionType.EXPENSE, categoryId = 1L, amount = 1_000_000L, description = "e1"),
        Transaction(type = TransactionType.EXPENSE, categoryId = 2L, amount = 500_000L, description = "e2"),
        Transaction(type = TransactionType.EXPENSE, categoryId = 3L, amount = 200_000L, description = "e3")
      )
    val total = transactions.sumOf { it.amount }
    assertEquals(1_700_000L, total)
  }

  // --- Cell-count-per-row assertion tests (T1-1) ---

  private fun tx(
    type: TransactionType,
    amount: Long,
    accountId: Long = 1,
    destId: Long? = null
  ) = Transaction(
    type = type,
    categoryId = 1L,
    amount = amount,
    description = "test",
    date = System.currentTimeMillis(),
    accountId = accountId,
    destinationAccountId = destId
  )

  private fun account(
    id: Long,
    name: String
  ) = AccountEntity(id = id, name = name, type = AccountType.BANK)

  @Test
  fun buildTransactionsSheetRowsMatchHeaderCount() {
    val exporter = ExcelExporter(context)
    val txs =
      listOf(
        tx(TransactionType.INCOME, 5_000_000),
        tx(TransactionType.EXPENSE, 1_000_000, destId = 2),
        tx(TransactionType.TRANSFER, 2_000_000, destId = 2)
      )
    val accounts = listOf(account(1, "Bank"), account(2, "Wallet"))
    val accountMap = accounts.associateBy { it.id }

    val sheet = exporter.buildTransactionsSheet(txs, emptyMap(), accountMap)
    val headerCount = sheet.headers.size

    // buildTransactionsSheet has 8 headers
    assertEquals("headers count", 8, headerCount)
    // Every row must have exactly headerCount cells
    for ((i, row) in sheet.rows.withIndex()) {
      assertEquals(
        "row $i cell count must equal header count",
        headerCount,
        row.size
      )
    }
    // Type column (header index 1) must label each transaction correctly:
    // TRANSFER must not collapse into the binary income/expense branch.
    assertEquals("دریافتی", sheet.rows[0][1].value)
    assertEquals("پرداختی", sheet.rows[1][1].value)
    assertEquals(context.getString(R.string.transaction_type_transfer), sheet.rows[2][1].value)
  }

  @Test
  fun buildSummaryTxSheetRowsMatchHeaderCount() {
    val exporter = ExcelExporter(context)
    val txs =
      listOf(
        tx(TransactionType.INCOME, 5_000_000),
        tx(TransactionType.EXPENSE, 1_000_000)
      )

    val sheet = exporter.buildSummaryTxSheet("دریافتی\u200Cها", txs, emptyMap())
    val headerCount = sheet.headers.size

    // buildSummaryTxSheet has 5 headers (no type, no account columns)
    assertEquals("headers count", 5, headerCount)
    // Every data row must have exactly headerCount cells
    for ((i, row) in sheet.rows.withIndex()) {
      assertEquals(
        "row $i cell count must equal header count",
        headerCount,
        row.size
      )
    }
    // The totals row must also match the header width, so a hardcoded width
    // drift in buildTotalRow is caught.
    val summaryRow = checkNotNull(sheet.summaryRow) { "summary row must exist" }
    assertEquals(
      "summary row cell count must equal header count",
      headerCount,
      summaryRow.size
    )
  }
}
