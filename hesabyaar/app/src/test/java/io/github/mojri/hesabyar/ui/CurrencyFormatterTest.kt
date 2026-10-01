package io.github.mojri.hesabyar.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyFormatterTest {
  /** Strip LRM prefix so tests focus on formatting logic, not BIDI control chars. */
  private fun String.stripLrm(): String = removePrefix("\u200E")

  @After
  fun reset() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  // --- toRial ---

  @Test
  fun torialTomanMultipliesBy10() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(100_000L, CurrencyFormatter.toRial(10_000L))
  }

  @Test
  fun torialRialPassesThrough() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    assertEquals(10_000L, CurrencyFormatter.toRial(10_000L))
  }

  @Test
  fun torialZero() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(0L, CurrencyFormatter.toRial(0L))
  }

  // --- fromRial ---

  @Test
  fun fromrialTomanDividesBy10() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(10_000L, CurrencyFormatter.fromRial(100_000L))
  }

  @Test
  fun fromrialRialPassesThrough() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    assertEquals(100_000L, CurrencyFormatter.fromRial(100_000L))
  }

  @Test
  fun fromrialTruncatesOnOddRial() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(5L, CurrencyFormatter.fromRial(55L))
  }

  // --- setUnit state ---

  @Test
  fun setunitUpdatesCurrentunit() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    assertEquals(CurrencyUnit.RIAL, CurrencyFormatter.currentUnit)
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(CurrencyUnit.TOMAN, CurrencyFormatter.currentUnit)
  }

  // --- round-trip consistency ---

  @Test
  fun torialThenFromrialRoundtripsForToman() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val original = 5_500_000L
    val rial = CurrencyFormatter.toRial(original)
    val back = CurrencyFormatter.fromRial(rial)
    assertEquals(original, back)
  }

  @Test
  fun torialThenFromrialRoundtripsForRial() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    val original = 5_500_000L
    val rial = CurrencyFormatter.toRial(original)
    val back = CurrencyFormatter.fromRial(rial)
    assertEquals(original, back)
  }

  // --- unitLabel ---

  @Test
  fun unitlabelMatchesEnumLabel() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    assertEquals(CurrencyUnit.RIAL.label, CurrencyFormatter.unitLabel)
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals(CurrencyUnit.TOMAN.label, CurrencyFormatter.unitLabel)
  }

  // --- format includes unit ---

  @Test
  fun formatIncludesRialUnit() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    val result = CurrencyFormatter.format(1_000_000L).stripLrm()
    assertTrue(result.endsWith("ریال"))
  }

  @Test
  fun formatIncludesTomanUnit() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.format(10_000_000L).stripLrm()
    assertTrue(result.endsWith("تومان"))
  }

  // --- fromKey ---

  @Test
  fun fromkeyReturnsCorrectUnit() {
    assertEquals(CurrencyUnit.RIAL, CurrencyUnit.fromKey("rial"))
    assertEquals(CurrencyUnit.TOMAN, CurrencyUnit.fromKey("toman"))
  }

  @Test
  fun fromkeyFallsBackToToman() {
    assertEquals(CurrencyUnit.TOMAN, CurrencyUnit.fromKey("unknown"))
    assertEquals(CurrencyUnit.TOMAN, CurrencyUnit.fromKey(""))
  }

  // --- format / formatNumber fallback + sign handling ---

  @Test
  fun formatDividesRialBy10InTomanMode() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.format(10_000_000L).stripLrm()
    assertTrue(result.contains("۱٬۰۰۰٬۰۰۰"))
    assertTrue(result.endsWith("تومان"))
  }

  @Test
  fun formatShowsRawValueInRialMode() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    val result = CurrencyFormatter.format(10_000_000L).stripLrm()
    assertTrue(result.contains("۱۰٬۰۰۰٬۰۰۰"))
    assertTrue(result.endsWith("ریال"))
  }

  @Test
  fun formatNegativeValueKeepsSignBeforeUnit() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.format(-10_000_000L).stripLrm()
    assertTrue(result.startsWith("-"))
    assertTrue(result.endsWith("تومان"))
  }

  // --- Kotlin fallback branch (Rust unavailable in unit tests) ---

  @Test
  fun formatnumberFallsBackToPersianDigitsForPositive() {
    val result = CurrencyFormatter.formatNumber(1_234_567L).stripLrm()
    assertEquals("۱٬۲۳۴٬۵۶۷", result)
  }

  @Test
  fun formatnumberFallsBackToPersianDigitsWithSignForNegative() {
    val result = CurrencyFormatter.formatNumber(-1_234_567L).stripLrm()
    assertEquals("-۱٬۲۳۴٬۵۶۷", result)
  }

  @Test
  fun formatFallsBackToPersianDigitsWithTomanLabel() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.format(10_000_000L).stripLrm()
    assertEquals("۱٬۰۰۰٬۰۰۰ تومان", result)
  }

  @Test
  fun formatFallsBackToPersianDigitsWithSignAndRialLabel() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    val result = CurrencyFormatter.format(-10_000_000L).stripLrm()
    assertEquals("-۱۰٬۰۰۰٬۰۰۰ ریال", result)
  }

  @Test
  fun formatZeroFallsBackWithoutSign() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.format(0L).stripLrm()
    assertEquals("۰ تومان", result)
  }

  // --- LRM (U+200E) BIDI correctness ---

  @Test
  fun formatNegativeValueStartsWithLrmBeforeMinus() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.format(-10_000_000L)
    // LRM (U+200E) must be the first character so the number block renders LTR in RTL layout
    assertTrue("must start with LRM", result.startsWith('\u200E'))
    assertTrue("LRM+minus must be followed by digits", result[1] == '-' || result[1] == '\u200E')
    // After LRM and minus, the next char must be a Persian digit (0x06F0–0x06F9)
    val digitIndex = result.indexOfFirst { it.code in 0x06F0..0x06F9 }
    assertFalse("must contain Persian digits", digitIndex == -1)
    assertTrue("Persian digits must come after LRM+minus", digitIndex >= 2)
  }

  @Test
  fun formatPositiveValueStartsWithLrm() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.format(10_000_000L)
    assertTrue("positive must start with LRM", result.startsWith('\u200E'))
  }

  @Test
  fun formatnumberNegativeValueStartsWithLrmBeforeMinus() {
    val result = CurrencyFormatter.formatNumber(-500_000L)
    assertTrue("must start with LRM", result.startsWith('\u200E'))
    assertTrue("second char must be minus", result[1] == '-')
  }

  // --- formatSigned ---

  @Test
  fun formatsignedPositivePrependsPlus() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.formatSigned(1_000_000L)
    assertTrue("must start with +", result.startsWith("+"))
  }

  @Test
  fun formatsignedNegativePrependsMinus() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.formatSigned(-1_000_000L)
    assertTrue("must start with -", result.startsWith("-"))
  }

  @Test
  fun formatsignedZeroPrependsPlus() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.formatSigned(0L)
    assertTrue("zero must start with +", result.startsWith("+"))
  }

  // --- formatSignedParts ---

  @Test
  fun formatsignedpartsPositiveReturnsPlusSign() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val (sign, amount) = CurrencyFormatter.formatSignedParts(1_000_000L)
    assertEquals("+", sign)
    assertTrue("amount must contain toman", amount.stripLrm().endsWith("تومان"))
  }

  @Test
  fun formatsignedpartsNegativeReturnsMinusSign() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val (sign, amount) = CurrencyFormatter.formatSignedParts(-1_000_000L)
    assertEquals("-", sign)
    assertTrue("amount must contain toman", amount.stripLrm().endsWith("تومان"))
  }

  @Test
  fun formatsignedpartsZeroReturnsPlusSign() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val (sign, _) = CurrencyFormatter.formatSignedParts(0L)
    assertEquals("+", sign)
  }

  // --- Long.MIN_VALUE overflow guard (T1-3) ---

  @Test
  fun formatsignedpartsMinValueDoesNotOverflow() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val (sign, amount) = CurrencyFormatter.formatSignedParts(Long.MIN_VALUE)
    // Must produce "-" sign (negative), not overflow to positive
    assertEquals("Long.MIN_VALUE must be treated as negative", "-", sign)
    // Amount must be a finite formatted string, not a sign-flipped artifact
    val stripped = amount.stripLrm()
    assertTrue("amount must be non-empty", stripped.isNotEmpty())
    assertTrue("amount must not start with -", !stripped.startsWith("-"))
    assertTrue("amount must not start with +", !stripped.startsWith("+"))
  }

  @Test
  fun formatsignedMinValueDoesNotOverflow() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.formatSigned(Long.MIN_VALUE)
    assertTrue("must start with minus", result.startsWith("-"))
    val stripped = result.stripLrm()
    // After the minus sign, there must be a valid formatted number
    assertTrue("after minus must have digits", stripped.drop(1).isNotEmpty())
  }

  @Test
  fun formatMinValueClampsMagnitudeToMaxValue() {
    // Long.MIN_VALUE is negative — the sign must be preserved (same as any
    // other negative value); only the magnitude is clamped to Long.MAX_VALUE
    // so that kotlin.math.abs() cannot overflow and flip the sign.
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    val result = CurrencyFormatter.format(Long.MIN_VALUE).stripLrm()
    assertTrue("must start with minus", result.startsWith("-"))
    assertTrue("must contain toman", result.endsWith("تومان"))
    // Magnitude must be identical to format(Long.MAX_VALUE) — the clamped
    // value, not an overflow artifact (abs(Long.MIN_VALUE) == Long.MIN_VALUE).
    val magnitude = result.drop(1)
    val expectedMagnitude = CurrencyFormatter.format(Long.MAX_VALUE).stripLrm()
    assertEquals("magnitude must equal clamped format(Long.MAX_VALUE)", expectedMagnitude, magnitude)
  }
}
