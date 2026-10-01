package io.github.mojri.hesabyar.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MathUtilsTest {
  @Test
  fun saturatingAddNormalValuesReturnsExactSum() {
    assertEquals("10 + 20 must equal 30", 30L, MathUtils.saturatingAdd(10L, 20L))
    assertEquals("-10 + -20 must equal -30", -30L, MathUtils.saturatingAdd(-10L, -20L))
    assertEquals("10 + -5 must equal 5", 5L, MathUtils.saturatingAdd(10L, -5L))
  }

  @Test
  fun saturatingAddPositiveOverflowClampsToLongMax() {
    assertEquals(
      "Long.MAX_VALUE + 1 must clamp to Long.MAX_VALUE",
      Long.MAX_VALUE,
      MathUtils.saturatingAdd(Long.MAX_VALUE, 1L)
    )
    assertEquals(
      "Long.MAX_VALUE + Long.MAX_VALUE must clamp to Long.MAX_VALUE",
      Long.MAX_VALUE,
      MathUtils.saturatingAdd(Long.MAX_VALUE, Long.MAX_VALUE)
    )
  }

  @Test
  fun saturatingAddNegativeOverflowClampsToLongMin() {
    assertEquals(
      "Long.MIN_VALUE + -1 must clamp to Long.MIN_VALUE",
      Long.MIN_VALUE,
      MathUtils.saturatingAdd(Long.MIN_VALUE, -1L)
    )
    assertEquals(
      "Long.MIN_VALUE + Long.MIN_VALUE must clamp to Long.MIN_VALUE",
      Long.MIN_VALUE,
      MathUtils.saturatingAdd(Long.MIN_VALUE, Long.MIN_VALUE)
    )
  }

  @Test
  fun saturatingSubNormalValuesReturnsExactDifference() {
    assertEquals("30 - 10 must equal 20", 20L, MathUtils.saturatingSub(30L, 10L))
    assertEquals("-30 - -10 must equal -20", -20L, MathUtils.saturatingSub(-30L, -10L))
    assertEquals("10 - -5 must equal 15", 15L, MathUtils.saturatingSub(10L, -5L))
  }

  @Test
  fun saturatingSubPositiveOverflowClampsToLongMax() {
    assertEquals(
      "Long.MAX_VALUE - -1 must clamp to Long.MAX_VALUE",
      Long.MAX_VALUE,
      MathUtils.saturatingSub(Long.MAX_VALUE, -1L)
    )
    assertEquals(
      "Long.MAX_VALUE - -100 must clamp to Long.MAX_VALUE",
      Long.MAX_VALUE,
      MathUtils.saturatingSub(Long.MAX_VALUE, -100L)
    )
  }

  @Test
  fun saturatingSubNegativeOverflowClampsToLongMin() {
    assertEquals(
      "Long.MIN_VALUE - 1 must clamp to Long.MIN_VALUE",
      Long.MIN_VALUE,
      MathUtils.saturatingSub(Long.MIN_VALUE, 1L)
    )
    assertEquals(
      "Long.MIN_VALUE - Long.MAX_VALUE must clamp to Long.MIN_VALUE",
      Long.MIN_VALUE,
      MathUtils.saturatingSub(Long.MIN_VALUE, Long.MAX_VALUE)
    )
  }

  @Test
  fun saturatingSubWithLongMinValue() {
    assertEquals(Long.MAX_VALUE, MathUtils.saturatingSub(5L, Long.MIN_VALUE))
    assertEquals(Long.MAX_VALUE, MathUtils.saturatingSub(0L, Long.MIN_VALUE))
    assertEquals(Long.MAX_VALUE, MathUtils.saturatingSub(-1L, Long.MIN_VALUE))
    assertEquals(Long.MAX_VALUE - 4, MathUtils.saturatingSub(-5L, Long.MIN_VALUE))
    assertEquals(0L, MathUtils.saturatingSub(Long.MIN_VALUE, Long.MIN_VALUE))
  }
}
