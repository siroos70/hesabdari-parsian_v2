package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

private const val KEYWORD_PARKING = "پارکینگ"
private const val KEYWORD_SHOPPING = "فروشگاه"

/**
 * Keywords that signal the input is about a monetary transaction. Used as a
 * validation gate in [GeminiParser.kotlinFallbackParse] to reject non-monetary
 * numeric strings (e.g. a year "1403", a phone number, or an ID) before digit
 * extraction runs, preventing false-positive transaction parsing.
 */
private val FINANCIAL_CONTEXT_KEYWORDS =
  // Currency units
  listOf(
    "تومان",
    "تومن",
    "ریال"
  ) +
    // Amount multipliers (strong money signal on their own)
    listOf(
      "هزار",
      "میلیون",
      "میلیارد"
    ) +
    // Transaction verbs / nouns
    listOf(
      "خرید",
      "خرج",
      "هزینه",
      "پرداخت",
      "برداشت",
      "واریز",
      "دریافت",
      "پس‌انداز",
      "قسط",
      "قرض",
      "وام",
      "طلب",
      "بدهی",
      "مانده",
      "حساب",
      "فاکتور",
      "صورت‌حساب"
    ) +
    // Income / sale signals
    listOf(
      "حقوق",
      "درآمد",
      "فروش",
      "سود"
    ) +
    // Common category words (mirrors the categoryKeywords map inside the parser)
    listOf(
      "برق",
      "آب",
      "گاز",
      "تلفن",
      "قبض",
      "بنزین",
      "تاکسی",
      "مترو",
      "اتوبوس",
      KEYWORD_PARKING,
      "غذا",
      "رستوران",
      "ناهار",
      "شام",
      "صبحانه",
      "بستنی",
      "مرغ",
      "گوشت",
      "ماهی",
      "سبزی",
      "میوه",
      "شیر",
      "تخم",
      "پنیر",
      "نان",
      "لباس",
      "کفش",
      KEYWORD_SHOPPING,
      "آموزش",
      "کلاس",
      "مدرسه",
      "دانشگاه",
      "درمان",
      "دارو",
      "بیمارستان",
      "پزشک",
      "اصلاح",
      "آرایشگاه",
      "هدیه",
      "جشن",
      "مراسم",
      "خیریه",
      "صدقه",
      "سرمایه‌گذاری",
      "صندوق",
      "سهام",
      "اجاره",
      "رهن",
      "اسنپ",
      "کرایه",
      "نوشابه"
    )

private val DIGIT_PATTERN = Regex("""[0-9\u06F0-\u06F9\u0660-\u0669]""")

private fun containsDigits(text: String): Boolean = DIGIT_PATTERN.containsMatchIn(text)

private fun hasFinancialContext(text: String): Boolean = FINANCIAL_CONTEXT_KEYWORDS.any { text.contains(it) }

object GeminiParser {
  private const val TAG = "GeminiParser"

  private const val TYPE_EXPENSE = "EXPENSE"
  private const val TYPE_INCOME = "INCOME"

  private const val KEYWORD_OT = "اضافه کار"
  private const val KEYWORD_INVESTMENT = "سرمایه گذاری"
  private const val KEYWORD_PAYMENT = "پرداخت"
  private const val KEYWORD_DID_PAY = "پرداخت کردم"
  private const val KEYWORD_HAVE_CLAIM = "طلب دارم"
  private const val KEYWORD_CREDITOR = "طلبکار"
  private const val KEYWORD_DEBTOR = "بدهکار"
  private const val TYPE_LOAN_DEBTOR = "LOAN_DEBTOR"
  private const val TYPE_LOAN_CREDITOR = "LOAN_CREDITOR"
  private const val TYPE_INSTALLMENT = "INSTALLMENT"
  private const val KEY_DAYS_FROM_NOW = "daysFromNow"
  private const val KEYWORD_ICE_CREAM = "بستنی"

  private const val DAY_MS = 24L * 60L * 60L * 1000L

  private const val TOMAN_TO_RIAL = 10L
  private const val BILLION_SCALE = 1_000_000_000L

