package io.github.mojri.hesabyar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.screens.dashboard.components.TransactionMiniItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [TransactionMiniItem] account color propagation.
 *
 * Verifies that:
 * 1. Account name appears in the subtitle for non-transfer transactions (secondary signal).
 * 2. For transfers, both source and destination account names appear in the subtitle.
 * 3. Transaction description is always rendered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TransactionMiniItemColorTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val bankAccount =
    AccountEntity(
      id = 1L,
      name = "حساب بانکی",
      type = AccountType.BANK,
      color = 0xFF2196F3L, // blue
    )

  private val cashAccount =
    AccountEntity(
      id = 2L,
      name = "کیف پول",
      type = AccountType.CASH_WALLET,
      color = 0xFFFF9800L, // orange
    )

  private val foodCategory =
    Category(
      id = 10L,
      name = "خوراک",
      key = "Food",
      icon = "Restaurant",
      color = 0xFF4CAF50L,
      type = CategoryType.EXPENSE,
    )

  // -- Test a: non-transfer shows account name in subtitle ---------------------

  @Test
  fun nonTransferShowsAccountNameInSubtitle() {
    val transaction =
      Transaction(
        id = 100L,
        type = TransactionType.EXPENSE,
        categoryId = 10L,
        amount = 50_000L,
        description = "ناهار",
        date = 1752580800000L,
        accountId = 1L,
      )

    composeRule.setContent {
      TransactionMiniItem(
        transaction = transaction,
        categories = listOf(foodCategory),
        accounts = listOf(bankAccount, cashAccount),
      )
    }

    // Account name should appear in the subtitle (accessibility secondary signal)
    composeRule.onNode(hasText("حساب بانکی", substring = true)).assertIsDisplayed()
    // Transaction description should be visible
    composeRule.onNodeWithText("ناهار").assertIsDisplayed()
  }

  // -- Test b: transfer shows both account names -------------------------------

  @Test
  fun transferShowsBothAccountNames() {
    val transaction =
      Transaction(
        id = 200L,
        type = TransactionType.TRANSFER,
        categoryId = 10L,
        amount = 100_000L,
        description = "انتقال به کیف پول",
        date = 1752580800000L,
        accountId = 1L,
        destinationAccountId = 2L,
      )

    composeRule.setContent {
      TransactionMiniItem(
        transaction = transaction,
        categories = listOf(foodCategory),
        accounts = listOf(bankAccount, cashAccount),
      )
    }

    // Both account names should appear in the subtitle
    composeRule.onNode(hasText("حساب بانکی", substring = true)).assertIsDisplayed()
    composeRule.onNode(hasText("کیف پول", substring = true)).assertIsDisplayed()
    // Description should be visible
    composeRule.onNodeWithText("انتقال به کیف پول").assertIsDisplayed()
  }

  // -- Test c: no accounts passed — fallback works ----------------------------

  @Test
  fun noAccountsPassedStillRenders() {
    val transaction =
      Transaction(
        id = 300L,
        type = TransactionType.EXPENSE,
        categoryId = 10L,
        amount = 25_000L,
        description = "شام",
        date = 1752580800000L,
        accountId = 1L,
      )

    composeRule.setContent {
      TransactionMiniItem(
        transaction = transaction,
        categories = listOf(foodCategory),
        accounts = emptyList(),
      )
    }

    // Should still render without crash
    composeRule.onNodeWithText("شام").assertIsDisplayed()
  }

  // -- Test d: transfer is neutral + positive, never a red negative expense ----

  @Test
  fun transferRendersPositiveAmountSign() {
    val transaction =
      Transaction(
        id = 400L,
        type = TransactionType.TRANSFER,
        categoryId = 10L,
        amount = 100_000L,
        description = "انتقال به کیف پول",
        date = 1752580800000L,
        accountId = 1L,
        destinationAccountId = 2L,
      )

    composeRule.setContent {
      TransactionMiniItem(
        transaction = transaction,
        categories = listOf(foodCategory),
        accounts = listOf(bankAccount, cashAccount),
      )
    }

    // A transfer is neither income nor expense: it must render a positive (+)
    // sign rather than the red, negative "expense" treatment.
    // NOTE: This test only verifies the positive sign is present; it does not
    // assert the text color. See TransactionMiniItem for the tertiary color used.
    composeRule.onNodeWithText("+").assertIsDisplayed()
  }
}
