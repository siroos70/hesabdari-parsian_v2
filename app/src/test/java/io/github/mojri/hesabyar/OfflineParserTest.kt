package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.GeminiParser
import io.github.mojri.hesabyar.rust.RustBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Tests for parse() which now delegates to Rust core.
 * Tests for MoneyDetector, PersianAmountParser, and PersianTextPreprocessor were removed
 * since those Kotlin classes were deleted — the Rust implementations are tested separately
 * in the Rust test suite (hesabyar-core).
 */

@Category(RustTest::class)
class OfflineParserTest {
  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  /** Fixed "today" (1405/04/10 = 10 Tir 1405) used by date-sensitive tests.
   *  UTC-midnight of that Jalali date — the exact epoch-ms convention the
   *  Rust parser's date math uses — so results are deterministic regardless
   *  of which real-world day the test runs on. */
  private fun fixedNow(
    year: Int,
    month: Int,
    day: Int
  ): Long = RustBridge.jalaliToGregorianSync(year, month, day)

  private fun parse(
    sentence: String,
    nowMs: Long = System.currentTimeMillis()
  ) = GeminiParser.parseSentenceOffline(sentence, nowMs)
    ?: throw AssertionError("parseSentenceOffline returned null for: $sentence")

  @Test
  fun parseExpenseWithMillion() {
    val result = parse("امروز مرغ خریدم 5 میلیون")
    assertEquals("EXPENSE", result.type)
    assertEquals(50_000_000L, result.amount)
    assertEquals("Food", result.category)
  }

  @Test
  fun parseExpenseWithThousand() {
    val result = parse("بنزین خریدم 450 هزار تومان")
    assertEquals("EXPENSE", result.type)
    assertEquals(4_500_000L, result.amount)
    assertEquals("Transportation", result.category)
  }

  @Test
  fun parseIncomeWithMillion() {
    val result = parse("حقوق گرفتم 20 میلیون")
    assertEquals("INCOME", result.type)
    assertEquals(200_000_000L, result.amount)
    assertEquals("Income", result.category)
  }

  @Test
  fun parseLoanCreditor() {
    val result = parse("از علی 5 میلیون قرض گرفتم")
    assertEquals("LOAN_CREDITOR", result.type)
    assertEquals(50_000_000L, result.amount)
    assertEquals("علی", result.personName)
  }

  @Test
  fun parseLoanDebtor() {
    val result = parse("به رضا 2 میلیون قرض دادم")
    assertEquals("LOAN_DEBTOR", result.type)
    assertEquals(20_000_000L, result.amount)
  }

  @Test
  fun parseInstallment() {
    val result = parse("قسط ماشین 3 میلیون")
    assertEquals("INSTALLMENT", result.type)
    assertEquals(30_000_000L, result.amount)
    assertNotNull(result.title)
  }

  @Test
  fun parseDateOffsetYesterday() {
    val result = parse("دیروز 500 هزار خرج کردم")
    assertEquals(-1, result.dateOffsetDays)
  }

  @Test
  fun parseDateOffsetTomorrow() {
    val result = parse("فردا 1 میلیون واریز می‌کنم")
    assertEquals(1, result.dateOffsetDays)
  }

  @Test
  fun parseAmountWithoutMultiplier() {
    val result = parse("1200 تومان خرج کردم")
    assertEquals(12_000L, result.amount)
  }

  @Test
  fun parseShoppingExpense() {
    val result = parse("لباس خریدم 800 هزار تومان")
    assertEquals("EXPENSE", result.type)
    assertEquals("Shopping", result.category)
  }

  @Test
  fun parseBillPayment() {
    val result = parse("قبض برق دادم 200 هزار")
    assertEquals("EXPENSE", result.type)
    assertEquals("Bills", result.category)
  }

  @Test
  fun parseHaircutExpenseWithThousand() {
    val result = parse("اصلاح کردم 200 هزار تومن")
    assertEquals("EXPENSE", result.type)
    assertEquals(2_000_000L, result.amount)
    assertEquals("Other", result.category)
  }

