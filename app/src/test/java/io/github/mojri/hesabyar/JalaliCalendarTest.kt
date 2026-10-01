package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import io.github.mojri.hesabyar.ui.JalaliNativeBridge
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class JalaliCalendarTest {
  @Before
  fun setUp() {
    // Ensure bridgeProvider is null before each test so tests are isolated
    // from other test suites that may set a mock bridge.
    JalaliCalendarHelper.bridgeProvider = null
  }

  @After
  fun tearDown() {
    JalaliCalendarHelper.bridgeProvider = null
  }

  @Test
  fun `known date conversion - 1403-01-01`() {
    val jd = JalaliCalendarHelper.gregorianToJalali(2024, 3, 20)
    assertEquals(1403, jd.year)
    assertEquals(1, jd.month)
    assertEquals(1, jd.day)
  }

  @Test
  fun `known date conversion - 1403-07-01`() {
    val jd = JalaliCalendarHelper.gregorianToJalali(2024, 9, 22)
    assertEquals(1403, jd.year)
    assertEquals(7, jd.month)
    assertEquals(1, jd.day)
  }

  @Test
  fun `roundtrip gregorian to jalali and back`() {
    val gYear = 2024
    val gMonth = 6
    val gDay = 15
    val jd = JalaliCalendarHelper.gregorianToJalali(gYear, gMonth, gDay)
    val gc = JalaliCalendarHelper.jalaliToGregorian(jd.year, jd.month, jd.day)
    assertNotNull("jalaliToGregorian returned null for Jalali ${jd.year}/${jd.month}/${jd.day}", gc)
    assertEquals(gYear, gc!!.get(Calendar.YEAR))
    assertEquals(gMonth - 1, gc.get(Calendar.MONTH))
    assertEquals(gDay, gc.get(Calendar.DAY_OF_MONTH))
  }

  @Test
  fun `roundtrip for multiple dates`() {
    val dates =
      listOf(
        Triple(2024, 1, 1),
        Triple(2024, 2, 29),
        Triple(2024, 6, 15),
        Triple(2024, 12, 25),
        Triple(2025, 1, 1),
        Triple(2025, 3, 20),
        Triple(2025, 8, 10),
        Triple(2023, 7, 1),
        Triple(2020, 3, 21),
      )
    for ((gYear, gMonth, gDay) in dates) {
      val jd = JalaliCalendarHelper.gregorianToJalali(gYear, gMonth, gDay)
      val gc = JalaliCalendarHelper.jalaliToGregorian(jd.year, jd.month, jd.day)
      assertNotNull(
        "jalaliToGregorian returned null for $gYear/$gMonth/$gDay (Jalali: ${jd.year}/${jd.month}/${jd.day})",
        gc
      )
      assertEquals("Year mismatch for $gYear/$gMonth/$gDay", gYear, gc!!.get(Calendar.YEAR))
      assertEquals("Month mismatch for $gYear/$gMonth/$gDay", gMonth - 1, gc.get(Calendar.MONTH))
      assertEquals("Day mismatch for $gYear/$gMonth/$gDay", gDay, gc.get(Calendar.DAY_OF_MONTH))
    }
  }

  @Test
  fun `isJalaliLeapYear - known leap years`() {
    assertTrue(JalaliCalendarHelper.isJalaliLeapYear(1403))
    assertTrue(JalaliCalendarHelper.isJalaliLeapYear(1408))
    assertTrue(JalaliCalendarHelper.isJalaliLeapYear(1412))
    assertTrue(JalaliCalendarHelper.isJalaliLeapYear(1399))
  }

  @Test
  fun `isJalaliLeapYear - known non-leap years`() {
    assertFalse(JalaliCalendarHelper.isJalaliLeapYear(1400))
    assertFalse(JalaliCalendarHelper.isJalaliLeapYear(1401))
    assertFalse(JalaliCalendarHelper.isJalaliLeapYear(1402))
    assertFalse(JalaliCalendarHelper.isJalaliLeapYear(1404))
  }

  @Test
  fun `isJalaliLeapYear pattern repeats every 33 years`() {
    val leapYears = (1..33).filter { JalaliCalendarHelper.isJalaliLeapYear(1400 + it) }
    assertEquals(8, leapYears.size)
  }

  @Test
  fun `getDaysInMonth - first 6 months have 31 days`() {
    for (month in 1..6) {
      assertEquals(31, JalaliCalendarHelper.getDaysInMonth(1403, month))
    }
  }

  @Test
  fun `getDaysInMonth - months 7-11 have 30 days`() {
    for (month in 7..11) {
      assertEquals(30, JalaliCalendarHelper.getDaysInMonth(1403, month))
    }
  }

  @Test
  fun `getDaysInMonth - month 12 non-leap year has 29 days`() {
    assertEquals(29, JalaliCalendarHelper.getDaysInMonth(1400, 12))
  }

  @Test
  fun `getDaysInMonth - month 12 leap year has 30 days`() {
    assertEquals(30, JalaliCalendarHelper.getDaysInMonth(1403, 12))
  }

  @Test
  fun `jalaliToGregorian - known conversion`() {
    val gc = JalaliCalendarHelper.jalaliToGregorian(1403, 1, 1)
    assertNotNull("jalaliToGregorian(1403, 1, 1) should not return null", gc)
    assertEquals(2024, gc!!.get(Calendar.YEAR))
    assertEquals(Calendar.MARCH, gc.get(Calendar.MONTH))
    assertEquals(20, gc.get(Calendar.DAY_OF_MONTH))
  }

  @Test
  fun `gregorianToJalali from timestamp`() {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.set(2024, Calendar.MARCH, 20, 0, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val jd = JalaliCalendarHelper.gregorianToJalali(cal.timeInMillis)
    assertEquals(1403, jd.year)
    assertEquals(1, jd.month)
    assertEquals(1, jd.day)
  }

  @Test
  fun `JalaliDate toString format`() {
    val date = JalaliCalendarHelper.JalaliDate(1403, 1, 5)
    assertEquals("1403/01/05", date.toString())
  }

  @Test
  fun `JalaliDate toString zero-pads month and day`() {
    val date = JalaliCalendarHelper.JalaliDate(1403, 3, 9)
    assertEquals("1403/03/09", date.toString())
  }

  @Test
  fun `gregorianToJalaliLocal matches known conversions (Rust fallback)`() {
    // Pure-Kotlin fallback must agree with the Rust core when it is offline.
    val cases =
      listOf(
        Triple(2024, 3, 20) to Triple(1403, 1, 1),
        Triple(2024, 9, 22) to Triple(1403, 7, 1),
        Triple(2025, 3, 20) to Triple(1403, 12, 30),
        Triple(2024, 2, 29) to Triple(1402, 12, 10),
        Triple(2020, 3, 21) to Triple(1399, 1, 2),
        Triple(2023, 7, 1) to Triple(1402, 4, 10),
      )
    for ((g, expected) in cases) {
      val jd = JalaliCalendarHelper.gregorianToJalaliLocal(g.first, g.second, g.third)
      assertNotNull("Local fallback returned null for ${g.first}/${g.second}/${g.third}", jd)
      assertEquals("Year mismatch for ${g.first}/${g.second}/${g.third}", expected.first, jd!!.year)
      assertEquals("Month mismatch for ${g.first}/${g.second}/${g.third}", expected.second, jd.month)
      assertEquals("Day mismatch for ${g.first}/${g.second}/${g.third}", expected.third, jd.day)
    }
  }

  @Test
  fun `gregorianToJalali rejects invalid date when Rust available`() {
    // Calendar.set() normalizes invalid dates, so the Rust path must still
    // reject them (not silently accept the normalized date).
    val invalid =
      listOf(
        Triple(2024, 2, 30),
        Triple(2023, 2, 29),
        Triple(2024, 0, 15),
        Triple(2024, 13, 15),
        Triple(2024, 4, 31),
      )
    for ((gYear, gMonth, gDay) in invalid) {
      assertThrows(
        "Expected IllegalStateException for invalid date $gYear/$gMonth/$gDay",
        IllegalStateException::class.java,
      ) {
        JalaliCalendarHelper.gregorianToJalali(gYear, gMonth, gDay)
      }
    }
  }

  @Test
  fun `gregorianToJalaliLocal returns null for invalid date`() {
    assertNull(JalaliCalendarHelper.gregorianToJalaliLocal(2024, 0, 15))
    assertNull(JalaliCalendarHelper.gregorianToJalaliLocal(2024, 13, 15))
    assertNull(JalaliCalendarHelper.gregorianToJalaliLocal(2023, 2, 29))
    assertNull(JalaliCalendarHelper.gregorianToJalaliLocal(2024, 2, 30))
    assertNull(JalaliCalendarHelper.gregorianToJalaliLocal(2024, 4, 31))
  }

  @Test
  fun `getDaysInMonth falls back to local when native returns -1`() {
    val originalProvider = JalaliCalendarHelper.bridgeProvider
    try {
      JalaliCalendarHelper.bridgeProvider = {
        object : JalaliNativeBridge {
          override fun gregorianToJalaliSync(timestampMs: Long): Long = throw AssertionError("Should not be called")

          override fun jalaliToGregorianSync(
            year: Int,
            month: Int,
            day: Int
          ): Long = throw AssertionError("Should not be called")

          override fun getJalaliDaysInMonthSync(
            year: Int,
            month: Int
          ): Int = -1

          override fun isJalaliLeapYearSync(year: Int): Boolean = year % 33 in intArrayOf(1, 5, 9, 13, 17, 22, 26, 30)
        }
      }
      // Month 12 in non-leap year should have 29 days
      assertEquals(29, JalaliCalendarHelper.getDaysInMonth(1400, 12))
      // Month 1 always has 31 days
      assertEquals(31, JalaliCalendarHelper.getDaysInMonth(1403, 1))
      // Month 7 always has 30 days
      assertEquals(30, JalaliCalendarHelper.getDaysInMonth(1403, 7))
    } finally {
      JalaliCalendarHelper.bridgeProvider = originalProvider
    }
  }

  @Test
  fun `getDaysInMonth falls back to local when bridge returns null`() {
    val originalProvider = JalaliCalendarHelper.bridgeProvider
    try {
      JalaliCalendarHelper.bridgeProvider = { null }
      // Without native bridge, should use local calculation
      assertEquals(29, JalaliCalendarHelper.getDaysInMonth(1400, 12))
      assertEquals(30, JalaliCalendarHelper.getDaysInMonth(1403, 12))
      assertEquals(31, JalaliCalendarHelper.getDaysInMonth(1403, 1))
    } finally {
      JalaliCalendarHelper.bridgeProvider = originalProvider
    }
  }
}
