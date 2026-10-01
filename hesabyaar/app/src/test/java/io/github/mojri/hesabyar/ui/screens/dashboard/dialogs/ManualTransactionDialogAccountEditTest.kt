package io.github.mojri.hesabyar.ui.screens.dashboard.dialogs

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.usecase.SubmitManualTransactionUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the edit-mode account selection in
 * [ManualTransactionDialog]: the source account must be held in local dialog
 * state seeded from the transaction, so an edit can move the transaction to
 * another account, and a caller that omits the account params (the Reports
 * edit path before the fix) still submits with the transaction's own account
 * instead of being blocked by the source-account validation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ManualTransactionDialogAccountEditTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val accounts =
    listOf(
      AccountEntity(id = 10L, name = "Bank A", type = AccountType.BANK),
      AccountEntity(id = 20L, name = "Cash B", type = AccountType.CASH_WALLET),
    )

  private val categories =
    listOf(
      Category(id = 1L, name = "Food", key = "Food", icon = "", color = 0L, type = CategoryType.EXPENSE),
    )

  private val original =
    Transaction(
      id = 1L,
      type = TransactionType.EXPENSE,
      categoryId = 1L,
      amount = 5000L,
      description = "Old",
      date = System.currentTimeMillis(),
      accountId = 10L,
    )

  private val transferOriginal =
    Transaction(
      id = 1L,
      type = TransactionType.TRANSFER,
      categoryId = 1L,
      amount = 5_000L,
      description = "انتقال",
      date = System.currentTimeMillis(),
      accountId = 10L,
      destinationAccountId = 20L,
    )

  private fun launchEditDialog(
    seedAccountId: Long?,
    captured: androidx.compose.runtime.MutableState<SubmitManualTransactionUseCase.SubmitManualTransactionRequest?>
  ) {
    composeRule.setContent {
      ManualTransactionDialog(
        onSubmit = { request ->
          captured.value = request
          SubmitManualTransactionUseCase.SubmitResult(success = true)
        },
        categories = categories,
        transactionToEdit = original,
        accounts = accounts,
        selectedAccountId = seedAccountId,
        onDismiss = {},
      )
    }
  }

  // -- Reports/Dashboard edit contract: explicit seed from the transaction -----

  @Test
  fun editDialogSubmitsWithOriginalAccountWhenSelectionUnchanged() {
    val captured = mutableStateOf<SubmitManualTransactionUseCase.SubmitManualTransactionRequest?>(null)

    launchEditDialog(seedAccountId = 10L, captured = captured)

    // The dialog is seeded from the transaction's account.
    composeRule.onAllNodesWithText("Bank A")[0].assertIsSelected()
    composeRule.onNodeWithText("ذخیره تغییرات").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) { captured.value != null }

    val request = captured.value
    assertNotNull("Submit must not be blocked", request)
    assertEquals("Edit must keep the original account when unchanged", 10L, request?.accountId)
    assertEquals("Edit must keep the original transaction reference", original, request?.transactionToEdit)
  }

  // -- Safety net: caller omits the account params (pre-fix Reports path) ------

  @Test
  fun editDialogWithoutAccountParamsFallsBackToTransactionAccount() {
    val captured = mutableStateOf<SubmitManualTransactionUseCase.SubmitManualTransactionRequest?>(null)

    launchEditDialog(seedAccountId = null, captured = captured)

    // No account params passed: the selection must be seeded from the
    // transaction itself, so submit is not blocked by the source-account check.
    composeRule.onAllNodesWithText("Bank A")[0].assertIsSelected()
    composeRule.onNodeWithText("ذخیره تغییرات").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) { captured.value != null }

    val request = captured.value
    assertNotNull("Submit must not be blocked", request)
    assertEquals("Edit without account params must keep the original account", 10L, request?.accountId)
  }

  // -- The finding itself: moving the transaction to another account ----------

  @Test
  fun editDialogSelectingDifferentAccountMovesTransactionOnSubmit() {
    val captured = mutableStateOf<SubmitManualTransactionUseCase.SubmitManualTransactionRequest?>(null)

    launchEditDialog(seedAccountId = 10L, captured = captured)

    composeRule.onNodeWithText("Cash B").performClick()
    composeRule.onNodeWithText("ذخیره تغییرات").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) { captured.value != null }

    val request = captured.value
    assertNotNull("Submit must not be blocked", request)
    assertEquals("Selecting another account must move the transaction on edit", 20L, request?.accountId)
  }

  // -- Edit-mode type selector must offer TRANSFER (FINDING D) -------------------

  @Test
  fun editModeSelectsTransferChipWhenEditingTransfer() {
    val captured = mutableStateOf<SubmitManualTransactionUseCase.SubmitManualTransactionRequest?>(null)
    composeRule.setContent {
      ManualTransactionDialog(
        onSubmit = { request ->
          captured.value = request
          SubmitManualTransactionUseCase.SubmitResult(success = true)
        },
        categories = categories,
        transactionToEdit = transferOriginal,
        accounts = accounts,
        selectedAccountId = 10L,
        onDismiss = {},
      )
    }
    // Editing an existing TRANSFER: the edit-mode chip list must include and
    // select "انتقال". Pre-fix the edit list only had EXPENSE/INCOME, so the
    // TRANSFER chip was absent and nothing was selected.
    // The description field of transferOriginal also contains "انتقال", so
    // onAllNodesWithText("انتقال") matches two nodes with no guaranteed order
    // (the description text field exposes an OnClick too). Target the chip alone:
    // it is the only node with both this text and Selectable semantics.
    val transferChip = composeRule.onNode(hasText("انتقال").and(isSelectable()))
    transferChip.assertIsDisplayed()
    transferChip.assertIsSelected()
  }
}