  private const val CATEGORY_FOOD = "Food"
  private const val CATEGORY_TRANSPORTATION = "Transportation"
  private const val CATEGORY_SHOPPING = "Shopping"
  private const val CATEGORY_BILLS = "Bills"
  private const val CATEGORY_INSTALLMENTS = "Installments"
  private const val CATEGORY_LOANS = "Loans"
  private const val CATEGORY_INCOME = "Income"
  private const val CATEGORY_OTHER = "Other"
  private const val CATEGORY_PERSONAL_CARE = "Personal Care"
  private const val CATEGORY_EDUCATION = "Education"
  private const val CATEGORY_RENT_UTILITIES = "Rent & Utilities"
  private const val CATEGORY_LOANS_DEBT = "Loans & Debt"
  private const val CATEGORY_EVENTS_GIFTS = "Events & Gifts"
  private const val CATEGORY_CHARITY = "Charity"
  private const val CATEGORY_INVESTMENT = "Investment"

  private val VALID_TYPES = listOf(TYPE_EXPENSE, TYPE_INCOME, TYPE_LOAN_DEBTOR, TYPE_LOAN_CREDITOR, TYPE_INSTALLMENT)

  private const val KEYWORD_TOMAN = "تومان"
  private const val KEYWORD_HAZAR = "هزار"
  private const val MIN_JALALI_DAY = 1
  private const val MAX_JALALI_DAY = 31

