package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Independent coverage of the Kotlin fallback parser ([GeminiParser.kotlinFallbackParse]),
 * which runs only when the Rust core is unavailable. These do NOT rely on Rust being
 * loaded, so they verify the offline-resilience behavior in isolation.
 *
 * All date-sensitive tests pin a fixed "today" (1405/04/10 = 10 Tir 1405) so
 * results are deterministic regardless of the wall-clock date.
 */
class GeminiParserFallbackTest {
  /** Fixed "today" — 1405/04/10 (10 Tir 1405) — used by all date-sensitive
   *  tests to make them deterministic on any calendar day. */
  private val fixedToday = JalaliCalendarHelper.JalaliDate(1405, 4, 10)

  private fun parse(
    sentence: String,
    today: JalaliCalendarHelper.JalaliDate = fixedToday
  ) = GeminiParser.kotlinFallbackParse(sentence, today)

  private val monthName =
    mapOf(
      1 to "فروردین",
      2 to "اردیبهشت",
      3 to "خرداد",
      4 to "تیر",
      5 to "مرداد",
      6 to "شهریور",
      7 to "مهر",
      8 to "آبان",
      9 to "آذر",
      10 to "دی",
      11 to "بهمن",
      12 to "اسفند"
    )

  // --- success: amount extraction ---

  @Test
  fun fallbackExtractsMillionAmount() {
    val result = parse("غذا خریدم 5 میلیون")
    assertEquals(50_000_000L, result!!.amount)
  }

  @Test
  fun fallbackExtractsThousandAmount() {
    val result = parse("بنزین خریدم 450 هزار تومان")
    assertEquals(4_500_000L, result!!.amount)
  }

  @Test
  fun fallbackExtractsPlainCommaAmount() {
    val result = parse("1,500,000 تومان خرج کردم")
    assertEquals(15_000_000L, result!!.amount)
  }

  @Test
  fun fallbackNormalizesPersianNumeralsToAmount() {
    val result = parse("بنزین زدم ۶۰۰ هزار تومان")
    assertEquals(6_000_000L, result!!.amount)
  }

  @Test
  fun fallbackOverflowingMillionAmountReturnsNullInsteadOfWrapping() {
    // 9,999,999,999,999,999 * 1_000_000 * 10 overflows Long; must return null
    // rather than a wrapped/negative amount.
    assertNull(parse("9999999999999999 میلیون خرج کردم"))
  }

  // --- success: type detection ---

  @Test
  fun fallbackDetectsIncomeViaKeyword() {
    val result = parse("حقوق گرفتم 20 میلیون")
    assertEquals("INCOME", result!!.type)
    assertEquals("Income", result.category)
    assertEquals(200_000_000L, result.amount)
  }

  @Test
  fun fallbackDetectsInstallmentViaQestKeyword() {
    val result = parse("قسط ماشین 3 میلیون")
    assertEquals("INSTALLMENT", result!!.type)
    assertEquals("Installments", result.category)
  }

  @Test
  fun fallbackDoesNotMistakeForooshgahForSaleIncome() {
    val result = parse("فروشگاه رفتم 200 هزار")
    assertEquals("EXPENSE", result!!.type)
    assertEquals("Shopping", result.category)
  }

  @Test
  fun fallbackDetectsLoanDebtorViaGharzDadam() {
    val result = parse("به علی 5 میلیون قرض دادم")
    assertEquals("LOAN_DEBTOR", result!!.type)
    assertEquals("Loans", result.category)
    assertEquals("علی", result.personName)
  }

  @Test
  fun fallbackDetectsLoanCreditorViaGharzGereftam() {
    val result = parse("از رضا 2 میلیون قرض گرفتم")
    assertEquals("LOAN_CREDITOR", result!!.type)
    assertEquals("Loans", result.category)
    assertEquals("رضا", result.personName)
  }

  @Test
  fun fallbackDefaultsToExpense() {
    val result = parse("غذا خریدم 100 هزار")
    assertEquals("EXPENSE", result!!.type)
  }

  // --- success: category detection ---

  @Test
  fun fallbackMapsFoodKeywordToFood() {
    val result = parse("غذا خریدم 100 هزار")
    assertEquals("Food", result!!.category)
  }

  @Test
  fun fallbackMapsBillKeywordToBills() {
    val result = parse("قبض برق دادم 200 هزار")
    assertEquals("Bills", result!!.category)
  }

  @Test
  fun fallbackDefaultsUnknownToOther() {
    val result = parse("شیء عجیب 50 هزار")
    assertEquals("Other", result!!.category)
  }

  // --- success: person name ---

  @Test
  fun fallbackExtractsPersonNameAfterTo() {
    val result = parse("به رضا 2 میلیون قرض دادم")
    assertEquals("رضا", result!!.personName)
  }

  @Test
  fun fallbackExtractsPersonNameAfterFrom() {
    val result = parse("از علی 5 میلیون قرض گرفتم")
    assertEquals("علی", result!!.personName)
  }

  @Test
  fun fallbackHasNoPersonNameWhenAbsent() {
    val result = parse("غذا خریدم 100 هزار")
    assertNull(result!!.personName)
  }

