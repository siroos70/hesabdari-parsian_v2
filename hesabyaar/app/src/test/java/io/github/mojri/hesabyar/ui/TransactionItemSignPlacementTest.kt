package io.github.mojri.hesabyar.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import io.github.mojri.hesabyar.ui.components.TransactionItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [TransactionItem] signed-amount rendering.
 *
 * The amount block renders the sign and the formatted amount in separate
 * `Text` composables inside an LTR provider (see
 * `CurrencyFormatter.formatSignedParts` — separate Texts prevent the bidi
 * algorithm from reordering the sign across the digits in an RTL layout).
 * The shared Row merges descendants so TalkBack announces the pair as one
 * node instead of two separate focusables.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TransactionItemSignPlacementTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Before
  fun setUp() {
    // Fixed display unit so the expected formatted strings are deterministic
    // regardless of the unit state left behind by other tests.
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  @After
  fun tearDown() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  private fun setAmountUnderRtl(
    amount: Long,
    isIncome: Boolean
  ) {
    composeRule.setContent {
      // Simulate the app's RTL layout: the sign must still render to the
      // LEFT of the digits because the amount block forces LTR paragraph
      // direction and keeps the sign in its own Text.
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        TransactionItem(
          title = "خرید نان",
          amount = amount,
          isIncome = isIncome,
          categoryColor = Color.Gray,
          categoryInitial = "ن",
          transactionId = 42L,
        )
      }
    }
  }

  @Test
  fun negativeAmountRendersMinusSignToTheLeftOfAmount() {
    setAmountUnderRtl(amount = 1_000_000L, isIncome = false)

    val signNode =
      composeRule
        .onNodeWithText("-", useUnmergedTree = true)
        .fetchSemanticsNode()
    val amountNode =
      composeRule
        .onNodeWithText("۱۰۰٬۰۰۰", substring = true, useUnmergedTree = true)
        .fetchSemanticsNode()

    assertTrue(
      "minus sign must render LEFT of the amount digits in RTL layout, " +
        "but sign.left=${signNode.boundsInRoot.left} " +
        "amount.left=${amountNode.boundsInRoot.left}",
      signNode.boundsInRoot.left < amountNode.boundsInRoot.left
    )
  }

  @Test
  fun positiveAmountRendersPlusSignToTheLeftOfAmount() {
    setAmountUnderRtl(amount = 1_000_000L, isIncome = true)

    val signNode =
      composeRule
        .onNodeWithText("+", useUnmergedTree = true)
        .fetchSemanticsNode()
    val amountNode =
      composeRule
        .onNodeWithText("۱۰۰٬۰۰۰", substring = true, useUnmergedTree = true)
        .fetchSemanticsNode()

    assertTrue(
      "plus sign must render LEFT of the amount digits in RTL layout, " +
        "but sign.left=${signNode.boundsInRoot.left} " +
        "amount.left=${amountNode.boundsInRoot.left}",
      signNode.boundsInRoot.left < amountNode.boundsInRoot.left
    )
  }

  @Test
  fun amountRowMergesSignAndAmountIntoOneAccessibilityNode() {
    setAmountUnderRtl(amount = 1_000_000L, isIncome = false)

    // The sign and the amount stay separate Text nodes (the BIDI safeguard
    // from formatSignedParts) — not a single concatenated string.
    assertEquals(
      "sign must be its own text node",
      1,
      composeRule.onAllNodesWithText("-", useUnmergedTree = true).fetchSemanticsNodes().size
    )
    assertEquals(
      "amount must be its own text node",
      1,
      composeRule
        .onAllNodesWithText("۱۰۰٬۰۰۰", substring = true, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .size
    )

    // But the shared Row merges descendants, so in the merged (accessibility)
    // tree exactly one node carries the sign and one node the amount, and
    // that node is displayed.
    assertEquals(
      "merged tree must expose exactly one node for the sign",
      1,
      composeRule.onAllNodesWithText("-").fetchSemanticsNodes().size
    )
    assertEquals(
      "merged tree must expose exactly one node for the amount",
      1,
      composeRule.onAllNodesWithText("۱۰۰٬۰۰۰", substring = true).fetchSemanticsNodes().size
    )
    composeRule.onNodeWithText("-").assertIsDisplayed()
  }

  @Test
  fun transferRendersPositiveAmountNotAsExpense() {
    composeRule.setContent {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        TransactionItem(
          title = "انتقال",
          amount = 1_000_000L,
          isIncome = false,
          isTransfer = true,
          categoryColor = Color.Gray,
          categoryInitial = "ن",
          transactionId = 42L,
        )
      }
    }

    // A transfer is neither income nor expense: it must render a positive (+)
    // sign, not the red, negative minus-expense treatment. (Distinct from the
    // bidi sign-placement tests above.)
    composeRule.onNodeWithText("+", useUnmergedTree = true).assertIsDisplayed()
    assertEquals(
      "transfer must not render a minus-expense sign",
      0,
      composeRule.onAllNodesWithText("-", useUnmergedTree = true).fetchSemanticsNodes().size
    )
  }
}
