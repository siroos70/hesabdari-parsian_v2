package io.github.mojri.hesabyar.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class FakeRepositoryTest {
  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /**
   * The exportGate is a one-shot suspension: it should block only the FIRST
   * collection of allCategories, then be consumed (cleared) so subsequent
   * collections are not suspended on the same — possibly incomplete — deferred.
   */
  @Test
  fun exportGateBlocksOnlyFirstCollection() =
    runTest {
      val repo = FakeRepository()
      val gate = CompletableDeferred<Unit>()
      repo.exportGate = gate

      // First collection: should consume the gate and suspend on it.
      var firstEmitted = false
      val firstJob =
        launch {
          repo.allCategories.collect { firstEmitted = true }
        }
      advanceUntilIdle()
      assertEquals("first collection should read the flow once", 1, repo.exportCategoryReadCount)
      assertFalse("first collection must block while gate is incomplete", firstEmitted)

      // Gate was consumed (cleared) during the first collection's await.
      // A second collection on the same incomplete deferred must not block.
      var secondEmitted = false
      val secondJob =
        launch {
          repo.allCategories.collect { secondEmitted = true }
        }
      advanceUntilIdle()
      assertTrue("second collection must not block on the consumed gate", secondEmitted)

      // Clean up: complete the gate so the suspended first collection can finish.
      gate.complete(Unit)
      advanceUntilIdle()
      firstJob.join()
      secondJob.cancel()
      secondJob.join()
    }
}
