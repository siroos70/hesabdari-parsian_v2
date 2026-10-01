package io.github.mojri.hesabyar.ui

import io.github.mojri.hesabyar.ui.utils.extractForecastPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the free helper functions extracted from DashboardScreen:
 * - CurrencyFormatter.format: converts rial to display unit
 * - extractForecastPreview: extracts preview text from markdown forecast
 */
class DashboardHelperTest {
  /** Strip LRM prefix so tests focus on formatting logic, not BIDI control chars. */
  private fun String.stripLrm(): String = removePrefix("\u200E")

  // --- CurrencyFormatter.format tests (default: تومان) ---

  @Test
  fun formatConvertsRialToToman() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals("۱۰٬۰۰۰ تومان", CurrencyFormatter.format(100_000L).stripLrm())
  }

  @Test
  fun formatLargeAmountToman() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals("۵۰۰٬۰۰۰ تومان", CurrencyFormatter.format(5_000_000L).stripLrm())
  }

  @Test
  fun formatZeroToman() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals("۰ تومان", CurrencyFormatter.format(0L).stripLrm())
  }

  @Test
  fun formatSmallAmountTomanRoundsDown() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals("۵۰ تومان", CurrencyFormatter.format(500L).stripLrm())
  }

  @Test
  fun formatVeryLargeAmountToman() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
    assertEquals("۱۲۳٬۴۵۶٬۷۸۹ تومان", CurrencyFormatter.format(1_234_567_890L).stripLrm())
  }

  // --- CurrencyFormatter.format tests (ریال) ---

  @Test
  fun formatKeepsRialValueUnchanged() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    assertEquals("۱۰۰٬۰۰۰ ریال", CurrencyFormatter.format(100_000L).stripLrm())
  }

  @Test
  fun formatLargeAmountRial() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
    assertEquals("۵٬۰۰۰٬۰۰۰ ریال", CurrencyFormatter.format(5_000_000L).stripLrm())
  }

  // --- extractForecastPreview tests ---

  @Test
  fun extractforecastpreviewStripsMarkdownHeaders() {
    val forecast = "# گزارش\nمبلغ خرج شده: 5 میلیون\nباقیمانده: 2 میلیون"
    val preview = extractForecastPreview(forecast)
    assertEquals("مبلغ خرج شده: 5 میلیون | باقیمانده: 2 میلیون", preview)
  }

  @Test
  fun extractforecastpreviewStripsBulletMarkers() {
    val forecast = "- خرج اول\n- خرج دوم\n- خرج سوم"
    val preview = extractForecastPreview(forecast)
    assertEquals("خرج اول | خرج دوم | خرج سوم", preview)
  }

  @Test
  fun extractforecastpreviewTakesOnlyFirst3Lines() {
    val forecast = "خط اول\nخط دوم\nخط سوم\nخط چهارم\nخط پنجم"
    val preview = extractForecastPreview(forecast)
    assertEquals("خط اول | خط دوم | خط سوم", preview)
  }

  @Test
  fun extractforecastpreviewReturnsDefaultForEmptyContent() {
    val forecast = "# title\n"
    val preview = extractForecastPreview(forecast)
    assertEquals("گزارش آماده است", preview)
  }

  @Test
  fun extractforecastpreviewReturnsDefaultForBlankInput() {
    val preview = extractForecastPreview("")
    assertEquals("گزارش آماده است", preview)
  }

  @Test
  fun extractforecastpreviewTruncatesLongLinesAt60Chars() {
    val longLine = "a".repeat(80)
    val forecast = longLine
    val preview = extractForecastPreview(forecast)
    assertTrue("Should be truncated with ...", preview.endsWith("..."))
    assertTrue("Truncated length <= 63", preview.length <= 63)
  }

  @Test
  fun extractforecastpreviewTruncatesTotalPreviewAt150Chars() {
    val line1 = "a".repeat(55)
    val line2 = "b".repeat(55)
    val line3 = "c".repeat(55)
    val forecast = "$line1\n$line2\n$line3"
    val preview = extractForecastPreview(forecast)
    assertTrue("Total preview <= 153 (150 + ...)", preview.length <= 153)
    assertTrue("Should end with ...", preview.endsWith("..."))
  }

  @Test
  fun extractforecastpreviewHandlesAsteriskBullets() {
    val forecast = "* آیتم اول\n* آیتم دوم"
    val preview = extractForecastPreview(forecast)
    assertEquals("آیتم اول | آیتم دوم", preview)
  }
}
