package io.github.mojri.hesabyar.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.RestoreMode
import io.github.mojri.hesabyar.domain.usecase.ManageBackupUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * Restore-execution cancellation tests for [BackupImportCoordinator]. Verifies
 * that a coroutine cancellation does not leave [operationState] stuck in
 * [BackupOperationState.Importing] (the in-flight guard would otherwise block retry).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class BackupImportCoordinatorTest {
  private val testDispatcher = StandardTestDispatcher()

  private lateinit var context: Context
  private lateinit var operationState: androidx.compose.runtime.MutableState<BackupOperationState>
  private lateinit var passphraseDialogState: androidx.compose.runtime.MutableState<PassphraseDialogState>
  private lateinit var isCryptoInProgress: androidx.compose.runtime.MutableState<Boolean>

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    context = RuntimeEnvironment.getApplication()
    operationState = mutableStateOf(BackupOperationState.Idle)
    passphraseDialogState = mutableStateOf(PassphraseDialogState.Hidden)
    isCryptoInProgress = mutableStateOf(false)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private data class CoordinatorFixture(
    val coordinator: BackupImportCoordinator,
    val fakeRepo: FakeRepository
  )

  private fun buildCoordinator(): CoordinatorFixture {
    val fakeRepo = FakeRepository()
    return CoordinatorFixture(
      coordinator =
        BackupImportCoordinator(
          application = context,
          manageBackupUseCase = ManageBackupUseCase(fakeRepo, testDispatcher),
          scope = CoroutineScope(testDispatcher),
          operationState = operationState,
          passphraseDialogState = passphraseDialogState,
          isCryptoInProgress = isCryptoInProgress
        ),
      fakeRepo = fakeRepo
    )
  }

  @Test
  fun executeRestoreOnCancellationResetsOperationStateToIdle() =
    runTest {
      val fixture = buildCoordinator()
      val coordinator = fixture.coordinator
      val fakeRepo = fixture.fakeRepo

      coordinator.pendingRestoreBackup.value = BackupPayload(accounts = emptyList())
      coordinator.selectedRestoreMode.value = RestoreMode.REPLACE

      // Simulate the restore coroutine being cancelled mid-flight (e.g. ViewModel
      // cleared during a long-running restore). CancellationException is what
      // Kotlin coroutines throw at a cancellation point.
      fakeRepo.importShouldThrow = CancellationException("simulated scope cancellation")
      coordinator.executeRestore()
      advanceUntilIdle()

      assertTrue(
        "After cancellation, operationState must be Idle, got ${operationState.value}",
        operationState.value is BackupOperationState.Idle
      )
    }

  @Test
  fun validateAndStageImportGuardDropsSubmissionWhileExporting() =
    runTest {
      val coordinator = buildCoordinator().coordinator

      operationState.value = BackupOperationState.Exporting
      coordinator.validateAndStageImport(ByteArrayInputStream("{}".toByteArray()))

      assertTrue(
        "Import guard must drop the submission while an export is in flight, got ${operationState.value}",
        operationState.value is BackupOperationState.Exporting
      )
    }

  @Test
  fun executeRestoreGuardDropsWhileExporting() =
    runTest {
      val fixture = buildCoordinator()
      val coordinator = fixture.coordinator
      val fakeRepo = fixture.fakeRepo

      coordinator.pendingRestoreBackup.value = BackupPayload(accounts = emptyList())
      coordinator.selectedRestoreMode.value = RestoreMode.REPLACE
      operationState.value = BackupOperationState.Exporting

      coordinator.executeRestore()
      advanceUntilIdle()

      assertEquals("Restore must not run while an export is in flight", 0, fakeRepo.executeRestoreCount)
      assertTrue(
        "Restore guard must not overwrite Exporting, got ${operationState.value}",
        operationState.value is BackupOperationState.Exporting
      )
    }

  @Test
  fun decryptRetryDoesNotClearInProgressFlagForNewerJob() =
    runTest {
      val fakeRepo = FakeRepository()
      val scope = CoroutineScope(testDispatcher)
      // Separate schedulers so the retried job is suspended (in-flight) while the
      // cancelled first job's finally block runs. Both jobs share the `scope`
      // (on testDispatcher) but do their parse/crypto work on their own queues.
      val parseScheduler = TestCoroutineScheduler()
      val parseDispatcher = StandardTestDispatcher(parseScheduler)
      val cryptoScheduler = TestCoroutineScheduler()
      val cryptoDispatcher = StandardTestDispatcher(cryptoScheduler)
      val useCase = ManageBackupUseCase(fakeRepo, parseDispatcher)
      val coordinator =
        BackupImportCoordinator(
          application = context,
          manageBackupUseCase = useCase,
          scope = scope,
          operationState = operationState,
          passphraseDialogState = passphraseDialogState,
          isCryptoInProgress = isCryptoInProgress
        )
      coordinator.cryptoDispatcher = cryptoDispatcher
      coordinator.pendingImportRawJson = "{}"
      coordinator.pendingImportSalt = "salt"

      // First attempt: starts and parks its parse on the parse scheduler.
      coordinator.decryptAndStageImport("first")
      testDispatcher.scheduler.runCurrent()
      // Let the parse return; the job then parks on the crypto scheduler.
      parseScheduler.runCurrent()
      testDispatcher.scheduler.runCurrent()
      assertTrue("First decrypt must be in progress", isCryptoInProgress.value)

      // Retry: cancels the first job and launches the second (replaces the tracked job).
      coordinator.decryptAndStageImport("second")
      testDispatcher.scheduler.runCurrent()
      assertTrue("Second decrypt must be in progress", isCryptoInProgress.value)

      // Drive the first job's parse/crypto to finish so its finally block runs
      // while the second job is still suspended (still in-flight).
      parseScheduler.runCurrent()
      cryptoScheduler.advanceUntilIdle()
      testDispatcher.scheduler.runCurrent()

      // The cancelled first job's finally must NOT clear the flag for the newer job.
      assertTrue(
        "isCryptoInProgress must stay true while the newer decrypt job is running",
        isCryptoInProgress.value
      )

      // Let the second job finish so no coroutine leaks past the test.
      cryptoScheduler.advanceUntilIdle()
      testDispatcher.scheduler.runCurrent()
      assertFalse(
        "isCryptoInProgress must be cleared once the owning job completes",
        isCryptoInProgress.value
      )
    }

  @Test
  fun executeRestoreDropsSubmissionWhileCryptoInProgress() =
    runTest {
      val fixture = buildCoordinator()
      val coordinator = fixture.coordinator

      coordinator.pendingRestoreBackup.value = BackupPayload(accounts = emptyList())
      coordinator.selectedRestoreMode.value = RestoreMode.REPLACE
      isCryptoInProgress.value = true

      coordinator.executeRestore()
      advanceUntilIdle()

      assertTrue(
        "Restore must be blocked while crypto is in progress, got ${operationState.value}",
        operationState.value is BackupOperationState.Idle
      )
      assertEquals(
        "Restore must not execute while crypto is in progress",
        0,
        fixture.fakeRepo.executeRestoreCount
      )
    }

  @Test
  fun executeRestoreWithNullPendingBackupDoesNotSetImportingState() =
    runTest {
      val coordinator = buildCoordinator().coordinator

      coordinator.executeRestore()
      advanceUntilIdle()

      assertTrue(
        "State must remain Idle when pendingRestoreBackup is null, got ${operationState.value}",
        operationState.value is BackupOperationState.Idle
      )
    }
}
