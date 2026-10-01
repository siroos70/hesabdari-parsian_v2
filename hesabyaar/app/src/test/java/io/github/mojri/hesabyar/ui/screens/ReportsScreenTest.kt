package io.github.mojri.hesabyar.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ReportsScreenTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Before
  fun setUp() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  @After
  fun tearDown() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  private fun renderAmount(transaction: Transaction) {
    composeRule.setContent {
      MaterialTheme {
        Box(modifier = Modifier.wrapContentSize()) {
          ReportsTransactionAmount(transaction = transaction)
        }
      }
    }
  }

  @Test
  fun reportsScreenTransferTransactionShowsPositiveSignAndTransferSemanticsTag() {
    val transferTx =
      Transaction(
        id = 1L,
        type = TransactionType.TRANSFER,
        categoryId = 0L,
        amount = 500_000L,
        description = "انتقال بین حساب‌ها",
        date = System.currentTimeMillis(),
        accountId = 5L,
        destinationAccountId = 9L
      )

    renderAmount(transferTx)

    composeRule
      .onNodeWithText("+", useUnmergedTree = true)
      .assertIsDisplayed()

    assertTrue(
      "TRANSFER must show a positive sign (+), not a minus (-)",
      composeRule.onAllNodesWithText("-", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    )

    composeRule
      .onNodeWithTag("reports_tx_amount_transfer")
      .assertIsDisplayed()
    composeRule
      .onNodeWithTag("reports_tx_amount_expense")
      .assertIsNotDisplayed()
  }

  @Test
  fun reportsScreenExpenseTransactionShowsMinusSignAndExpenseSemanticsTag() {
    val expenseTx =
      Transaction(
        id = 2L,
        type = TransactionType.EXPENSE,
        categoryId = 0L,
        amount = 300_000L,
        description = "خرج غذا",
        date = System.currentTimeMillis(),
        accountId = 5L,
        destinationAccountId = null
      )

    renderAmount(expenseTx)

    composeRule
      .onNodeWithText("-", useUnmergedTree = true)
      .assertIsDisplayed()

    assertTrue(
      "EXPENSE must show a minus sign (-), not a plus (+)",
      composeRule.onAllNodesWithText("+", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    )

    composeRule
      .onNodeWithTag("reports_tx_amount_expense")
      .assertIsDisplayed()
    composeRule
      .onNodeWithTag("reports_tx_amount_transfer")
      .assertIsNotDisplayed()
  }
}