  @Test
  fun parseAmountWithPersianNumerals() {
    val result = parse("بنزین زدم ۶۰۰ هزار تومان")
    assertEquals("EXPENSE", result.type)
    assertEquals(6_000_000L, result.amount)
  }

  @Test
  fun parseSalonVisit() {
    val result = parse("آرایشگاه رفتم ۱۵۰ هزار تومان")
    assertEquals("EXPENSE", result.type)
    assertEquals("Other", result.category)
    assertEquals(1_500_000L, result.amount)
  }

  @Test
  fun parseDescriptionExtractedFromSentence() {
    val result = parse("اصلاح کردم 200 هزار تومن")
    assertTrue(result.description.isNotBlank())
  }

  @Test
  fun installmentDescriptionIsFutureorientedNotPaid() {
    val result = parse("قسط ماشین 3 میلیون")
    assertEquals("INSTALLMENT", result.type)
    assertEquals("قسط آینده", result.description)
  }

  @Test
  fun installmentNotesIndicatePendingStatus() {
    val result = parse("قسط ماشین 3 میلیون")
    assertEquals("قسط در انتظار پرداخت", result.notes)
  }

  @Test
  fun installmentWithSpecificJalaliDateCalculatesCorrectDays() {
    // Pinned "today" = 1405/04/10 (10 Tir). Target = 25 Tir → exactly
    // 15 days ahead, no year-rollover. The fixed nowMs makes this
    // deterministic on every calendar day.
    val todayMs = fixedNow(1405, 4, 10)
    val result = parse("قسط ماشین 25 تیر 10 میلیون", todayMs)
    assertEquals("INSTALLMENT", result.type)
    assertNotNull(result.daysFromNow)
    assertEquals("daysFromNow for 25 Tir from 10 Tir", 15, result.daysFromNow ?: 0)
  }

  @Test
  fun installmentWithMordadMonthExtractsDays() {
    // Pinned "today" = 1405/05/10 (10 Mordad). Target = 15 Mordad →
    // exactly 5 days ahead, unambiguously in the future.
    val todayMs = fixedNow(1405, 5, 10)
    val result = parse("قسط خانه 15 مرداد 5 میلیون", todayMs)
    assertEquals("INSTALLMENT", result.type)
    assertNotNull(result.daysFromNow)
    assertEquals("daysFromNow for 15 Mordad from 10 Mordad", 5, result.daysFromNow ?: 0)
  }

  @Test
  fun installmentWithoutSpecificDateDefaultsTo30() {
    val result = parse("قسط جدید 2 میلیون")
    assertEquals("INSTALLMENT", result.type)
    assertEquals(30, result.daysFromNow)
  }

  @Test
  fun installmentWithPersianNumeralsInDate() {
    // Pinned "today" = 1405/07/05 (5 Mehr). Target = 20 Mehr →
    // exactly 15 days ahead, unambiguously in the future.
    val todayMs = fixedNow(1405, 7, 5)
    val result = parse("قسط ماشین ۲۰ مهر ۸ میلیون", todayMs)
    assertEquals("INSTALLMENT", result.type)
    assertNotNull(result.daysFromNow)
    assertEquals("daysFromNow for 20 Mehr from 5 Mehr", 15, result.daysFromNow ?: 0)
  }

  @Test
  fun installmentTitleIsExtractedCorrectly() {
    val result = parse("قسط ماشین 25 تیر 10 میلیون")
    assertEquals("قسط ماشین", result.title)
  }

  @Test
  fun installmentForMortgageLoanExtractsCorrectTitle() {
    val result = parse("قسط وام مسکن 10 مرداد 5 میلیون")
    assertEquals("قسط وام مسکن", result.title)
  }

