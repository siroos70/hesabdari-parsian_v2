package io.github.mojri.hesabyar

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountQuickFillTest {
  private val maxAmountToman = 999_999_999_999L

  private fun quickFill(
    currentAmount: Long,
    factor: Long
  ): Long = (currentAmount * factor).coerceAtMost(maxAmountToman)

  @Test
  fun `thousand factor appends 3 zeros`() {
    assertEquals(250_000L, quickFill(250L, 1_000L))
  }

  @Test
  fun `million factor appends 6 zeros`() {
    assertEquals(6_000_000L, quickFill(6L, 1_000_000L))
  }

  @Test
  fun `billion factor appends 9 zeros`() {
    assertEquals(1_000_000_000L, quickFill(1L, 1_000_000_000L))
  }

  @Test
  fun `empty input results in zero times factor`() {
    assertEquals(0L, quickFill(0L, 1_000_000L))
  }

  @Test
  fun `large amount with million stays within max`() {
    assertEquals(maxAmountToman, quickFill(999_999_999L, 1_000_000L))
  }

  @Test
  fun `large amount with billion is clamped`() {
    assertEquals(maxAmountToman, quickFill(1_000L, 1_000_000_000L))
  }

  @Test
  fun `1 million is correct`() {
    assertEquals(1_000_000L, quickFill(1L, 1_000_000L))
  }

  @Test
  fun `1000 with thousand is one million`() {
    assertEquals(1_000_000L, quickFill(1_000L, 1_000L))
  }

  @Test
  fun `parsing empty string gives zero`() {
    val amount = "".toLongOrNull() ?: 0L
    assertEquals(0L, amount)
  }

  @Test
  fun `parsing valid string gives number`() {
    val amount = "250".toLongOrNull() ?: 0L
    assertEquals(250L, amount)
  }
}
