package io.github.mojri.hesabyar.ui

import android.content.Context
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.domain.usecase.FakeRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
  private lateinit var viewModel: AccountViewModel
  private lateinit var fakeRepo: FakeRepository
  private lateinit var context: Context
  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    context = RuntimeEnvironment.getApplication()
    fakeRepo = FakeRepository()
    viewModel = AccountViewModel(context, fakeRepo)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun addAccountWithBlankNameReturnsValidationError() =
    runTest {
      val result =
        viewModel.addAccount(
          name = "",
          type = AccountType.BANK
        )
      assertTrue(result is AccountViewModel.AddAccountResult.ValidationError)
    }

  @Test
  fun addAccountWithBlankNameDoesNotInsert() =
    runTest {
      viewModel.addAccount(name = "  ", type = AccountType.CASH_WALLET)
      advanceUntilIdle()
      assertEquals("blank name should not insert", 0, fakeRepo.accountsList.size)
    }

  @Test
  fun addAccountSuccessInsertsAccount() =
    runTest {
      val result =
        viewModel.addAccount(
          name = ".test account",
          type = AccountType.BANK,
          initialBalance = 1000L
        )
      assertTrue(result is AccountViewModel.AddAccountResult.Success)
      advanceUntilIdle()
      assertEquals("should have 1 account", 1, fakeRepo.accountsList.size)
      assertEquals("name should match", ".test account", fakeRepo.accountsList[0].name)
      assertEquals("initial balance should match", 1000L, fakeRepo.accountsList[0].initialBalance)
    }

  @Test
  fun addAccountSetsCorrectDisplayOrder() =
    runTest {
      fakeRepo.accountsList.add(
        AccountEntity(id = 1, name = "existing", type = AccountType.BANK, displayOrder = 5)
      )
      fakeRepo.refreshAccounts()

      viewModel.addAccount(name = "new account", type = AccountType.CASH_WALLET)
      advanceUntilIdle()

      assertEquals("should have 2 accounts", 2, fakeRepo.accountsList.size)
      val newAccount = fakeRepo.accountsList.first { it.name == "new account" }
      assertEquals("displayOrder should be max+1", 6, newAccount.displayOrder)
    }

  @Test
  fun addAccountSetsTimestamps() =
    runTest {
      val before = System.currentTimeMillis()
      viewModel.addAccount(name = ".timestamp test", type = AccountType.OTHER)
      advanceUntilIdle()
      val after = System.currentTimeMillis()

      assertEquals("should have 1 account", 1, fakeRepo.accountsList.size)
      val account = fakeRepo.accountsList[0]
      assertTrue(account.createdAt in before..after)
      assertTrue(account.updatedAt in before..after)
    }

  @Test
  fun updateAccountSetsNewUpdatedAt() =
    runTest {
      val original =
        AccountEntity(
          id = 10,
          name = "old",
          type = AccountType.BANK,
          updatedAt = 100L
        )
      fakeRepo.accountsList.add(original)
      fakeRepo.refreshAccounts()

      viewModel.updateAccount(original.copy(name = "new"))
      advanceUntilIdle()

      val updated = fakeRepo.accountsList.first { it.id == 10L }
      assertEquals("name should be updated", "new", updated.name)
      assertTrue(updated.updatedAt > 100L)
    }

  @Test
  fun deleteAccountRemovesFromRepository() =
    runTest {
      val account = AccountEntity(id = 5, name = "to delete", type = AccountType.BANK)
      fakeRepo.accountsList.add(AccountEntity(id = 1, name = "other", type = AccountType.BANK))
      fakeRepo.accountsList.add(account)
      fakeRepo.refreshAccounts()

      viewModel.deleteAccount(account)
      advanceUntilIdle()

      assertTrue(fakeRepo.accountsList.none { it.id == 5L })
      assertEquals("should have 1 account remaining", 1, fakeRepo.accountsList.size)
    }

  @Test
  fun deleteAccountRejectsDeletingLastAccount() =
    runTest {
      val account = AccountEntity(id = 5, name = "only account", type = AccountType.BANK)
      fakeRepo.accountsList.add(account)
      fakeRepo.refreshAccounts()

      val errorMessages = mutableListOf<String>()
      val job =
        launch {
          viewModel.errorEvents.collect { errorMessages.add(it) }
        }
      advanceUntilIdle()

      viewModel.deleteAccount(account)
      advanceUntilIdle()

      assertEquals("last account should not be deleted", 1, fakeRepo.accountsList.size)
      assertEquals(
        "last-active-account error should be emitted",
        context.getString(R.string.account_delete_last_active_account_error, "only account"),
        errorMessages.single()
      )
      job.cancel()
    }

  @Test
  fun deleteAccountAllowsDeletingArchivedAccountEvenWithStaleEntity() =
    runTest {
      fakeRepo.accountsList.add(AccountEntity(id = 1, name = "active", type = AccountType.BANK))
      fakeRepo.accountsList.add(
        AccountEntity(id = 5, name = "archived", type = AccountType.BANK, isArchived = true)
      )
      fakeRepo.refreshAccounts()

      val errorMessages = mutableListOf<String>()
      val job =
        launch {
          viewModel.errorEvents.collect { errorMessages.add(it) }
        }
      advanceUntilIdle()

      // Stale entity: isArchived=false, but the fresh allAccounts copy is archived.
      val staleEntity = AccountEntity(id = 5, name = "archived", type = AccountType.BANK)
      viewModel.deleteAccount(staleEntity)
      advanceUntilIdle()

      assertTrue("no last-active error must be emitted", errorMessages.isEmpty())
      assertTrue("archived account should be deleted", fakeRepo.accountsList.none { it.id == 5L })
      assertEquals("only the active account remains", 1, fakeRepo.accountsList.size)
      job.cancel()
    }

  @Test
  fun deleteAccountBlockedWhenTransactionsExist() =
    runTest {
      fakeRepo.transactionCountOverride = 3
      val account = AccountEntity(id = 5, name = "has transactions", type = AccountType.BANK)
      fakeRepo.accountsList.add(AccountEntity(id = 1, name = "other", type = AccountType.BANK))
      fakeRepo.accountsList.add(account)
      fakeRepo.refreshAccounts()

      val errorMessages = mutableListOf<String>()
      val job =
        launch {
          viewModel.errorEvents.collect { errorMessages.add(it) }
        }
      advanceUntilIdle()

      viewModel.deleteAccount(account)
      advanceUntilIdle()

      assertTrue(
        "account with transactions should not be deleted",
        fakeRepo.accountsList.any { it.id == 5L }
      )
      assertEquals("should have 2 accounts remaining", 2, fakeRepo.accountsList.size)
      assertEquals(
        "transaction-count error should be emitted",
        context.getString(R.string.account_delete_transaction_count_error, "has transactions", 3),
        errorMessages.single()
      )
      job.cancel()
    }

  @Test
  fun canDeleteAccountReturnsLastActiveAccountForOnlyActiveAccount() =
    runTest {
      val account = AccountEntity(id = 5, name = "only account", type = AccountType.BANK)
      fakeRepo.accountsList.add(account)
      fakeRepo.refreshAccounts()

      var result: AccountViewModel.DeleteCheckResult? = null
      viewModel.canDeleteAccount(account.id) { result = it }
      advanceUntilIdle()

      assertTrue(
        "last active account should report LastActiveAccount, got $result",
        result is AccountViewModel.DeleteCheckResult.LastActiveAccount
      )
    }

  @Test
  fun canDeleteAccountReturnsCanDeleteWhenNoTransactions() =
    runTest {
      var result: AccountViewModel.DeleteCheckResult? = null
      viewModel.canDeleteAccount(1L) { result = it }
      advanceUntilIdle()
      assertTrue(
        "should report CanDelete, got $result",
        result is AccountViewModel.DeleteCheckResult.CanDelete
      )
    }

  @Test
  fun canDeleteAccountReturnsHasTransactionsWhenTransactionsExist() =
    runTest {
      fakeRepo.transactionCountOverride = 3
      var result: AccountViewModel.DeleteCheckResult? = null
      viewModel.canDeleteAccount(1L) { result = it }
      advanceUntilIdle()
      assertTrue(
        "should report HasTransactions, got $result",
        result is AccountViewModel.DeleteCheckResult.HasTransactions
      )
    }

  @Test
  fun canDeleteAccountStaleCallbackSuppressedAfterSameAccountReopen() =
    runTest {
      // Simulates: user opens delete dialog for Account A, dismisses it,
      // then immediately reopens for Account A. The first (slow) check
      // completes AFTER the second (fast) check. The token guard must
      // suppress the first callback so stale data doesn't overwrite the
      // fresh result.
      // Two accounts so Account 1 (the delete target) is NOT the last active account.
      fakeRepo.accountsList.add(AccountEntity(id = 1, name = "test", type = AccountType.BANK))
      fakeRepo.accountsList.add(AccountEntity(id = 2, name = "other", type = AccountType.BANK))
      fakeRepo.refreshAccounts()

      // Gate that suspends the first check's repository call until released.
      val gate = CompletableDeferred<Unit>()
      fakeRepo.txCountGate = gate

      var staleResult: AccountViewModel.DeleteCheckResult? = null
      viewModel.canDeleteAccount(1L) { staleResult = it }

      // Let coroutine #1 run until it suspends at the gate.
      runCurrent()
      assertEquals(
        "first check must not have completed yet (suspended at gate)",
        null,
        staleResult
      )

      // Release the gate; immediately start a second check for the SAME account.
      gate.complete(Unit)
      var freshResult: AccountViewModel.DeleteCheckResult? = null
      viewModel.canDeleteAccount(1L) { freshResult = it }

      // Let both coroutines finish.
      advanceUntilIdle()

      assertNotNull(
        "second (fresh) check must deliver a result",
        freshResult
      )
      assertTrue(
        "fresh check should report CanDelete, got $freshResult",
        freshResult is AccountViewModel.DeleteCheckResult.CanDelete
      )
      assertNull(
        "stale first-check callback must be suppressed by the token guard, got $staleResult",
        staleResult
      )
    }

  @Test
  fun archiveAccountSetsArchivedFlag() =
    runTest {
      val account = AccountEntity(id = 7, name = "archivable", type = AccountType.BANK)
      fakeRepo.accountsList.add(account)
      fakeRepo.refreshAccounts()

      viewModel.archiveAccount(account)
      advanceUntilIdle()

      val archived = fakeRepo.accountsList.first { it.id == 7L }
      assertTrue(archived.isArchived)
    }

  @Test
  fun addAccountErrorEventEmittedOnRepositoryFailure() =
    runTest {
      fakeRepo.shouldThrowOnInsert = true
      val errorMessages = mutableListOf<String>()
      val job =
        launch {
          viewModel.errorEvents.collect { errorMessages.add(it) }
        }
      advanceUntilIdle()

      val result = viewModel.addAccount(name = "fail test", type = AccountType.BANK)
      advanceUntilIdle()

      assertTrue(result is AccountViewModel.AddAccountResult.InsertError)
      assertTrue(errorMessages.isNotEmpty())
      assertTrue(errorMessages[0].contains("خطا"))
      job.cancel()
    }

  @Test
  fun updateAccountErrorEventEmittedOnRepositoryFailure() =
    runTest {
      fakeRepo.shouldThrowOnUpdate = true
      val errorMessages = mutableListOf<String>()
      val job =
        launch {
          viewModel.errorEvents.collect { errorMessages.add(it) }
        }

      val account = AccountEntity(id = 1, name = "test", type = AccountType.BANK)
      fakeRepo.accountsList.add(account)
      fakeRepo.refreshAccounts()
      viewModel.updateAccount(account)
      advanceUntilIdle()

      assertTrue(errorMessages.isNotEmpty())
      assertTrue(errorMessages[0].contains("خطا"))
      job.cancel()
    }

  @Test
  fun deleteAccountErrorEventEmittedOnRepositoryFailure() =
    runTest {
      fakeRepo.shouldThrowOnDelete = true
      val errorMessages = mutableListOf<String>()
      val job =
        launch {
          viewModel.errorEvents.collect { errorMessages.add(it) }
        }

      fakeRepo.accountsList.add(AccountEntity(id = 1, name = "other", type = AccountType.BANK))
      val account = AccountEntity(id = 2, name = "test", type = AccountType.BANK)
      fakeRepo.accountsList.add(account)
      fakeRepo.refreshAccounts()
      viewModel.deleteAccount(account)
      advanceUntilIdle()

      assertTrue(errorMessages.isNotEmpty())
      assertTrue(errorMessages[0].contains("خطا"))
      job.cancel()
    }

  @Test
  fun canDeleteAccountErrorEventEmittedOnRepositoryFailure() =
    runTest {
      fakeRepo.shouldThrowOnTransactionCount = true
      val errorMessages = mutableListOf<String>()
      val job =
        launch {
          viewModel.errorEvents.collect { errorMessages.add(it) }
        }

      var result: AccountViewModel.DeleteCheckResult? = null
      viewModel.canDeleteAccount(1L) { result = it }
      advanceUntilIdle()

      val outcome = result
      assertTrue(errorMessages.isNotEmpty())
      assertTrue(errorMessages[0].contains("خطا"))
      assertTrue(
        "repository failure must report CheckFailed, got $outcome",
        outcome is AccountViewModel.DeleteCheckResult.CheckFailed
      )
      assertEquals(
        "CheckFailed must carry the emitted error message",
        errorMessages.single(),
        (outcome as AccountViewModel.DeleteCheckResult.CheckFailed).message
      )
      job.cancel()
    }

  @Test
  fun archiveAccountErrorEventEmittedOnRepositoryFailure() =
    runTest {
      fakeRepo.shouldThrowOnUpdate = true
      val errorMessages = mutableListOf<String>()
      val job =
        launch {
          viewModel.errorEvents.collect { errorMessages.add(it) }
        }

      val account = AccountEntity(id = 1, name = "test", type = AccountType.BANK)
      fakeRepo.accountsList.add(account)
      fakeRepo.refreshAccounts()
      viewModel.archiveAccount(account)
      advanceUntilIdle()

      assertTrue(errorMessages.isNotEmpty())
      assertTrue(errorMessages[0].contains("خطا"))
      job.cancel()
    }

  @Test
  fun canDeleteAccountStaleErrorSuppressedAfterSameAccountReopen() =
    runTest {
      // Simulates: user opens delete dialog for Account A, a check starts (#1).
      // User dismisses and reopens, starting check #2. When #1 fails, its error
      // must NOT be emitted to errorEvents — the token guard must suppress it
      // just as it suppresses the stale onResult callback.
      fakeRepo.accountsList.add(AccountEntity(id = 1, name = "test", type = AccountType.BANK))
      fakeRepo.accountsList.add(AccountEntity(id = 2, name = "other", type = AccountType.BANK))
      fakeRepo.refreshAccounts()
      fakeRepo.shouldThrowOnTransactionCount = true

      var staleResult: AccountViewModel.DeleteCheckResult? = null
      var freshResult: AccountViewModel.DeleteCheckResult? = null
      val errorMessages = mutableListOf<String>()
      val job =
        launch {
          viewModel.errorEvents.collect { errorMessages.add(it) }
        }
      advanceUntilIdle()

      // Start check #1 (token=1) and #2 (token=2) without running either yet.
      viewModel.canDeleteAccount(1L) { staleResult = it }
      viewModel.canDeleteAccount(1L) { freshResult = it }

      advanceUntilIdle()

      assertNull(
        "stale check's callback must be suppressed by the token guard, got $staleResult",
        staleResult
      )
      assertNotNull(
        "fresh check must deliver a result, got $freshResult",
        freshResult
      )
      assertTrue(
        "fresh check should report CheckFailed, got $freshResult",
        freshResult is AccountViewModel.DeleteCheckResult.CheckFailed
      )
      assertEquals(
        "only the fresh check's error must be emitted, not the stale one",
        1,
        errorMessages.size
      )
      job.cancel()
    }

  @Test
  fun deleteAccountRepositoryExceptionEmitsLocalizedMessageNotEnglish() =
    runTest {
      // Simulate a TOCTOU race: the ViewModel's pre-check sees 2 active accounts
      // (so it does NOT short-circuit), but the repository throw is forced via
      // forceLastActiveAccountException — mirroring a concurrent deletion between
      // the pre-check and the repository's own check.
      fakeRepo.forceLastActiveAccountException = true
      fakeRepo.accountsList.add(AccountEntity(id = 1, name = "other", type = AccountType.BANK))
      val account = AccountEntity(id = 2, name = "test", type = AccountType.BANK)
      fakeRepo.accountsList.add(account)
      fakeRepo.refreshAccounts()

      val errorMessages = mutableListOf<String>()
      val job =
        launch {
          viewModel.errorEvents.collect { errorMessages.add(it) }
        }
      advanceUntilIdle()

      viewModel.deleteAccount(account)
      advanceUntilIdle()

      assertEquals(
        "repository exception must emit the localized string",
        context.getString(R.string.account_delete_last_active_account_error, "test"),
        errorMessages.single()
      )
      assertTrue(
        "English exception message must not leak to the user, got: $errorMessages",
        !errorMessages.any { it.contains("is the last remaining active account") }
      )
      job.cancel()
    }
}