  @Test
  fun installmentAmountIsCorrect() {
    val result = parse("قسط ماشین 25 تیر 10 میلیون")
    assertEquals(100_000_000L, result.amount)
  }

  // ============================================================
  // GeminiParser.parseSentenceOffline (now Rust-backed) tests
  // ============================================================

  @Test
  fun parseSodaPurchaseAsExpenseNotIncome() {
    val result = parse("نوشابه گرفتم 85 هزار تومن")
    assertEquals("EXPENSE", result.type)
    assertEquals(850_000L, result.amount)
  }

  @Test
  fun parseInternetPackageAsExpenseNotIncome() {
    val result = parse("دیروز بسته ایترنت گرفتم 109 هزار و 800 تومن")
    assertEquals("EXPENSE", result.type)
    assertEquals(1_098_000L, result.amount)
  }

  @Test
  fun incomeDescriptionIncludesSubject() {
    val result = parse("بابت فروش پرتقال ها 200 هزار تومن گرفتم")
    assertEquals("INCOME", result.type)
    assertTrue("Description should mention subject", result.description.contains("پرتقال"))
  }

  @Test
  fun expenseDescriptionIncludesSubject() {
    val result = parse("بسته اینترنت خریدم 100 هزار تومن")
    assertEquals("EXPENSE", result.type)
    assertTrue("Description should mention subject", result.description.contains("بسته اینترنت"))
  }

  @Test
  fun expenseDescriptionForFoodIncludesItem() {
    val result = parse("مرغ خریدم 80 هزار تومن")
    assertEquals("EXPENSE", result.type)
    assertTrue("Description should mention food item", result.description.contains("مرغ"))
  }

  @Test
  fun sodaPurchaseDescriptionIncludesSoda() {
    val result = parse("نوشابه خریدم 85 هزار تومن")
    assertEquals("EXPENSE", result.type)
    assertTrue("Description should contain soda", result.description.contains("نوشابه"))
  }

  @Test
  fun sodaWithTimeWordExcludesTimeFromSubject() {
    val result = parse("دیشب نوشابه گرفتم")
    assertEquals("EXPENSE", result.type)
    assertTrue("Description should not contain دیشب", !result.description.contains("دیشب"))
    assertTrue("Description should contain soda", result.description.contains("نوشابه"))
  }

  // ============================================================
  // Category inference tests
  // ============================================================

  @Test
  fun categoryInferenceFoodKeywords() {
    val foodSentences =
      listOf(
        "مرغ خریدم 80 هزار تومن",
        "گوشت گرفتم 150 هزار",
        "غذا خریدم 100 هزار",
        "میوه خریدم 50 هزار",
        "رستوران رفتم 200 هزار",
        "نان خریدم 10 هزار",
        "شیر خریدم 15 هزار",
        "چای خریدم 20 هزار",
        "قهوه خریدم 30 هزار",
        "کباب خریدم 120 هزار",
        "پیتزا خریدم 90 هزار"
      )
    foodSentences.forEach { sentence ->
      val result = parse(sentence)
      assertEquals("Expected Food for: $sentence", "Food", result.category)
    }
  }

  @Test
  fun categoryInferenceTransportationKeywords() {
    val transportSentences =
      listOf(
        "بنزین زدم 200 هزار",
        "اسنپ گرفتم 50 هزار",
        "کرایه تاکسی 30 هزار",
        "مترو رفتم 10 هزار"
      )
    transportSentences.forEach { sentence ->
      val result = parse(sentence)
      assertEquals("Expected Transportation for: $sentence", "Transportation", result.category)
    }
  }

  @Test
  fun categoryInferenceShoppingKeywords() {
    val shoppingSentences =
      listOf(
        "لباس خریدم 500 هزار",
        "کفش خریدم 300 هزار",
        "کیف خریدم 200 هزار"
      )
    shoppingSentences.forEach { sentence ->
      val result = parse(sentence)
      assertEquals("Expected Shopping for: $sentence", "Shopping", result.category)
    }
  }

