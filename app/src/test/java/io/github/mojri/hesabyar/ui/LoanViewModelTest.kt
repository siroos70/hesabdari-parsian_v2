package io.github.mojri.hesabyar.ui

import io.github.mojri.hesabyar.domain.usecase.ManageLoanUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoanViewModelTest {
  private lateinit var repository: FakeRepository
  private lateinit var viewModel: LoanViewModel
  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    repository = FakeRepository()
    viewModel = LoanViewModel(ManageLoanUseCase(repository))
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun makeRepaymentReportsFailureWhenRepositoryThrows() =
    runTest(testDispatcher) {
      repository.addPaymentBehavior = { throw IllegalStateException("db down") }
      var reported: Boolean? = null

      viewModel.makeRepayment(1L, 100L, "notes", null) { reported = it }
      advanceUntilIdle()

      assertFalse("repository failure must reach the UI callback as false", reported ?: true)
    }

  @Test
  fun makeRepaymentReportsRepositoryRejection() =
    runTest(testDispatcher) {
      var reported: Boolean? = null

      viewModel.makeRepayment(1L, 100L, "notes", null) { reported = it }
      advanceUntilIdle()

      assertFalse(reported ?: true)
    }
}
