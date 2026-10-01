package io.github.mojri.hesabyar.ui

import io.github.mojri.hesabyar.core.MathUtils
import java.util.Calendar
import java.util.TimeZone

/**
 * Abstraction over the native (Rust) core used by [JalaliCalendarHelper].
 *
 * The helper deliberately avoids a compile-time dependency on the `rust`
 * package so it can be compiled standalone (e.g. by the pure-JVM benchmark
 * module). The app installs a provider via [JalaliCalendarHelper.bridgeProvider]
 * that lazily initializes the Rust core on first use. When it returns null the
 * pure-Kotlin fallback paths below are used, keeping the helper fully
 * functional offline.
 */
internal interface JalaliNativeBridge {
  fun gregorianToJalaliSync(timestampMs: Long): Long

  fun jalaliToGregorianSync(
    year: Int,
    month: Int,
    day: Int
  ): Long

  fun getJalaliDaysInMonthSync(
    year: Int,
    month: Int
  ): Int

  fun isJalaliLeapYearSync(year: Int): Boolean
}

/** Remainders of (year % 33) that identify a leap year in the Iranian calendar. */
private val JALALI_LEAP_REMAINDERS = setOf(1, 5, 9, 13, 17, 22, 26, 30)

object JalaliCalendarHelper {
  private const val PACKED_DATE_INVALID = 0L
  private const val YEAR_SHIFT = 16
  private const val MONTH_SHIFT = 8

  /**
   * Provider for the native core bridge, installed by the app module. Kept as
   * a decoupled lambda (not a direct `rust` dependency) so this helper compiles
   * standalone. When it returns null the pure-Kotlin fallback paths are used.
   */
  internal var bridgeProvider: (() -> JalaliNativeBridge?)? = null

  private fun resolveBridge(): JalaliNativeBridge? = bridgeProvider?.invoke()

  data class JalaliDate(
    val year: Int,
    val month: Int,
    val day: Int
  ) {
    override fun toString(): String = String.format(java.util.Locale.US, "%04d/%02d/%02d", year, month, day)
  }

  fun isJalaliLeapYear(year: Int): Boolean =
    resolveBridge()?.isJalaliLeapYearSync(year)
      ?: year % 33 in JALALI_LEAP_REMAINDERS

  fun getDaysInMonth(
    year: Int,
    month: Int
  ): Int {
    // Native call may return -1 if the Rust core is unavailable or failed, or
    // null if no bridge is installed. In either case fall back to the
    // pure-Kotlin Jalali month-length logic for the exact correct value.
    val fromNative = resolveBridge()?.getJalaliDaysInMonthSync(year, month)
    return if (fromNative != null && fromNative > 0) {
      fromNative
    } else {
      jalaliDaysInMonthLocal(year, month)
    }
  }

  fun gregorianToJalali(timestamp: Long): JalaliDate {
    // Extract device-local Gregorian date first, then convert to Jalali.
    // Using the raw epoch timestamp directly would use UTC and can shift
    // dates by one day for users ahead of UTC (e.g. Iran UTC+3:30).
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    return gregorianToJalali(
      cal.get(Calendar.YEAR),
      cal.get(Calendar.MONTH) + 1,
      cal.get(Calendar.DAY_OF_MONTH)
    )
  }

  private fun unpackJalaliDate(packed: Long): JalaliDate? {
    if (packed == PACKED_DATE_INVALID || packed == Long.MIN_VALUE) return null
    val year = (packed shr YEAR_SHIFT).toInt()
    val month = (packed shr MONTH_SHIFT and 0xFF).toInt()
    val day = (packed and 0xFF).toInt()
    return JalaliDate(year, month, day)
  }

  /**
   * Pure-Kotlin Gregorian→Jalali conversion, mirroring the Rust core
   * (calendar.rs: gregorian_to_jalali_date). Used when the native bridge is
   * unavailable so callers stay functional. Returns null for invalid input.
   *
   * The lookup tables below are hoisted to object-level constants so the hot
   * conversion path never allocates on every call (the JMH benchmark tracks
   * this routine's average time, which runs on every transaction/reminder/report
   * render).
   */
  private val GREGORIAN_MONTH_MAX_DAYS = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
  private val GREGORIAN_MONTH_MAX_DAYS_LEAP = intArrayOf(0, 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
  private val GREGORIAN_MONTH_DAY_OFFSETS = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 0)
  private val JALALI_MONTH_DAY_COUNTS = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