  @Test
  fun categoryInferenceBillsKeywords() {
    val billSentences =
      listOf(
        "قبض برق دادم 200 هزار",
        "قبض گاز پرداخت کردم 100 هزار"
      )
    billSentences.forEach { sentence ->
      val result = parse(sentence)
      assertEquals("Expected Bills for: $sentence", "Bills", result.category)
    }
  }

  @Test
  fun categoryInferencePersonalCareKeywords() {
    val personalSentences =
      listOf(
        "اصلاح کردم 100 هزار",
        "آرایشگاه رفتم 150 هزار",
        "عطر خریدم 300 هزار"
      )
    personalSentences.forEach { sentence ->
      val result = parse(sentence)
      assertEquals("Expected Other for: $sentence", "Other", result.category)
    }
  }

  @Test
  fun categoryInferenceEducationKeywords() {
    val educationSentences =
      listOf(
        "کلاس ثبت نام کردم 500 هزار",
        "شهریه دانشگاه پرداخت کردم 2 میلیون"
      )
    educationSentences.forEach { sentence ->
      val result = parse(sentence)
      assertEquals("Expected Other for: $sentence", "Other", result.category)
    }
  }

  @Test
  fun categoryInferenceIncomeKeywords() {
    val incomeSentences =
      listOf(
        "حقوق گرفتم 20 میلیون",
        "درآمد داشتم 5 میلیون",
        "واریز شد 10 میلیون"
      )
    incomeSentences.forEach { sentence ->
      val result = parse(sentence)
      assertEquals("Expected Income for: $sentence", "Income", result.category)
    }
  }

  @Test
  fun categoryInferenceLoansKeywords() {
    val loanSentences =
      listOf(
        "قرض دادم 5 میلیون",
        "قرض گرفتم 10 میلیون"
      )
    loanSentences.forEach { sentence ->
      val result = parse(sentence)
      assertEquals("Expected Loans for: $sentence", "Loans", result.category)
    }
  }

  @Test
  fun categoryInferenceDefaultToOther() {
    val otherSentences =
      listOf(
        "چیز عجیبی خریدم 50 هزار"
      )
    otherSentences.forEach { sentence ->
      val result = parse(sentence)
      assertEquals("Expected Other for: $sentence", "Other", result.category)
    }
  }

  // ============================================================
  // Confidence calculation tests
  // ============================================================

  @Test
  fun confidenceMultipleFactorsIncreaseConfidence() {
    val result = parse("دیروز مرغ خریدم 80 هزار تومن به علی")
    assertTrue("Confidence should be >= 0.85", result.confidence >= 0.85f)
  }

  @Test
  fun confidenceAmountOnlyGivesModerateConfidence() {
    val result = parse("500 هزار تومان")
    assertTrue("Confidence should be >= 0.70", result.confidence >= 0.70f)
  }

  @Test
  fun confidenceNoMoneyKeywordsGivesLowConfidence() {
    val result = parse("متن بدون پول")
    assertTrue("Confidence should be <= 0.65", result.confidence <= 0.65f)
  }

  // ============================================================
  // Fallback parser financial-context validation gate
  // ============================================================

  @Test
  fun kotlinFallbackReturnsNullForNonmonetaryNumericString() {
    // A bare year must not be parsed as a transaction (false positive).
    assertNull(GeminiParser.kotlinFallbackParse("سال 1403"))
    // Persian digits are normalized, so a year in Arabic-Indic digits is blocked too.
    assertNull(GeminiParser.kotlinFallbackParse("سال ۱۴۰۳"))
    // A phone-number-like numeric string also lacks monetary context.
    assertNull(GeminiParser.kotlinFallbackParse("شماره من 09123456789 است"))
  }

