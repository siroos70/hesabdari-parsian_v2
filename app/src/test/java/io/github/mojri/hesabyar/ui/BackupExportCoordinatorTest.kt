package io.github.mojri.hesabyar.ui

import android.content.Context
import io.github.mojri.hesabyar.domain.usecase.GetSettingsUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageBackupUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.io.ByteArrayOutputStream

/**
 * Export-flow coordinator tests extracted from [BackupViewModelTest] to keep
 * that class under detekt's LargeClass threshold. Covers the plaintext-export
 * busy guard and the threading of the persisted dark-mode setting into the
 * exported JSON.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class BackupExportCoordinatorTest {
  private lateinit var viewModel: BackupViewModel
  private lateinit var fakeRepo: FakeRepository
  private lateinit var context: Context
  private var originalDarkMode = false
  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    context = RuntimeEnvironment.getApplication()
    originalDarkMode =
      context.getSharedPreferences("hesabyar_prefs", Context.MODE_PRIVATE).getBoolean("dark_mode", true)
    fakeRepo = FakeRepository()
    viewModel =
      BackupViewModel(
        context,
        ManageBackupUseCase(fakeRepo, testDispatcher),
        GetSettingsUseCase(context.getSharedPreferences("hesabyar_prefs", Context.MODE_PRIVATE))
      )
    viewModel.exportCoordinator.ioDispatcher = testDispatcher
    viewModel.exportCoordinator.cryptoDispatcher = testDispatcher
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    // Restore the original dark-mode pref so the value we mutate in the
    // dark-mode test cannot leak into other tests that share this process.
    context
      .getSharedPreferences("hesabyar_prefs", Context.MODE_PRIVATE)
      .edit()
      .putBoolean("dark_mode", originalDarkMode)
      .apply()
  }

  @Test
  fun exportWithoutPassphraseIgnoresConcurrentDuplicateSubmission() =
    runTest {
      // Re-enter exportWithoutPassphrase after staging already started: the
      // synchronous isCryptoInProgress guard must drop the redundant call so the
      // repository flow is only read once (mirrors executeRestore's Importing guard).
      viewModel.exportCoordinator.exportWithoutPassphrase()
      viewModel.exportCoordinator.exportWithoutPassphrase()

      testDispatcher.scheduler.advanceUntilIdle()

      assertEquals(
        "export flow must be read exactly once despite double submission",
        1,
        fakeRepo.exportCategoryReadCount
      )
      assertTrue(
        "Expected Exporting after staging, got ${viewModel.operationState.value}",
        viewModel.operationState.value is BackupOperationState.Exporting
      )
    }

  @Test
  fun exportWritesActualDarkModeSettingIntoJson() =
    runTest {
      // Simulate light mode persisted (darkMode=false), then export and confirm
      // the exact value is embedded rather than a hardcoded true.
      context
        .getSharedPreferences("hesabyar_prefs", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("dark_mode", false)
        .commit()

      viewModel.exportCoordinator.exportWithoutPassphrase()
      testDispatcher.scheduler.advanceUntilIdle()

      val outputStream = ByteArrayOutputStream()
      viewModel.exportCoordinator.writeStagedExportToFile(outputStream)
      testDispatcher.scheduler.advanceUntilIdle()

      val jsonText = outputStream.toString(Charsets.UTF_8.name())
      val root = org.json.JSONObject(jsonText)
      val settings = root.getJSONObject("settings")
      assertEquals("export must embed the actual persisted dark mode", false, settings.getBoolean("darkMode"))
    }

  @Test
  fun exportSummaryReflectsBankLoanCount() {
    val root =
      org.json.JSONObject().apply {
        put("transactions", org.json.JSONArray())
        put("loans", org.json.JSONArray())
        put("installments", org.json.JSONArray())
        put("categories", org.json.JSONArray())
        put("accounts", org.json.JSONArray())
        put("bankLoans", org.json.JSONArray().put(org.json.JSONObject().put("id", 1)))
      }

    val summary = viewModel.exportCoordinator.buildExportSummary(root)
    assertTrue(
      "export summary must reflect bank loan count (1 وام بانکی); actual: $summary",
      summary.contains("1 وام بانکی")
    )
    assertFalse(
      "export summary must not show zero bank loans; actual: $summary",
      summary.contains("0 وام بانکی")
    )
  }

  @Test
  fun exportSummaryShowsZeroBankLoansWhenAbsent() {
    val root =
      org.json.JSONObject().apply {
        put("transactions", org.json.JSONArray())
        put("loans", org.json.JSONArray())
        put("installments", org.json.JSONArray())
        put("categories", org.json.JSONArray())
        put("accounts", org.json.JSONArray())
      }

    val summary = viewModel.exportCoordinator.buildExportSummary(root)
    assertTrue(
      "export summary must show zero bank loans when absent; actual: $summary",
      summary.contains("0 وام بانکی")
    )
  }
}