  fun gregorianToJalaliLocal(
    gYear: Int,
    gMonth: Int,
    gDay: Int
  ): JalaliDate? {
    if (gMonth < 1 || gMonth > 12) return null
    val isLeap = gYear % 4 == 0 && gYear % 100 != 0 || gYear % 400 == 0
    val maxDays = if (isLeap) GREGORIAN_MONTH_MAX_DAYS_LEAP else GREGORIAN_MONTH_MAX_DAYS
    if (gDay < 1 || gDay > maxDays[gMonth]) return null

    val gy = gYear - 1600
    val gm = gMonth - 1
    val gd = gDay - 1

    var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
    gDayNo += GREGORIAN_MONTH_DAY_OFFSETS[gm]
    if (gm > 1 && isLeap) gDayNo += 1
    gDayNo += gd

    var jDayNo = gDayNo - 79
    val jNp = jDayNo / 12053
    jDayNo %= 12053
    var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
    jDayNo %= 1461
    if (jDayNo >= 366) {
      jy += (jDayNo - 1) / 365
      jDayNo = (jDayNo - 1) % 365
    }

    var i = 0
    while (i < 11 && jDayNo >= JALALI_MONTH_DAY_COUNTS[i]) {
      jDayNo -= JALALI_MONTH_DAY_COUNTS[i]
      i++
    }

    return JalaliDate(jy, i + 1, jDayNo + 1)
  }

  fun gregorianToJalali(
    gYear: Int,
    gMonth: Int,
    gDay: Int
  ): JalaliDate {
    // Validate the Gregorian input up front. Calendar.set() below silently
    // normalizes invalid dates (e.g. 2024-02-30 -> 2024-03-01) before the native
    // bridge sees them, so the native path would otherwise accept inputs the
    // pure-Kotlin fallback rejects. Validating first keeps both paths
    // consistent: invalid dates throw here regardless of native availability.
    val local =
      gregorianToJalaliLocal(gYear, gMonth, gDay)
        ?: throw IllegalStateException(
          "Failed to convert Gregorian date ($gYear-$gMonth-$gDay) to Jalali: invalid date"
        )

    // Encode the Gregorian Y/M/D as a UTC-midnight timestamp so the native
    // core (which interprets timestamps in UTC) returns the Jalali date that
    // corresponds to this *local* calendar day.
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utcCal.set(gYear, gMonth - 1, gDay)
    utcCal.set(Calendar.HOUR_OF_DAY, 0)
    utcCal.set(Calendar.MINUTE, 0)
    utcCal.set(Calendar.SECOND, 0)
    utcCal.set(Calendar.MILLISECOND, 0)
    val fromNative =
      resolveBridge()
        ?.gregorianToJalaliSync(utcCal.timeInMillis)
        ?.let { unpackJalaliDate(it) }
    // Prefer the native result; fall back to the already-validated local conversion.
    return fromNative ?: local
  }

  fun jalaliToGregorian(
    jYear: Int,
    jMonth: Int,
    jDay: Int
  ): Calendar? {
    val timestampMs =
      resolveBridge()?.jalaliToGregorianSync(jYear, jMonth, jDay)
    if (timestampMs != null && timestampMs != Long.MIN_VALUE) {
      // Native returns UTC midnight for the given Jalali date. To avoid 1-day
      // shift in UTC-negative timezones, extract Y/M/D in UTC then build a
      // *local* Calendar from those fields (not from the raw timestamp).
      val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
      utcCal.timeInMillis = timestampMs
      val cal = Calendar.getInstance()
      cal.set(
        utcCal.get(Calendar.YEAR),
        utcCal.get(Calendar.MONTH),
        utcCal.get(Calendar.DAY_OF_MONTH),
        0,
        0,
        0
      )
      cal.set(Calendar.MILLISECOND, 0)
      return cal
    }
    // Native bridge unavailable: use the pure-Kotlin conversion.
    return jalaliToGregorianLocal(jYear, jMonth, jDay)
  }

  /**
   * Returns the inclusive `[start, end]` millisecond boundaries of the Jalali
   * month containing [timestamp]:
   * - `start` = 00:00:00.000 of the 1st day of that month
   * - `end`   = 23:59:59.999 of the last day of that month
   *
   * `end` is exactly (start of the following month) − 1 ms, so filtering with
   * `date in start..end` is equivalent to `date >= start && date < nextMonthStart`.
   *
   * Both values are expressed in the device's local timezone (consistent with
   * [gregorianToJalali] and [jalaliToGregorian]). If the conversion fails for an
   * extreme input, a best-effort 30-day window around [timestamp] is returned so
   * callers still get a finite, ordered range.
   */
  fun getJalaliMonthBoundaries(timestamp: Long): Pair<Long, Long> {
    val jalaliDate = gregorianToJalali(timestamp)

    val startCal = jalaliToGregorian(jalaliDate.year, jalaliDate.month, 1)
    val start =
      startCal?.timeInMillis
        ?: saturatingSubtract(timestamp, 15L * 24 * 60 * 60 * 1000)

    val nextMonth = if (jalaliDate.month == 12) 1 else jalaliDate.month + 1
    val nextMonthYear = if (jalaliDate.month == 12) jalaliDate.year + 1 else jalaliDate.year
    val nextMonthStartCal = jalaliToGregorian(nextMonthYear, nextMonth, 1)
    val nextMonthStart =
      nextMonthStartCal?.timeInMillis
        ?: saturatingAdd(timestamp, 15L * 24 * 60 * 60 * 1000)

    return start to saturatingSubtract(nextMonthStart, 1L)
  }