  @Test
  fun kotlinFallbackIgnoresPhoneNumbersContainingTheTelephoneKeyword() {
    // "تلفن" is a bill category keyword, but a phone NUMBER must not be parsed
    // as a transaction (the whole number would otherwise be extracted as an amount).
    assertNull(GeminiParser.kotlinFallbackParse("شماره تلفن ۰۹۱۲۳۴۵۶۷۸۹"))
    assertNull(GeminiParser.kotlinFallbackParse("تلفن 09123456789"))
    // A bare mobile number is also excluded even without the "تلفن" word.
    assertNull(GeminiParser.kotlinFallbackParse("۰۹۱۲۳۴۵۶۷۸۹"))
  }

  @Test
  fun kotlinFallbackStillParsesARealTelephoneBill() {
    // Phone BILLS carry stronger keywords (قبض/هزینه) so they must still parse.
    val result = GeminiParser.kotlinFallbackParse("قبض تلفن ۵۰ هزار")
    assertNotNull(result)
    assertEquals(500_000L, result!!.amount)
    assertEquals("Bills", result.category)
  }

  @Test
  fun kotlinFallbackParsesBillThatEmbedsTheAccountPhoneNumber() {
    // A bill description that includes the account's phone number alongside a
    // real monetary signal must still parse (phone check only applies to pure
    // phone numbers lacking any bill/payment keyword).
    val result =
      GeminiParser.kotlinFallbackParse("پرداخت قبض تلفن ۰۹۱۲۳۴۵۶۷۸۹ مبلغ ۵۰ هزار")
    assertNotNull(result)
    assertEquals(500_000L, result!!.amount)
    assertEquals("Bills", result.category)
  }

  @Test
  fun kotlinFallbackParsesBillWithPhoneNumberAndAmountWithoutUnit() {
    // A bill that embeds the account phone number and states the amount via
    // "مبلغ <number>" WITHOUT a currency suffix must still parse, and the amount
    // must come from the مبلغ token (not the phone digits).
    val result =
      GeminiParser.kotlinFallbackParse("پرداخت قبض تلفن ۰۹۱۲۳۴۵۶۷۸۹ مبلغ ۵۰۰۰۰")
    assertNotNull(result)
    assertEquals(500_000L, result!!.amount)
    assertEquals("Bills", result.category)
  }

  @Test
  fun kotlinFallbackStillParsesValidMonetaryNumericString() {
    // A number WITH monetary context must still parse (regression guard).
    val result = GeminiParser.kotlinFallbackParse("خرید 500 تومان")
    assertNotNull(result)
    assertEquals(5_000L, result!!.amount)
  }

  // ============================================================
  // classifyInstallment paid vs pending tests
  // ============================================================

  @Test
  fun paidInstallmentWithTasvieReturnsExpense() {
    val result = parse("قسط ماشین را تسویه کردم 3 میلیون")
    assertEquals("EXPENSE", result.type)
    assertEquals("Installments", result.category)
    assertNull(result.daysFromNow)
  }

  @Test
  fun paidInstallmentWithPardakhtReturnsExpense() {
    val result = parse("قسط خانه پرداخت کردم 5 میلیون")
    assertEquals("EXPENSE", result.type)
    assertEquals("Installments", result.category)
    assertNull(result.daysFromNow)
  }

  @Test
  fun paidInstallmentWithDadamReturnsExpense() {
    val result = parse("قسط ماشین دادم 3 میلیون")
    assertEquals("EXPENSE", result.type)
    assertEquals("Installments", result.category)
    assertNull(result.daysFromNow)
  }

  @Test
  fun pendingInstallmentWithoutPaidKeywordReturnsInstallmentType() {
    val result = parse("قسط ماشین 3 میلیون")
    assertEquals("INSTALLMENT", result.type)
    assertEquals("Installments", result.category)
    assertNotNull(result.daysFromNow)
  }

  @Test
  fun installmentWithVarizDoesNotForceExpense() {
    val result = parse("قسط ماشین واریز شد 3 میلیون")
    assertEquals("INSTALLMENT", result.type)
    assertEquals("Installments", result.category)
  }