  // --- success: date offset ---

  @Test
  fun fallbackDetectsYesterdayOffset() {
    val result = parse("دیروز 500 هزار خرج کردم")
    assertEquals(-1, result!!.dateOffsetDays)
  }

  @Test
  fun explicitDateDoesNotMatchMonthNameInsideLongerWordLikeDirooz() {
    // "5 دیروز" must not be read as "5 دی" (Dey 5); the month name requires a
    // letter boundary after it. The sentence still contains the relative word
    // "دیروز", so the offset should resolve to yesterday (-1), not a Dey offset.
    val result = parse("خریدم 5 دیروز 500 هزار")
    assertEquals(-1, result!!.dateOffsetDays)
  }

  @Test
  fun fallbackDetectsTomorrowOffset() {
    val result = parse("فردا 1 میلیون واریز می‌کنم")
    assertEquals(1, result!!.dateOffsetDays)
  }

  @Test
  fun fallbackDefaultsOffsetToZero() {
    val result = parse("غذا خریدم 100 هزار")
    assertEquals(0, result!!.dateOffsetDays)
  }

  @Test
  fun fallbackParsesExplicitJalaliDateToDayOffset() {
    // Pinned today = 1405/04/10 (10 Tir). Future month (Mordad 5) →
    // positive offset; earlier month (Khordad 5) → next year → positive.
    val future = parse("خرج ۱۰۰ هزار مرداد ۵", fixedToday)
    assertTrue(future!!.dateOffsetDays!! > 0)

    val earlier = parse("خرج ۱۰۰ هزار خرداد ۵", fixedToday)
    assertTrue(earlier!!.dateOffsetDays!! > 0)
  }

  // --- explicit Jalali date: day-month format (e.g. "۲۵ تیر") ---

  @Test
  fun explicitJalaliDateDayMonthFormatParsesCorrectly() {
    // Pinned today = 1405/04/10. testDay = 11 → same month, 1 day ahead.
    val result = parse("خریدم ۱۰۰ هزار ۱۱ تیر", fixedToday)
    assertNotNull(result)
    assertNotNull(result!!.dateOffsetDays)
    assertEquals(1L, result.dateOffsetDays!!.toLong())
  }

  // --- explicit Jalali date: month-day format (e.g. "تیر ۲۵") ---

  @Test
  fun explicitJalaliDateMonthDayFormatParsesCorrectly() {
    // Pinned today = 1405/04/10. testDay = 11 → same month, 1 day ahead.
    val result = parse("خریدم ۱۰۰ هزار تیر ۱۱", fixedToday)
    assertNotNull(result)
    assertNotNull(result!!.dateOffsetDays)
    assertEquals(1L, result.dateOffsetDays!!.toLong())
  }

  // --- same-month zero offset ---

  @Test
  fun explicitJalaliDateSameDayYieldsZeroOffset() {
    // Pinned today = 1405/04/10. Parse "10 Tir" → offset 0.
    val result = parse("خریدم ۱۰۰ هزار ۱۰ تیر", fixedToday)
    assertNotNull(result)
    assertEquals(0, result!!.dateOffsetDays)
  }

  // --- edge-case day numbers ---

  @Test
  fun explicitJalaliDateDayOneParsesCorrectly() {
    // Pinned today = 1405/04/10. Day 1 < 10 → past → next year → non-null.
    val result = parse("خریدم ۱۰۰ هزار ۱ تیر", fixedToday)
    assertNotNull(result)
    assertNotNull(result!!.dateOffsetDays)
  }

  @Test
  fun explicitJalaliDateDay31ParsesCorrectlyFor31DayMonths() {
    // Farvardin (month 1) always has 31 days in Jalali. 31 Farvardin
    // is before 10 Tir → past → next year → non-null.
    val result = parse("خریدم ۱۰۰ هزار ۳۱ فروردین", fixedToday)
    assertNotNull(result)
    assertNotNull(result!!.dateOffsetDays)
  }

  // --- year-boundary: month < today.month means next year ---

  @Test
  fun explicitJalaliDateInEarlierMonthUsesNextYear() {
    // Pinned today = 1405/04/10. Earlier month = Khordad (3).
    // 5 Khordad → next year → positive offset.
    val result = parse("خریدم ۱۰۰ هزار ۵ خرداد", fixedToday)
    assertNotNull(result)
    assertNotNull(result!!.dateOffsetDays)
    assertTrue(
      "Offset should be positive (future) for next-year date, was ${result.dateOffsetDays}",
      result.dateOffsetDays!! > 0
    )
  }

  @Test
  fun explicitJalaliDateInSameOrLaterMonthUsesCurrentYear() {
    // Pinned today = 1405/04/10. testDay = 11 → same month, 1 day ahead.
    val result = parse("خریدم ۱۰۰ هزار ۱۱ تیر", fixedToday)
    assertNotNull(result)
    assertNotNull(result!!.dateOffsetDays)
    assertEquals(1L, result.dateOffsetDays!!.toLong())
  }

  // --- no Jalali date found ---