  /**
   * Saturating `a + b` that clamps to [Long.MIN_VALUE]/[Long.MAX_VALUE] instead of
   * wrapping, delegating to [MathUtils.saturatingAdd].
   */
  internal fun saturatingAdd(
    a: Long,
    b: Long
  ): Long = MathUtils.saturatingAdd(a, b)

  /**
   * Saturating `a - b` that clamps to [Long.MIN_VALUE]/[Long.MAX_VALUE] instead of
   * wrapping, delegating to [MathUtils.saturatingSub].
   */
  internal fun saturatingSubtract(
    a: Long,
    b: Long
  ): Long = MathUtils.saturatingSub(a, b)

  /**
   * UTC half-open `[start, nextMonthStart)` boundaries of the Jalali month
   * containing [timestamp], matching the Rust core's `compute_dashboard_data`
   * (which interprets epoch-ms timestamps in UTC).
   *
   * Both values are epoch-ms in UTC:
   * - `start`          = 00:00:00.000 UTC of the 1st day of the Jalali month
   * - `nextMonthStart` = 00:00:00.000 UTC of the 1st day of the *next* month
   *   (i.e. the exclusive end of the current month)
   *
   * The dashboard fallback in
   * [io.github.mojri.hesabyar.domain.usecase.GetDashboardDataUseCase] uses this
   * so transactions/installments are assigned to the same Jalali month as the
   * Rust path. A device-local range would otherwise differ by the UTC offset
   * (≈3:30 in Iran), making the fallback include an installment Rust assigns to
   * the adjacent month and producing divergent debt-to-income around boundaries.
   * Filter with `date >= start && date < nextMonthStart` — equivalent to the
   * Rust core's `[monthStartMs, monthEndMs)` range.
   */
  fun getUtcJalaliMonthBoundaries(timestamp: Long): Pair<Long, Long> {
    // Determine the Jalali month of [timestamp] in UTC, like the Rust core does.
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utcCal.timeInMillis = timestamp
    val jalaliDate =
      gregorianToJalaliLocal(
        utcCal.get(Calendar.YEAR),
        utcCal.get(Calendar.MONTH) + 1,
        utcCal.get(Calendar.DAY_OF_MONTH)
      )
    val (jy, jm) =
      if (jalaliDate != null) {
        jalaliDate.year to jalaliDate.month
      } else {
        // Best-effort 30-day window so callers still get a finite, ordered range.
        val windowStart = saturatingSubtract(timestamp, 30L * 24 * 60 * 60 * 1000)
        val windowEnd = saturatingAdd(timestamp, 30L * 24 * 60 * 60 * 1000)
        return windowStart to windowEnd
      }

    val start = jalaliMonthStartUtcMs(jy, jm)
    val nextMonth = if (jm == 12) 1 else jm + 1
    val nextYear = if (jm == 12) jy + 1 else jy
    val nextMonthStart = jalaliMonthStartUtcMs(nextYear, nextMonth)
    return start to nextMonthStart
  }

  /**
   * UTC epoch-ms boundaries for the Jalali month immediately *before* the
   * Jalali month that contains [currentMonthStartMs].
   *
   * Returns `(prevMonthStart, prevMonthEndExclusive)` where `prevMonthEndExclusive`
   * equals `currentMonthStartMs` (half-open range, matching the Rust core).
   *
   * Used by [io.github.mojri.hesabyar.domain.usecase.GetDashboardDataUseCase]
   * to compute per-account month-over-month deltas.
   */
  fun getUtcJalaliPreviousMonthBoundaries(currentMonthStartMs: Long): Pair<Long, Long> {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utcCal.timeInMillis = currentMonthStartMs
    val jalaliDate =
      gregorianToJalaliLocal(
        utcCal.get(Calendar.YEAR),
        utcCal.get(Calendar.MONTH) + 1,
        utcCal.get(Calendar.DAY_OF_MONTH)
      )
    if (jalaliDate != null) {
      val (jy, jm) = jalaliDate
      // Current month start we already have
      val curStart = jalaliMonthStartUtcMs(jy, jm)
      // Previous month: jm-1 (wrapping from 1→12 with year-1)
      val (pjy, pjm) = if (jm == 1) jy - 1 to 12 else jy to jm - 1
      val prevStart = jalaliMonthStartUtcMs(pjy, pjm)
      return prevStart to curStart
    }
    // Fallback: assume 30-day previous month
    val prevStart = saturatingSubtract(currentMonthStartMs, 30L * 24 * 60 * 60 * 1000)
    return prevStart to currentMonthStartMs
  }