  @Test
  fun categoryOtherExpenseDescriptionDoesNotIncludeSubjectInParentheses() {
    val result = parse("چیز عجیبی خریدم 50 هزار")
    assertEquals("EXPENSE", result.type)
    assertEquals("Other", result.category)
    assertFalse(
      "Description should not contain parentheses with subject",
      result.description.contains("(چیز عجیبی)")
    )
    assertTrue("Description should mention subject", result.description.contains("چیز عجیبی"))
  }

  @Test
  fun categoryOtherExpenseUsesBaseDescriptionWithoutFormatting() {
    val result = parse("خرج غیرمعمول 100 هزار")
    assertEquals("EXPENSE", result.type)
    assertEquals("Other", result.category)
    assertFalse(
      "Description should not have formatted subject in parentheses",
      result.description.matches(Regex(".*\\(.*\\).*"))
    )
  }

  @Test
  fun nonotherCategoryExpenseIncludesSubjectInParentheses() {
    val result = parse("مرغ خریدم 80 هزار")
    assertEquals("EXPENSE", result.type)
    assertEquals("Food", result.category)
    assertTrue(
      "Description should include subject in parentheses",
      result.description.contains("(") && result.description.contains(")")
    )
  }

  // ============================================================
  // parseJsonResultOffline tests
  // ============================================================

  @Test
  fun parseJsonResultOfflineValidExpenseJson() {
    val json =
      """{"type":"EXPENSE","amount":5000000,"category":"Food","""" +
        """description":"مرغ","personName":null,"dateOffsetDays":0}"""
    val result = GeminiParser.parseJsonResultOffline(json)
    assertNotNull(result)
    assertEquals("EXPENSE", result!!.type)
    // JSON amount is 5,000,000 Toman; parser normalizes to Rial (×10) → 50,000,000.
    assertEquals(50_000_000L, result.amount)
    assertEquals("Food", result.category)
    assertEquals("مرغ", result.description)
    assertNull(result.personName)
    assertEquals(0, result.dateOffsetDays)
  }

  @Test
  fun parseJsonResultOfflineValidIncomeJson() {
    val json =
      """{"type":"INCOME","amount":20000000,"category":"Income","""" +
        """description":"حقوق","personName":null,"dateOffsetDays":0}"""
    val result = GeminiParser.parseJsonResultOffline(json)
    assertNotNull(result)
    assertEquals("INCOME", result!!.type)
    // JSON amount is 20,000,000 Toman; parser normalizes to Rial (×10) → 200,000,000.
    assertEquals(200_000_000L, result.amount)
    assertEquals("Income", result.category)
  }

  @Test
  fun parseJsonResultOfflineValidLoanWithPersonname() {
    val json =
      """{"type":"LOAN_CREDITOR","amount":10000000,"category":"Loans","""" +
        """description":"قرض به علی","personName":"علی","dateOffsetDays":0}"""
    val result = GeminiParser.parseJsonResultOffline(json)
    assertNotNull(result)
    assertEquals("LOAN_CREDITOR", result!!.type)
    assertEquals("علی", result.personName)
  }

  @Test
  fun parseJsonResultOfflineValidInstallment() {
    val json =
      """{"type":"INSTALLMENT","amount":3000000,"category":"Installments","""" +
        """description":"قسط ماشین","personName":null,"dateOffsetDays":0,"daysFromNow":30,"title":"قسط ماشین","""" +
        """notes":"قسط در انتظار پرداخت"}"""
    val result = GeminiParser.parseJsonResultOffline(json)
    assertNotNull(result)
    assertEquals("INSTALLMENT", result!!.type)
    // JSON amount is 3,000,000 Toman; parser normalizes to Rial (×10) → 30,000,000.
    assertEquals(30_000_000L, result.amount)
    assertEquals("قسط ماشین", result.title)
  }

