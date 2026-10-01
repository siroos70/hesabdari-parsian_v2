package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Locks down [JalaliCalendarHelper.getJalaliMonthBoundaries]: the exact
 * 00:00:00.000 of the 1st day and 23:59:59.999 of the last day of the Jalali
 * month containing a timestamp, including a regular 31-day month, a 30-day
 * Esfand (leap year), and a 29-day Esfand (non-leap year).
 *
 * The default timezone is pinned to Asia/Tehran so the produced timestamps are
 * deterministic; the helper computes boundaries in device-local time.
 */
class JalaliMonthBoundariesTest {
  private val tehran = TimeZone.getTimeZone("Asia/Tehran")
  private var originalDefaultTz: TimeZone? = null
  private var originalBridgeProvider = JalaliCalendarHelper.bridgeProvider

  @Before
  fun setUp() {
    originalDefaultTz = TimeZone.getDefault()
    TimeZone.setDefault(tehran)
    // Capture and later restore the bridge provider so tests that install a
    // native bridge are not perturbed; force the pure-Kotlin path for this test
    // so results are deterministic and independent of native-core loading.
    originalBridgeProvider = JalaliCalendarHelper.bridgeProvider
    JalaliCalendarHelper.bridgeProvider = null
  }

  @After
  fun tearDown() {
    TimeZone.setDefault(originalDefaultTz)
    JalaliCalendarHelper.bridgeProvider = originalBridgeProvider
  }

  private fun tehranMillis(
    year: Int,
    month: Int,
    day: Int,
    hour: Int = 0,
    min: Int = 0,
    sec: Int = 0,
    ms: Int = 0
  ): Long {
    val cal = Calendar.getInstance(tehran)
    cal.set(year, month - 1, day, hour, min, sec)
    cal.set(Calendar.MILLISECOND, ms)
    return cal.timeInMillis
  }

  @Test
  fun `regular Farvardin month boundaries (1403-01)`() {
    val t = tehranMillis(2024, 3, 20, 12) // midday of Farvardin 1403
    val (start, end) = JalaliCalendarHelper.getJalaliMonthBoundaries(t)

    // 00:00:00.000 of the 1st day, 23:59:59.999 of the last day (31-day month).
    assertEquals(tehranMillis(2024, 3, 20), start)
    assertEquals(tehranMillis(2024, 4, 19, 23, 59, 59, 999), end)
    assertTrue(start <= t && t <= end)

    assertEquals(JalaliCalendarHelper.JalaliDate(1403, 1, 1), JalaliCalendarHelper.gregorianToJalali(start))
    assertEquals(JalaliCalendarHelper.JalaliDate(1403, 1, 31), JalaliCalendarHelper.gregorianToJalali(end))

    // Span covers exactly 31 days.
    assertEquals(31L * 24 * 60 * 60 * 1000, end - start + 1)
    // One ms before the start belongs to the previous (Esfand 1402) month.
    assertEquals(JalaliCalendarHelper.JalaliDate(1402, 12, 29), JalaliCalendarHelper.gregorianToJalali(start - 1))
    // One ms after the end is the 1st of the next month.
    assertEquals(JalaliCalendarHelper.JalaliDate(1403, 2, 1), JalaliCalendarHelper.gregorianToJalali(end + 1))
  }

  @Test
  fun `Esfand leap-year boundary (1403-12, 30 days)`() {
    val t = tehranMillis(2025, 2, 19, 12) // midday of Esfand 1403 (leap year)
    val (start, end) = JalaliCalendarHelper.getJalaliMonthBoundaries(t)

    assertEquals(tehranMillis(2025, 2, 19), start)
    assertEquals(tehranMillis(2025, 3, 20, 23, 59, 59, 999), end)
    assertTrue(start <= t && t <= end)

    assertEquals(JalaliCalendarHelper.JalaliDate(1403, 12, 1), JalaliCalendarHelper.gregorianToJalali(start))
    assertEquals(JalaliCalendarHelper.JalaliDate(1403, 12, 30), JalaliCalendarHelper.gregorianToJalali(end))

    assertEquals(30L * 24 * 60 * 60 * 1000, end - start + 1)
    assertEquals(JalaliCalendarHelper.JalaliDate(1403, 11, 30), JalaliCalendarHelper.gregorianToJalali(start - 1))
    assertEquals(JalaliCalendarHelper.JalaliDate(1404, 1, 1), JalaliCalendarHelper.gregorianToJalali(end + 1))
  }

  @Test
  fun `Esfand non-leap-year boundary (1402-12, 29 days)`() {
    val t = tehranMillis(2024, 2, 20, 12) // midday of Esfand 1402 (non-leap year)
    val (start, end) = JalaliCalendarHelper.getJalaliMonthBoundaries(t)

    assertEquals(tehranMillis(2024, 2, 20), start)
    assertEquals(tehranMillis(2024, 3, 19, 23, 59, 59, 999), end)
    assertTrue(start <= t && t <= end)

    assertEquals(JalaliCalendarHelper.JalaliDate(1402, 12, 1), JalaliCalendarHelper.gregorianToJalali(start))
    assertEquals(JalaliCalendarHelper.JalaliDate(1402, 12, 29), JalaliCalendarHelper.gregorianToJalali(end))

    assertEquals(29L * 24 * 60 * 60 * 1000, end - start + 1)
    assertEquals(JalaliCalendarHelper.JalaliDate(1402, 11, 30), JalaliCalendarHelper.gregorianToJalali(start - 1))
    assertEquals(JalaliCalendarHelper.JalaliDate(1403, 1, 1), JalaliCalendarHelper.gregorianToJalali(end + 1))
  }