  /**
   * UTC epoch-ms of midnight of the Gregorian date that corresponds to the
   * Jalali `(jy, jm, 1)`. Mirrors Rust's `jalali_to_gregorian(jy, jm, 1)`, which
   * returns the UTC midnight for the given Jalali date.
   */
  private fun jalaliMonthStartUtcMs(
    jy: Int,
    jm: Int
  ): Long {
    val gCal = jalaliToGregorianLocal(jy, jm, 1) ?: return 0L
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utcCal.set(
      gCal.get(Calendar.YEAR),
      gCal.get(Calendar.MONTH),
      gCal.get(Calendar.DAY_OF_MONTH),
      0,
      0,
      0
    )
    utcCal.set(Calendar.MILLISECOND, 0)
    return utcCal.timeInMillis
  }

  /**
   * Pure-Kotlin Jalali→Gregorian conversion, mirroring the Rust core. Used when
   * the native bridge is unavailable. Returns null for out-of-range input.
   *
   * Implemented as the exact inverse of [gregorianToJalaliLocal] so the two
   * stay perfectly consistent (same day-number arithmetic in both directions).
   */
  fun jalaliToGregorianLocal(
    jYear: Int,
    jMonth: Int,
    jDay: Int
  ): Calendar? {
    if (jMonth < 1 || jMonth > 12) return null
    if (jDay < 1 || jDay > jalaliDaysInMonthLocal(jYear, jMonth)) return null

    val gDayNo = jalaliToDayNo(jYear, jMonth, jDay) + 79
    val (gy, gm, gd) = gregorianFromDayNo(gDayNo)
    val cal = Calendar.getInstance()
    cal.set(gy, gm - 1, gd, 0, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal
  }

  private fun jalaliDaysInMonthLocal(
    year: Int,
    month: Int
  ): Int =
    when (month) {
      in 1..6 -> 31
      in 7..11 -> 30
      12 -> if (isJalaliLeapYear(year)) 30 else 29
      else -> 0
    }

  private val GREGORIAN_MONTH_OFFSETS = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365)

  private fun isGregLeap(year: Int): Boolean = year % 4 == 0 && year % 100 != 0 || year % 400 == 0

  /** Gregorian (year, month, day) → days since 1600-01-01, mirroring [gregorianToJalaliLocal]. */
  private fun gregorianToDayNo(
    gYear: Int,
    gMonth: Int,
    gDay: Int
  ): Long {
    val leap = isGregLeap(gYear)
    val gy = gYear - 1600
    val gm = gMonth - 1
    val gd = gDay - 1
    var dayNo = 365L * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
    dayNo += GREGORIAN_MONTH_OFFSETS[gm]
    if (gm > 1 && leap) dayNo += 1
    dayNo += gd
    return dayNo
  }

  /** Inverse of [gregorianToDayNo]: days since 1600-01-01 → Gregorian (year, month, day). */
  private fun gregorianFromDayNo(dayNo: Long): Triple<Int, Int, Int> {
    var rem = dayNo
    var year = 1600
    while (true) {
      val days = if (isGregLeap(year)) 366L else 365L
      if (rem < days) break
      rem -= days
      year++
    }
    val offsets =
      if (isGregLeap(year)) {
        intArrayOf(0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335, 366)
      } else {
        GREGORIAN_MONTH_OFFSETS
      }
    var m = 0
    while (m < 11 && rem >= offsets[m + 1]) m++
    val day = (rem - offsets[m] + 1).toInt()
    return Triple(year, m + 1, day)
  }

  /**
   * Inverse of the Jalali decomposition inside [gregorianToJalaliLocal]:
   * Jalali (year, month, day) → Jalali day number (days since the same epoch
   * used by that function).
   */
  private fun jalaliToDayNo(
    jy: Int,
    jm: Int,
    jd: Int
  ): Long {
    val j3 = jd - 1 + (0 until jm - 1).sumOf { JALALI_MONTH_DAY_COUNTS[it] }
    val base = jy - 979
    // jy - 979 = 33*jNp + 4*q1 + jyExtra, with q1 in [0,8] and jyExtra in [0,3].
    for (jNp in base / 33 - 1..base / 33 + 1) {
      val rem = base - 33 * jNp
      if (rem < 0 || rem > 32) continue
      for (jyExtra in 0..3) {
        if ((rem - jyExtra) % 4 != 0) continue
        val q1 = (rem - jyExtra) / 4
        if (q1 < 0 || q1 > 8) continue
        val j2 = if (jyExtra == 0) j3 else 365 * jyExtra + j3 + 1
        val j1 = q1 * 1461L + j2 % 1461
        return jNp * 12053L + j1
      }
    }
    return 0L
  }
}