  @Test
  fun parseJsonResultOfflineValidWithConfidence() {
    val json = """{"type":"EXPENSE","amount":1000000,"category":"Food","description":"نان","confidence":0.95}"""
    val result = GeminiParser.parseJsonResultOffline(json)
    assertNotNull(result)
    assertEquals(0.95f, result!!.confidence, 0.01f)
  }

  @Test
  fun parseJsonResultOfflineValidWithoutCategoryalias() {
    val json = """{"type":"EXPENSE","amount":500000,"category":"Other","description":"test"}"""
    val result = GeminiParser.parseJsonResultOffline(json)
    assertNotNull(result)
    assertEquals("Other", result!!.category)
  }

  @Test
  fun parseJsonResultOfflineNullInputReturnsNull() {
    assertNull(GeminiParser.parseJsonResultOffline("null"))
  }

  @Test
  fun parseJsonResultOfflineEmptyStringReturnsNull() {
    assertNull(GeminiParser.parseJsonResultOffline(""))
  }

  @Test
  fun parseJsonResultOfflineMalformedJsonReturnsNull() {
    assertNull(GeminiParser.parseJsonResultOffline("{invalid json"))
  }

  @Test
  fun parseJsonResultOfflineMissingRequiredFieldsReturnsNull() {
    assertNull(GeminiParser.parseJsonResultOffline("""{"type":"EXPENSE"}"""))
  }

  @Test
  fun parseJsonResultOfflineRandomTextReturnsNull() {
    assertNull(GeminiParser.parseJsonResultOffline("just some text"))
  }

  @Test
  fun parseJsonResultOfflineZeroAmountReturnsNull() {
    val json = """{"type":"EXPENSE","amount":0,"category":"Food","description":"test"}"""
    assertNull(GeminiParser.parseJsonResultOffline(json))
  }

  @Test
  fun parseJsonResultOfflineNegativeAmountReturnsNull() {
    val json = """{"type":"EXPENSE","amount":-5000,"category":"Food","description":"test"}"""
    assertNull(GeminiParser.parseJsonResultOffline(json))
  }

  @Test
  fun parseJsonResultOfflineMissingAmountReturnsNull() {
    val json = """{"type":"EXPENSE","category":"Food","description":"test"}"""
    assertNull(GeminiParser.parseJsonResultOffline(json))
  }

  @Test
  fun parseJsonResultOfflineNullDaysfromnowPreservedAsNull() {
    val json =
      """{"type":"INSTALLMENT","amount":3000000,"category":"Installments","""" +
        """description":"قسط","daysFromNow":null}"""
    val result = GeminiParser.parseJsonResultOffline(json)
    assertNotNull(result)
    assertNull("Null JSON null should be null, not 0", result!!.daysFromNow)
  }

  @Test
  fun parseJsonResultOfflineMissingDaysfromnowPreservedAsNull() {
    val json = """{"type":"INSTALLMENT","amount":3000000,"category":"Installments","description":"قسط"}"""
    val result = GeminiParser.parseJsonResultOffline(json)
    assertNotNull(result)
    assertNull("Missing key should be null", result!!.daysFromNow)
  }

  @Test
  fun parseJsonResultOfflineNonnumericDaysfromnowPreservedAsNull() {
    val json =
      """{"type":"INSTALLMENT","amount":3000000,"category":"Installments","""" +
        """description":"قسط","daysFromNow":"abc"}"""
    val result = GeminiParser.parseJsonResultOffline(json)
    assertNotNull(result)
    assertNull("Non-numeric string should be null, not 30", result!!.daysFromNow)
  }

  @Test
  fun parseJsonResultOfflineDaysfromnowZeroIsPreserved() {
    val json =
      """{"type":"INSTALLMENT","amount":3000000,"category":"Installments","""" +
        """description":"قسط","daysFromNow":0}"""
    val result = GeminiParser.parseJsonResultOffline(json)
    assertNotNull(result)
    assertEquals(0, result!!.daysFromNow)
  }
}
