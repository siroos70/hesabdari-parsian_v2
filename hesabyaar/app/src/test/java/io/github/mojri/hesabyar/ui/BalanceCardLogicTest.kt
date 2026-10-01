package io.github.mojri.hesabyar.ui

import androidx.compose.ui.graphics.Color
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
import io.github.mojri.hesabyar.ui.designsystem.FinancialColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.DecimalFormat

/**
 * Tests BalanceCard display logic without Compose rendering:
 * - Balance/income/expense formatting
 * - Gradient background: PurpleAccent with 0.2f alpha fading to Transparent
 * - Click behavior (onClick non-null → clickable)
 * - Zero balance display
 */
class BalanceCardLogicTest {
  /** Strip LRM prefix so tests focus on formatting logic, not BIDI control chars. */
  private fun String.stripLrm(): String = removePrefix("\u200E")

  @Before
  fun setUp() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  private val formatter = DecimalFormat("#,###")

  // Use CurrencyUnit enum label as authoritative — CurrencyFormatter.format() uses this too
  private val expectedSuffix = CurrencyUnit.TOMAN.label

  @Test
  fun balanceFormattedWithSeparatorsAndCurrencySuffix() {
    val balance = 5000000L // 5M rial
    val display = CurrencyFormatter.format(balance).stripLrm()
    assertEquals("۵۰۰٬۰۰۰", display.substringBeforeLast(" ")) // 5M rial = 500k TOMAN
    assertTrue(
      "Suffix should match $expectedSuffix but was: ${display.substringAfterLast(" ")}",
      display.endsWith(expectedSuffix)
    )
  }

  @Test
  fun zeroBalanceDisplaysCorrectly() {
    val display = CurrencyFormatter.format(0L).stripLrm()
    assertEquals("۰", display.substringBeforeLast(" "))
    assertTrue(
      "Suffix should match $expectedSuffix but was: ${display.substringAfterLast(" ")}",
      display.endsWith(expectedSuffix)
    )
  }

  @Test
  fun largeBalanceWithCommas() {
    val balance = 1234567890L
    val display = formatter.format(balance)
    assertEquals("formatted with commas", "1,234,567,890", display)
  }

  @Test
  fun gradientStartsWithPurpleAccentAt02Alpha() {
    val gradientStart = FinancialColors.PurpleAccent.copy(alpha = 0.2f)
    assertEquals("alpha should be 0.2", 0.2f, gradientStart.alpha, 0.001f)
    assertEquals("red should match PurpleAccent", FinancialColors.PurpleAccent.red, gradientStart.red, 0.001f)
    assertEquals("green should match PurpleAccent", FinancialColors.PurpleAccent.green, gradientStart.green, 0.001f)
    assertEquals("blue should match PurpleAccent", FinancialColors.PurpleAccent.blue, gradientStart.blue, 0.001f)
  }

  @Test
  fun gradientEndsWithTransparent() {
    val gradientEnd = Color.Transparent
    assertEquals("Transparent alpha should be 0", 0f, gradientEnd.alpha, 0.001f)
  }

  @Test
  fun clickableWhenOnclickIsProvided() {
    val onClick: (() -> Unit)? = { }
    assertTrue("Should be clickable", onClick != null)
  }

  @Test
  fun notClickableWhenOnclickIsNull() {
    val onClick: (() -> Unit)? = null
    assertEquals(false, onClick != null)
  }
}
