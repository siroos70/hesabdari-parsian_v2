package io.github.mojri.hesabyar.ui

import android.content.Context
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.domain.usecase.GetSettingsUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageBackupUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Restore-execution failure-path tests for BackupImportCoordinator. Kept as its own
 * class (split out of BackupViewModelTest) so detekt LargeClass stays under the
 * threshold while both classes share [FakeRepository].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreFailureTest {
  private lateinit var viewModel: BackupViewModel
  private lateinit var fakeRepo: FakeRepository
  private lateinit var context: Context
  private lateinit var useCase: ManageBackupUseCase
  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    context = RuntimeEnvironment.getApplication()
    fakeRepo = FakeRepository()
    useCase = ManageBackupUseCase(fakeRepo, testDispatcher)
    viewModel =
      BackupViewModel(
        context,
        useCase,
        GetSettingsUseCase(context.getSharedPreferences("hesabyar_prefs", Context.MODE_PRIVATE))
      )
    viewModel.importCoordinator.ioDispatcher = testDispatcher
    viewModel.importCoordinator.cryptoDispatcher = testDispatcher
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun executeRestoreWhenGenericDbErrorSurfacesAsErrorState() =
    runTest {
      // Stage a genuinely encrypted backup end-to-end so executeRestore has a real
      // payload to write (same staging shape as BackupViewModelTest's restore tests).
      fakeRepo.accountsList +=
        AccountEntity(
          id = 1,
          name = "کارت نمونه",
          type = AccountType.BANK,
          bankName = "ملی",
          cardNumber = "6037-9971-1234-5678",
          accountNumber = "123456789",
          iban = "IR123456789012345678901234"
        )
      val encryptedJson = useCase.exportBackupJson(passphrase = "secret")
      val rawJson = encryptedJson.toString(2)
      val salt =
        requireNotNull(ManageBackupUseCase.getEncryptionSalt(encryptedJson)) {
          "Encrypted backup must carry a PBKDF2 salt"
        }
      viewModel.importCoordinator.pendingImportRawJson = rawJson
      viewModel.importCoordinator.pendingImportSalt = salt
      viewModel.importCoordinator.decryptAndStageImport("secret")
      testDispatcher.scheduler.advanceUntilIdle()
      assertTrue(
        "Backup must be staged before the DB-failure simulation, pending=${viewModel.pendingRestoreBackup.value}",
        viewModel.pendingRestoreBackup.value != null
      )

      // A Room/SQLite failure surfaces as a plain runtime exception — not one of the
      // specific IOException/SecurityException/IllegalArgumentException cases already
      // handled. It must be converted to an Error state (not escape the coroutine
      // uncaught, which would crash the scope / leave the UI stuck in Importing).
      fakeRepo.importShouldThrow = IllegalStateException("simulated Room database failure")
      viewModel.importCoordinator.executeRestore()
      testDispatcher.scheduler.advanceUntilIdle()

      val state = viewModel.operationState.value
      assertTrue(
        "A generic DB failure must surface as Error, got $state",
        state is BackupOperationState.Error
      )
      assertTrue(
        "Error message must be the generic restore-failure message, got $state",
        (state as BackupOperationState.Error).message.contains("بازگردانی")
      )
    }
}