  @Test
  fun noJalaliDateReturnsNullOffsetViaRelativeWordFallback() {
    val result = parse("خریدم ۱۰۰ هزار غذا")
    assertEquals(0, result!!.dateOffsetDays)
  }

  @Test
  fun relativeWordStillWorksWhenNoExplicitJalaliDatePresent() {
    val result = parse("دیروز ۱۰۰ هزار خرج کردم")
    assertEquals(-1, result!!.dateOffsetDays)
  }

  // --- success: fixed fallback metadata ---

  @Test
  fun fallbackUsesRawSentenceAsDescription() {
    val sentence = "غذا خریدم 100 هزار"
    val result = parse(sentence)
    assertEquals(sentence, result!!.description)
  }

  @Test
  fun fallbackSetsFixedConfidenceAndNullRichFields() {
    val result = parse("غذا خریدم 100 هزار")
    assertEquals(0.5f, result!!.confidence, 0.001f)
    assertNull(result.daysFromNow)
    assertNull(result.title)
    assertNull(result.hour)
    assertNull(result.minute)
    assertNull(result.notes)
  }

  // --- failure paths ---

  @Test
  fun fallbackReturnsNullWhenNoAmountPresent() {
    assertNull(parse("متن بدون پول"))
  }

  @Test
  fun fallbackReturnsNullForEmptyString() {
    assertNull(parse(""))
  }

  @Test
  fun fallbackReturnsNullForBlankText() {
    assertNull(parse("   "))
  }

  @Test
  fun fallbackReturnsNullWhenOnlyZeroAmount() {
    assertNull(parse("0 تومان"))
  }

  // ============================================================
  // parseJsonResultFallback tests — Toman-to-Rial conversion
  // ============================================================

  private fun parseJson(json: String) = GeminiParser.parseJsonResultFallback(json)

  @Test
  fun jsonFallbackConvertsTomanToRial() {
    val json = """{"type":"EXPENSE","amount":5000000,"category":"Food","description":"مرغ"}"""
    val result = parseJson(json)
    assertNotNull(result)
    assertEquals(50_000_000L, result!!.amount) // 5M toman * 10 = 50M rial
  }

  @Test
  fun jsonFallbackLargeAmountConvertsCorrectly() {
    val json = """{"type":"INCOME","amount":20000000,"category":"Income","description":"حقوق"}"""
    val result = parseJson(json)
    assertNotNull(result)
    assertEquals(200_000_000L, result!!.amount) // 20M toman * 10 = 200M rial
  }

  @Test
  fun jsonFallbackSmallAmountConvertsCorrectly() {
    val json = """{"type":"EXPENSE","amount":50000,"category":"Food","description":"نان"}"""
    val result = parseJson(json)
    assertNotNull(result)
    assertEquals(500_000L, result!!.amount) // 50K toman * 10 = 500K rial
  }

  @Test
  fun jsonFallbackOneTomanConvertsToTenRial() {
    val json = """{"type":"EXPENSE","amount":1,"category":"Other","description":"test"}"""
    val result = parseJson(json)
    assertNotNull(result)
    assertEquals(10L, result!!.amount)
  }

  @Test
  fun jsonFallbackZeroAmountReturnsNull() {
    val json = """{"type":"EXPENSE","amount":0,"category":"Food","description":"test"}"""
    assertNull(parseJson(json))
  }

  @Test
  fun jsonFallbackNegativeAmountReturnsNull() {
    val json = """{"type":"EXPENSE","amount":-5000,"category":"Food","description":"test"}"""
    assertNull(parseJson(json))
  }

  @Test
  fun jsonFallbackOverflowingAmountReturnsNull() {
    // Long.MAX_VALUE / 10 = 922337203685477580; anything above overflows
    val json = """{"type":"EXPENSE","amount":922337203685477581,"category":"Food","description":"test"}"""
    assertNull(parseJson(json))
  }

  @Test
  fun jsonFallbackMissingAmountReturnsNull() {
    val json = """{"type":"EXPENSE","category":"Food","description":"test"}"""
    assertNull(parseJson(json))
  }

  @Test
  fun jsonFallbackMalformedJsonReturnsNull() {
    assertNull(parseJson("{invalid json"))
  }

  @Test
  fun jsonFallbackEmptyStringReturnsNull() {
    assertNull(parseJson(""))
  }

  @Test
  fun jsonFallbackPreservesAllFields() {
    val json =
      """{"type":"INSTALLMENT","amount":3000000,"category":"Installments","""" +
        """description":"قسط ماشین","personName":"علی","dateOffsetDays":5,"""" +
        """daysFromNow":30,"title":"قسط ماشین","hour":14,"minute":30,"confidence":0.9}"""
    val result = parseJson(json)
    assertNotNull(result)
    assertEquals(30_000_000L, result!!.amount)
    assertEquals("INSTALLMENT", result.type)
    assertEquals("علی", result.personName)
    assertEquals(5, result.dateOffsetDays)
    assertEquals(30, result.daysFromNow)
    assertEquals("قسط ماشین", result.title)
    assertEquals(14, result.hour)
    assertEquals(30, result.minute)
    assertEquals(0.9f, result.confidence, 0.01f)
  }
}