  @Test
  fun `testJalaliBoundaries_HandlesFiveYearLeapAnomaly_ForYear1407And1408`() {
    // Jalali leap years follow the 33-year Birashk cycle (year % 33 ∈
    // {1,5,9,13,17,22,26,30}), NOT a fixed `year % 4`. After the 1403 leap year
    // the next leap is 1408 — a 5-year gap (1404–1407 are 365-day years). The
    // Esfand month length must reflect this: 29 days in 1407, 30 days in 1408.
    // This locks the 5-year anomaly into the month-boundary math.
    assertEquals(false, JalaliCalendarHelper.isJalaliLeapYear(1407))
    assertEquals(true, JalaliCalendarHelper.isJalaliLeapYear(1408))
    assertEquals(29, JalaliCalendarHelper.getDaysInMonth(1407, 12))
    assertEquals(30, JalaliCalendarHelper.getDaysInMonth(1408, 12))

    // Scenario A: Esfand 1407 (29 days). A mid-month timestamp resolves to the
    // correct month, the boundary span is exactly 29 days, and the day after the
    // end is Farvardin 1408.
    val t1407 = JalaliCalendarHelper.jalaliToGregorian(1407, 12, 15)!!.timeInMillis
    val (start1407, end1407) = JalaliCalendarHelper.getJalaliMonthBoundaries(t1407)
    assertEquals(JalaliCalendarHelper.JalaliDate(1407, 12, 1), JalaliCalendarHelper.gregorianToJalali(start1407))
    assertEquals(JalaliCalendarHelper.JalaliDate(1407, 12, 29), JalaliCalendarHelper.gregorianToJalali(end1407))
    assertEquals(29L * 24 * 60 * 60 * 1000, end1407 - start1407 + 1)
    assertEquals(JalaliCalendarHelper.JalaliDate(1408, 1, 1), JalaliCalendarHelper.gregorianToJalali(end1407 + 1))

    // Scenario B: Esfand 1408 (30 days, the 5-year leap correction).
    val t1408 = JalaliCalendarHelper.jalaliToGregorian(1408, 12, 15)!!.timeInMillis
    val (start1408, end1408) = JalaliCalendarHelper.getJalaliMonthBoundaries(t1408)
    assertEquals(JalaliCalendarHelper.JalaliDate(1408, 12, 1), JalaliCalendarHelper.gregorianToJalali(start1408))
    assertEquals(JalaliCalendarHelper.JalaliDate(1408, 12, 30), JalaliCalendarHelper.gregorianToJalali(end1408))
    assertEquals(30L * 24 * 60 * 60 * 1000, end1408 - start1408 + 1)
    assertEquals(JalaliCalendarHelper.JalaliDate(1409, 1, 1), JalaliCalendarHelper.gregorianToJalali(end1408 + 1))
  }

  // --- Saturating arithmetic used by the month-boundary fallbacks ------------
  // Extreme timestamps must NOT wrap the 30-day fallback window; the range must
  // stay ordered and bounded at Long.MIN_VALUE / Long.MAX_VALUE.

  private val windowMs = 30L * 24 * 60 * 60 * 1000

  @Test
  fun `saturatingAdd clamps at Long MAX instead of wrapping`() {
    assertEquals(Long.MAX_VALUE, JalaliCalendarHelper.saturatingAdd(Long.MAX_VALUE, windowMs))
    assertEquals(Long.MAX_VALUE, JalaliCalendarHelper.saturatingAdd(Long.MAX_VALUE - windowMs / 2, windowMs))
  }

  @Test
  fun `saturatingAdd clamps at Long MIN when subtracting past it`() {
    assertEquals(Long.MIN_VALUE, JalaliCalendarHelper.saturatingAdd(Long.MIN_VALUE, -windowMs))
  }

  @Test
  fun `saturatingSubtract clamps at Long MIN instead of wrapping`() {
    assertEquals(Long.MIN_VALUE, JalaliCalendarHelper.saturatingSubtract(Long.MIN_VALUE, windowMs))
  }

  @Test
  fun `saturatingSubtract clamps at Long MAX for negative subtrahend`() {
    assertEquals(Long.MAX_VALUE, JalaliCalendarHelper.saturatingSubtract(Long.MAX_VALUE, -1))
  }

  @Test
  fun `saturating arithmetic matches plain arithmetic in safe range`() {
    assertEquals(1000L - windowMs, JalaliCalendarHelper.saturatingSubtract(1000L, windowMs))
    assertEquals(1000L + windowMs, JalaliCalendarHelper.saturatingAdd(1000L, windowMs))
    assertEquals(Long.MAX_VALUE - 1, JalaliCalendarHelper.saturatingSubtract(Long.MAX_VALUE, 1))
  }

  @Test
  fun `saturatingSubtract handles Long MIN_VALUE subtrahend without off-by-one`() {
    // `a - Long.MIN_VALUE == a + Long.MAX_VALUE + 1`. The +1 must be applied for
    // non-positive a (where it does not overflow); for positive a the result
    // already saturates to Long.MAX_VALUE.
    assertEquals(Long.MAX_VALUE, JalaliCalendarHelper.saturatingSubtract(5L, Long.MIN_VALUE))
    assertEquals(Long.MAX_VALUE, JalaliCalendarHelper.saturatingSubtract(0L, Long.MIN_VALUE))
    assertEquals(Long.MAX_VALUE, JalaliCalendarHelper.saturatingSubtract(-1L, Long.MIN_VALUE))
    assertEquals(Long.MAX_VALUE - 4, JalaliCalendarHelper.saturatingSubtract(-5L, Long.MIN_VALUE))
  }
}
