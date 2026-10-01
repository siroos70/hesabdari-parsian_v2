package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.ui.utils.ShakeDetector
import org.junit.Assert.assertEquals
import org.junit.Test

class ShakeDetectorTest {
  private val strong = 40f // ~4g

  @Test
  fun `three strong jolts inside window trigger one shake`() {
    var n = 0
    val d = ShakeDetector { n++ }
    d.onSample(strong, 0f, 0f, 0)
    d.onSample(0f, strong, 0f, 200)
    d.onSample(0f, 0f, strong, 400)
    assertEquals(1, n)
  }

  @Test
  fun `gravity only never triggers`() {
    var n = 0
    val d = ShakeDetector { n++ }
    repeat(100) { d.onSample(0f, 0f, 9.81f, it * 20L) }
    assertEquals(0, n)
  }

  @Test
  fun `slow jolts outside window do not trigger`() {
    var n = 0
    val d = ShakeDetector { n++ }
    d.onSample(strong, 0f, 0f, 0)
    d.onSample(strong, 0f, 0f, 1_500)
    d.onSample(strong, 0f, 0f, 3_000)
    assertEquals(0, n)
  }

  @Test
  fun `cooldown suppresses immediate second shake`() {
    var n = 0
    val d = ShakeDetector { n++ }
    listOf(0L, 100L, 200L).forEach { d.onSample(strong, 0f, 0f, it) }
    listOf(300L, 400L, 500L).forEach { d.onSample(strong, 0f, 0f, it) }
    assertEquals(1, n)
    listOf(2_500L, 2_600L, 2_700L).forEach { d.onSample(strong, 0f, 0f, it) }
    assertEquals(2, n)
  }
}