  suspend fun parseSentence(
    sentence: String,
    config: AiProviderConfig? = null
  ): ParsedResult? =
    withContext(Dispatchers.IO) {
      AppLogger.d(
        TAG,
        "parseSentence: config=${config?.let {
          "provider=${it.providerType}, isConfigured=${it.isConfigured}, model=${it.model}"
        } ?: "null"}"
      )
      val providerConfig = config ?: AiProviderConfig()
      if (!providerConfig.isConfigured) {
        AppLogger.w(TAG, "AI provider not configured, using offline local parser fallback")
        return@withContext parseSentenceOffline(sentence)
      }

      val systemInstruction =
        """
        You are a smart financial analyzer for a Persian accounting app. Parse Persian text and return JSON:
        {
          "type": "EXPENSE" | "INCOME" | "LOAN_DEBTOR" | "LOAN_CREDITOR" | "INSTALLMENT",
          "amount": double (Toman amount. Convert: 5 میلیون = 5000000, 450 هزار = 450000),
          "category": "Food" | "Transportation" | "Shopping" | "Bills" | "Installments" | "Loans" | "Income" | "Other",
          "personName": string (person name if specified, else null),
          "description": string (Persian description. For INSTALLMENT type, use future-oriented like 'قسط آینده' not 'پرداخت شده'),
          "daysFromNow": integer (calculate actual days from today to the Jalali date specified. Today's date is ${java.time.LocalDate.now()}. If text says '25 تیر', convert that Jalali date to Gregorian and compute days from today),
          "title": string (installment title like 'قسط ماشین', or null),
          "dateOffsetDays": integer ('دیروز'=-1, 'پریروز'=-2, 'فردا'=1, 'امروز'=0, default 0),
          "hour": integer (0-23, 'ساعت ۸ شب'=20, else null),
          "minute": integer (0-59, 'ساعت ۲ و نیم'=30, else null),
          "confidence": float (0.0-1.0 based on text clarity),
          "notes": string (for INSTALLMENT: 'قسط در انتظار پرداخت'. For loans: brief note. Or null)
        }
        Jalali months: فروردین(1), اردیبهشت(2), خرداد(3), تیر(4), مرداد(5), شهریور(6), مهر(7), آبان(8), آذر(9), دی(10), بهمن(11), اسفند(12). Days: months 1-6 have 31 days, months 7-11 have 30 days, month 12 has 29 (30 in leap years).
        Persian examples:
        - "۵۰۰ هزار تومن بابت برق" -> type="EXPENSE", amount=500000, category="Bills", description="پرداخت قبض برق"
        - "امروز حقوق گرفتم ۲۰ میلیون" -> type="INCOME", amount=20000000, category="Income", description="دریافت حقوق"
        - "به علی ۲ میلیون قرض دادم" -> type="LOAN_DEBTOR", amount=2000000, category="Loans", personName="علی"
        - "قسط ماشین 25 تیر 10 میلیون" -> type="INSTALLMENT", amount=10000000, category="Installments", title="قسط ماشین", daysFromNow=(actual days to 25 Tir), description="قسط آینده", notes="قسط در انتظار پرداخت"
        Return ONLY raw JSON, no markdown tags.
        """.trimIndent()

      when (
        val result =
          AiProvider.generateContent(
            config = providerConfig,
            prompt = sentence,
            systemInstruction = systemInstruction,
            temperature = 0.1,
            responseMimeType = "application/json"
          )
      ) {
        is AiProvider.ApiResult.Success -> {
          AppLogger.d(TAG, "AI parsed output received")
          parseJsonResultOffline(result.text) ?: parseSentenceOffline(sentence)
        }
        is AiProvider.ApiResult.Failure -> {
          AppLogger.e(TAG, "AI parse failed: ${result.error}, falling back to offline")
          parseSentenceOffline(sentence)
        }
      }
    }

  internal fun parseJsonResultOffline(jsonStr: String): ParsedResult? {
    val aiResult =
      io.github.mojri.hesabyar.rust.RustBridge
        .parseAiTransactionJsonSync(jsonStr)
    if (aiResult != null) {
      if (aiResult.wasRepaired) {
        AppLogger.w(TAG, "AI result repaired by Rust: ${aiResult.repairNotes.joinToString()}")
      }
      return mapRustParsedResult(aiResult.result)
    }
    // Fallback: parse manually if Rust unavailable
    return parseJsonResultFallback(jsonStr)
  }

  private fun mapRustParsedResult(r: io.github.mojri.hesabyar.rust.ParsedResult): ParsedResult =
    ParsedResult(
      type = r.txType.name,
      amount = r.amount,
      category = r.category,
      personName = r.personName,
      description = r.description,
      daysFromNow = r.daysFromNow,
      title = r.title,
      dateOffsetDays = r.dateOffsetDays,
      hour = r.hour,
      minute = r.minute,
      confidence = r.confidence,
      notes = r.notes
    )

  internal fun parseJsonResultFallback(jsonStr: String): ParsedResult? {
    return try {
      val json = JSONObject(jsonStr)
      val type = json.optString("type", TransactionType.EXPENSE.name)
      val amount = json.optLong("amount", 0L)
      val validAmount = amount.takeIf { it in 1..Long.MAX_VALUE / TOMAN_TO_RIAL } ?: return null
      val amountRial = validAmount * TOMAN_TO_RIAL
      val category = json.optString("category", CATEGORY_OTHER)
      val personName = json.optString("personName").takeIf { it.isNotEmpty() }
      val description = json.optString("description").takeIf { it.isNotEmpty() } ?: ""
      val daysFromNow =
        if (json.has(KEY_DAYS_FROM_NOW) && !json.isNull(KEY_DAYS_FROM_NOW)) {
          try {
            json.getInt(KEY_DAYS_FROM_NOW)
          } catch (_: Exception) {
            null
          }
        } else {
          null
        }
      val title = json.optString("title").takeIf { it.isNotEmpty() }
      val dateOffsetDays = json.optInt("dateOffsetDays", 0)
      val hour = json.optInt("hour", -1).let { if (it >= 0) it else null }
      val minute = json.optInt("minute", -1).let { if (it >= 0) it else null }
      val confidence = json.optDouble("confidence", 0.8).toFloat()
      val notes = json.optString("notes").takeIf { it.isNotEmpty() }

      ParsedResult(
        type = if (type in VALID_TYPES) type else TransactionType.EXPENSE.name,
        amount = amountRial,
        category = category,
        personName = personName,
        description = description,
        daysFromNow = daysFromNow,
        title = title,
        dateOffsetDays = dateOffsetDays,
        hour = hour,
        minute = minute,
        confidence = confidence,
        notes = notes
      )
    } catch (e: JSONException) {
      AppLogger.e(TAG, "Failed to parse JSON result", e)
      null
    }
  }

  // parseSentenceOffline now delegates to Rust — see below

  /**
   * Parse a sentence offline using the Rust core.
   *
   * Maps from Rust's [io.github.mojri.hesabyar.rust.ParsedResult] (which uses
   * [TransactionType] enum) to the Kotlin [ParsedResult] (which uses a String type).
   *
   * @param rawSentence The user's input sentence in Persian.
   * @param nowMs Epoch milliseconds (UTC) used to compute the Jalali "today"
   *   date. It affects both `daysFromNow` (installment due dates) and
   *   explicit Jalali date offsets in `dateOffsetDays` (e.g. "۲۵ تیر" is
   *   resolved to days-from-today via detectDateOffset →
   *   detectExplicitJalaliOffset, which depends on `today`). Only relative-word
   *   offsets («دیروز»=-1, «فردا»=+1, etc.) are fixed constants and do not
   *   depend on `nowMs`. The value defaults to [System.currentTimeMillis] so
   *   production behavior is unchanged and tests can pin a fixed "today".
   */
  fun parseSentenceOffline(
    rawSentence: String,
    nowMs: Long = System.currentTimeMillis(),
  ): ParsedResult? {
    AppLogger.d(TAG, "Using offline Rust parser")
    val rustResult =
      io.github.mojri.hesabyar.rust.RustBridge
        .parseSentenceOfflineSync(rawSentence, nowMs)
    if (rustResult != null) {
      return mapRustParsedResult(rustResult)
    }
    AppLogger.d(TAG, "Rust parser unavailable, using Kotlin fallback")
    return kotlinFallbackParse(
      rawSentence,
      JalaliCalendarHelper.gregorianToJalali(nowMs),
    )
  }

  /**
   * Detects input that is actually a phone number rather than a transaction.
   * Phone-number phrasing (e.g. "شماره تلفن ۰۹۱۲...") can contain the `تلفن`
   * category keyword, which would otherwise pass [hasFinancialContext] and let
   * [extractAmount] pull the digits as a transaction amount. We exclude such
   * input up front so it never parses as a transaction.
   */
  private val phoneNumberPattern = Regex("""09\d{9}|0\d{10}""")

  // Strong bill/payment words that distinguish a real bill (e.g. "قبض تلفن ۵۰
  // هزار") from a bare phone number that merely mentions "تلفن". Category words
  // like برق/آب/گاز/بنزین are intentionally excluded so a phone number with a
  // category label is still treated as a phone number unless a money signal exists.
  // Explicit money-unit words that confirm a distinct monetary amount (as
  // opposed to transaction wording like قبض/پرداخت). Used to allow a detected
  // phone number through only when a real amount is present.
  private val moneyUnitKeywords =
    listOf("تومان", "تومن", "ریال", "هزار", "میلیون", "میلیارد")

  private val billPaymentKeywords =
    listOf(
      "تومان",
      "تومن",
      "ریال",
      "هزار",
      "میلیون",
      "میلیارد",
      "خرید",
      "خرج",
      "هزینه",
      "پرداخت",
      "برداشت",
      "واریز",
      "دریافت",
      "پس‌انداز",
      "قسط",
      "قرض",
      "وام",
      "طلب",
      "بدهی",
      "مانده",
      "حساب",
      "فاکتور",
      "صورت‌حساب",
      "قبض"
    )

  private fun looksLikePhoneNumber(text: String): Boolean {
    val normalized = normalizePersianDigits(text)
    if (normalized.contains("شماره تلفن") ||
      normalized.contains("شماره موبایل") ||
      normalized.contains("شماره همراه")
    ) {
      return true
    }
    return phoneNumberPattern.containsMatchIn(normalized)
  }

  internal fun kotlinFallbackParse(
    rawSentence: String,
    today: JalaliCalendarHelper.JalaliDate = JalaliCalendarHelper.gregorianToJalali(System.currentTimeMillis()),
  ): ParsedResult? {
    val normalized = normalizePersianDigits(rawSentence)
    // Validation gate: block non-monetary numeric strings (e.g. a year "۱۴۰۳"
    // or a bare phone number) that contain digits but no financial context.
    // A telephone bill that embeds the account's phone number (e.g.
    // "پرداخت قبض تلفن ۰۹۱۲... مبلغ ۵۰ هزار") still carries a bill/payment signal,
    // so it must NOT be blocked as a phone number. However, transaction wording
    // alone (قبض/پرداخت/...) must NOT let a detected phone number through: the
    // fallback amount extractor treats any digit run as an amount, so a
    // phone-number-only bill would otherwise be parsed with the phone digits as
    // its amount. Therefore a detected phone number is skipped unless a distinct
    // monetary amount — signalled by an explicit money unit (تومان/ریال/هزار/...) —
    // is present. Bare years and pure phone numbers are blocked via the absence
    // of financial context / money unit.
    // Pure non-numeric text is left to fall through to the (null) amount extraction below.
    if (containsDigits(rawSentence)) {
      val isPhone = looksLikePhoneNumber(normalized)
      val hasContext = hasFinancialContext(rawSentence)
      val hasMoneyUnit = moneyUnitKeywords.any { normalized.contains(it) }
      val hasBillingAmount = billingAmountPattern.containsMatchIn(normalized)
      val shouldSkip = !hasContext && !isPhone || isPhone && !hasMoneyUnit && !hasBillingAmount
      if (shouldSkip) {
        val skipReason =
          if (isPhone) "phone without monetary amount" else "no financial context"
        AppLogger.d(TAG, "kotlinFallbackParse: skipped: $skipReason")
        return null
      }
    }
    val amount = extractAmount(normalized) ?: return null
    if (amount <= 0L) return null
    val type = detectType(rawSentence)
    val category = detectCategory(rawSentence, type)
    val personName = extractPersonName(rawSentence)
    val dateOffsetDays = detectDateOffset(rawSentence, today)
    return ParsedResult(
      type = type,
      amount = amount,
      category = category,
      personName = personName,
      description = rawSentence,
      daysFromNow = null,
      title = null,
      dateOffsetDays = dateOffsetDays,
      hour = null,
      minute = null,
      confidence = 0.5f,
      notes = null
    )
  }

  private fun normalizePersianDigits(text: String): String {
    val sb = StringBuilder(text.length)
    for (c in text) {
      when (c) {
        in '\u06F0'..'\u06F9' -> sb.append((c.code - 0x06F0 + '0'.code).toChar())
        in '\u0660'..'\u0669' -> sb.append((c.code - 0x0660 + '0'.code).toChar())
        else -> sb.append(c)
      }
    }
    return sb.toString()
  }

  // "مبلغ <number>" (optionally followed by a scale word) marks an explicit
  // monetary amount, used both as a validation signal and as the preferred
  // amount source so a phone number is never extracted as the amount.
  private val billingAmountPattern =
    Regex("""مبلغ\s*(\d[\d,]*)\s*(میلیارد|میلیون|هزار)?""", RegexOption.IGNORE_CASE)

  private fun extractAmount(text: String): Long? {
    // Match patterns like "5 میلیون", "450 هزار", "1,500,000", "۵۰۰۰۰۰"
    val millionPattern = Regex("""(\d[\d,]*)\s*(?:میلیون|million)""", RegexOption.IGNORE_CASE)
    val hazarPattern = Regex("""(\d[\d,]*)\s*(?:هزار|hazar)""", RegexOption.IGNORE_CASE)
    val plainPattern = Regex("""(\d[\d,]+)""")

    // A "مبلغ <number>" token is an explicit monetary amount. Match it first so a
    // preceding phone number is never mistaken for the amount, and so a bill that
    // states the amount without a currency suffix still parses correctly.
    billingAmountPattern.find(text)?.let { m ->
      val num = m.groupValues[1].replace(",", "").toLongOrNull() ?: return@let
      return when (m.groupValues[2]) {
        "هزار" -> scaleAmount(num, 1_000 * TOMAN_TO_RIAL)
        "میلیون" -> scaleAmount(num, 1_000_000 * TOMAN_TO_RIAL)
        "میلیارد" -> scaleAmount(num, BILLION_SCALE * TOMAN_TO_RIAL)
        else -> scaleAmount(num, TOMAN_TO_RIAL)
      }
    }

    millionPattern.find(text)?.let { m ->
      val num = m.groupValues[1].replace(",", "").toLongOrNull() ?: return@let
      return scaleAmount(num, 1_000_000 * TOMAN_TO_RIAL)
    }
    hazarPattern.find(text)?.let { m ->
      val num = m.groupValues[1].replace(",", "").toLongOrNull() ?: return@let
      return scaleAmount(num, 1_000 * TOMAN_TO_RIAL)
    }
    plainPattern.find(text)?.let { m ->
      val num = m.groupValues[1].replace(",", "").toLongOrNull() ?: return@let
      return scaleAmount(num, TOMAN_TO_RIAL)
    }
    return null
  }

  /**
   * Scale [num] (in Toman) to Rial by [multiplier] without overflowing [Long].
   * Returns null when the product would wrap, so callers never receive a
   * negative/wrapped amount. Mirrors the overflow guard in [parseJsonResultFallback].
   */
  private fun scaleAmount(
    num: Long,
    multiplier: Long
  ): Long? {
    if (num > 0 && num > Long.MAX_VALUE / multiplier) return null
    return num * multiplier
  }

  private fun detectType(text: String): String {
    if (looksLikeIncome(text)) return TYPE_INCOME
    if (text.contains("قسط")) return TYPE_INSTALLMENT
    if (looksLikeLoanDebtor(text)) return TYPE_LOAN_DEBTOR
    if (looksLikeLoanCreditor(text)) return TYPE_LOAN_CREDITOR
    return TYPE_EXPENSE
  }

  private fun looksLikeIncome(text: String): Boolean =
    text.contains(KEYWORD_OT) ||
      text.contains("حقوق") ||
      text.contains("درآمد") ||
      text.contains("واریز") ||
      text.contains("سود") ||
      looksLikeSale(text)

  private fun looksLikeSale(text: String): Boolean = text.contains("فروش") && !text.contains(KEYWORD_SHOPPING)

  private fun looksLikeLoanDebtor(text: String): Boolean =
    text.contains(KEYWORD_CREDITOR) || text.contains("طلب دارم") || text.contains("قرض دادم")

  private fun looksLikeLoanCreditor(text: String): Boolean =
    text.contains(KEYWORD_DEBTOR) || text.contains("قرض گرفتم") || text.contains("وام")

  private val categoryKeywords =
    mapOf(
      CATEGORY_TRANSPORTATION to listOf(KEYWORD_PARKING, "بنزین", "تاکسی", "اتوبوس", "مترو"),
      CATEGORY_BILLS to listOf("برق", "آب", "گاز", "تلفن", "قبض"),
      CATEGORY_RENT_UTILITIES to listOf("اجاره", "رهن"),
      CATEGORY_FOOD to
        listOf(
          "غذا",
          "رستوران",
          "ناهار",
          "شام",
          "صبحانه",
          "بستنی",
          "مرغ",
          "گوشت",
          "ماهی",
          "سبزی",
          "میوه",
          "شیر",
          "تخم",
          "پنیر",
          "نان"
        ),
      CATEGORY_SHOPPING to listOf("لباس", "کفش", KEYWORD_SHOPPING),
      CATEGORY_EDUCATION to listOf("آموزش", "کلاس", "مدرسه", "دانشگاه"),
      CATEGORY_PERSONAL_CARE to listOf("درمان", "دارو", "بیمارستان", "پزشک", "اصلاح", "آرایشگاه"),
      CATEGORY_EVENTS_GIFTS to listOf("هدیه", "جشن", "مراسم"),
      CATEGORY_CHARITY to listOf("خیریه", "صدقه"),
      CATEGORY_INVESTMENT to listOf(KEYWORD_INVESTMENT, "صندوق", "سهام")
    )

  private fun containsAny(
    text: String,
    keywords: List<String>
  ): Boolean = keywords.any { text.contains(it) }

  private fun detectCategory(
    text: String,
    type: String
  ): String {
    if (type == TYPE_INCOME) return CATEGORY_INCOME
    if (type == TYPE_INSTALLMENT) return CATEGORY_INSTALLMENTS
    if (type == TYPE_LOAN_DEBTOR || type == TYPE_LOAN_CREDITOR) return CATEGORY_LOANS
    for ((category, keywords) in categoryKeywords) {
      if (containsAny(text, keywords)) return category
    }
    return CATEGORY_OTHER
  }

  private fun extractPersonName(text: String): String? {
    val patterns =
      listOf(
        Regex("""(?:به|از)\s+(\S+)"""),
        Regex("""(?:قرض دادم به|قرض گرفتم از)\s+(\S+)""")
      )
    for (p in patterns) {
      p
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { return it }
    }
    return null
  }

  private fun detectDateOffset(
    text: String,
    today: JalaliCalendarHelper.JalaliDate
  ): Int {
    detectExplicitJalaliOffset(text, today)?.let { return it }
    return when {
      text.contains("دیروز") -> -1
      text.contains("پریروز") -> -2
      text.contains("پس‌فردا") || text.contains("پسفردا") -> 2
      text.contains("فردا") -> 1
      else -> 0
    }
  }

  /**
   * Parses an explicit Jalali date mention like "۲۵ تیر" or "تیر ۵" and returns the offset in days
   * from today (same-year assumption, mirroring the AI prompt's date math). Returns null when no
   * Jalali date is found so callers fall back to relative-word detection.
   */
  private fun detectExplicitJalaliOffset(
    text: String,
    today: JalaliCalendarHelper.JalaliDate
  ): Int? {
    val normalized = normalizePersianDigits(text)
    val monthByName =
      mapOf(
        "فروردین" to 1,
        "اردیبهشت" to 2,
        "خرداد" to 3,
        "تیر" to 4,
        "مرداد" to 5,
        "شهریور" to 6,
        "مهر" to 7,
        "آبان" to 8,
        "آذر" to 9,
        "دی" to 10,
        "بهمن" to 11,
        "اسفند" to 12
      )
    return monthByName.entries.firstNotNullOfOrNull { (name, month) ->
      computeJalaliDayOffset(normalized, name, month, today)
    }
  }

  private fun computeJalaliDayOffset(
    normalized: String,
    name: String,
    month: Int,
    today: JalaliCalendarHelper.JalaliDate
  ): Int? {
    val dayNum = extractJalaliDay(normalized, name) ?: return null
    val targetYear =
      if (month < today.month || month == today.month && dayNum < today.day) {
        today.year + 1
      } else {
        today.year
      }
    val todayCal = JalaliCalendarHelper.jalaliToGregorian(today.year, today.month, today.day)
    val targetCal = JalaliCalendarHelper.jalaliToGregorian(targetYear, month, dayNum)
    return todayCal?.let { tCal ->
      targetCal?.let { dCal -> ((dCal.timeInMillis - tCal.timeInMillis) / DAY_MS).toInt() }
    }
  }

  private fun extractJalaliDay(
    normalized: String,
    name: String
  ): Int? {
    val dayStr =
      """(?<![\d])(\d{1,2})\s*$name(?!\p{L})"""
        .toRegex()
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?: """(?<![\d])$name(?!\p{L})\s*(\d{1,2})(?![\d])"""
          .toRegex()
          .find(normalized)
          ?.groupValues
          ?.getOrNull(1)
    return dayStr?.toIntOrNull()?.takeIf { it in MIN_JALALI_DAY..MAX_JALALI_DAY }
  }
}

data class ParsedResult(
  val type: String,
  val amount: Long,
  val category: String,
  val personName: String?,
  val description: String,
  val daysFromNow: Int?,
  val title: String?,
  val dateOffsetDays: Int? = 0,
  val hour: Int? = null,
  val minute: Int? = null,
  val confidence: Float = 0.8f,
  val notes: String? = null
)
