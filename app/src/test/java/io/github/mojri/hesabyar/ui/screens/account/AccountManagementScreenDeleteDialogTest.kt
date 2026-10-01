package io.github.mojri.hesabyar.ui.screens.account

import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.ui.AccountViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [resolveDeleteDialogState]: each [AccountViewModel.DeleteCheckResult]
 * must select the correct dialog state — the UI never infers the deletion-block
 * reason from the account list, so a repository failure (CheckFailed) can never
 * surface as the misleading "has active transactions" warning.
 */
class AccountManagementScreenDeleteDialogTest {
  private val account = AccountEntity(id = 1, name = "test", type = AccountType.BANK)

  @Test
  fun canDeleteSelectsDeleteConfirmation() {
    val state =
      resolveDeleteDialogState(AccountViewModel.DeleteCheckResult.CanDelete, account)
    assertEquals(
      "CanDelete must open the delete confirmation",
      AccountDialogState.DeleteConfirmation(account),
      state
    )
  }

  @Test
  fun lastActiveAccountSelectsLastAccountWarning() {
    val state =
      resolveDeleteDialogState(AccountViewModel.DeleteCheckResult.LastActiveAccount, account)
    assertTrue(
      "LastActiveAccount must show the last-account warning, got $state",
      state is AccountDialogState.LastAccountWarning && state.account == account
    )
  }

  @Test
  fun hasTransactionsSelectsTransactionWarning() {
    val state =
      resolveDeleteDialogState(AccountViewModel.DeleteCheckResult.HasTransactions, account)
    assertTrue(
      "HasTransactions must show the transaction warning, got $state",
      state is AccountDialogState.TransactionWarning && state.account == account
    )
  }

  @Test
  fun checkFailedSelectsDeleteCheckErrorWithMessage() {
    val state =
      resolveDeleteDialogState(
        AccountViewModel.DeleteCheckResult.CheckFailed("خطا در بررسی حساب: x"),
        account
      )
    assertTrue(
      "CheckFailed must show the generic error dialog with the failure message, got $state",
      state is AccountDialogState.DeleteCheckError &&
        state.message == "خطا در بررسی حساب: x" &&
        state.account == account
    )
  }
}
