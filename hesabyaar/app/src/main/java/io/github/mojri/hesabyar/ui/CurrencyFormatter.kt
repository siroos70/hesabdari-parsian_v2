package io.github.mojri.hesabyar.ui

enum class CurrencyUnit(
  val key: String,
  val label: String
) {
  RIAL("rial", "ریال"),
  TOMAN("toman", "تومان");

  companion object {
    fun fromKey(key: String): CurrencyUnit =
      entries.firstOrNull { it.key == key }
        ?: entries.firstOrNull { it.label == key }
        ?: TOMAN
  }
}

/**
 * Centralized currency formatting.
 * DB stores amounts in Rial. This converts to the user-selected display unit.
 * Holds current unit as mutable state — SettingsViewModel updates it.
 */
object CurrencyFormatter {
  var currentUnit = CurrencyUnit.TOMAN
    private set

  val unitLabel: String get() = currentUnit.label

  fun setUnit(unit: CurrencyUnit) {
    currentUnit = unit
  }

  private fun toRustUnit(): io.github.mojri.hesabyar.rust.CurrencyUnit =
    if (currentUnit == CurrencyUnit.RIAL) {
      io.github.mojri.hesabyar.rust.CurrencyUnit.RIAL
    } else {
      io.github.mojri.hesabyar.rust.CurrencyUnit.TOMAN
    }

  /**
   * Convert Western digits to Persian and comma to Persian thousands separator.
   * Rust core returns Western-formatted numbers; Hesabyar is Persian-first.
   */
  private fun toPersianDigits(western: String): String {
    val sb = StringBuilder(western.length)
    for (c in western) {
      when {
        c in '0'..'9' -> sb.append((c.code - '0'.code + 0x06F0).toChar())
        c == ',' -> sb.append('\u066C') // Arabic thousands separator ٬
        else -> sb.append(c)
      }
    }
    return sb.toString()
  }

  /** Format number only (no unit) — for components that show their own label. */
  fun formatNumber(value: Long): String =
    formatWithSign(value, formatAbs = {
      io.github.mojri.hesabyar.rust.RustBridge
        .formatNumberSync(it)
    })

  fun format(rial: Long): String =
    formatWithSign(rial, formatAbs = {
      io.github.mojri.hesabyar.rust.RustBridge
        .formatCurrencySync(it, toRustUnit())
    }) {
      currencyFallback(it)
    }

  private fun formatWithSign(
    value: Long,
    formatAbs: (Long) -> String,
    fallback: (Long) -> String = { kotlinFallback(it) }
  ): String {
    val isNegative = value < 0
    // kotlin.math.abs(Long.MIN_VALUE) overflows and returns Long.MIN_VALUE,
    // so we guard against that edge case explicitly.
    val absValue =
      if (value == Long.MIN_VALUE) {
        Long.MAX_VALUE
      } else if (isNegative) {
        -value
      } else {
        value
      }
    val rustResult = formatAbs(absValue)
    val formatted =
      if (rustResult.isNotEmpty()) {
        toPersianDigits(rustResult)
      } else {
        fallback(absValue)
      }
    // LRM (U+200E) forces LTR rendering of the number block so the minus
    // sign stays on the left in RTL layout (Persian/Arabic).
    return if (isNegative) "\u200E-$formatted" else "\u200E$formatted"
  }

  /**
   * Format a signed amount with a directional prefix (+ or −) for display.
   *
   * Use this instead of manually prepending "+" or "-" to [format] output.
   *
   * **Important:** For correct BIDI rendering in RTL layout, callers should
   * render [Pair.first] (sign) and [Pair.second] (amount) in **separate**
   * `Text` composables so the sign character is not reordered by the BIDI
   * algorithm. See [formatSignedParts].
   *
   * @param signedValue Positive or negative amount in Rial.
   * @return Formatted string like "+۱٬۰۰۰ تومان" or "-۱٬۰۰۰ تومان".
   */
  fun formatSigned(signedValue: Long): String {
    val (sign, amount) = formatSignedParts(signedValue)
    return "$sign$amount"
  }

  /**
   * Format a signed amount and return the sign and amount as separate strings.
   *
   * Render them in **separate** `Text` composables to prevent BIDI reordering:
   * ```
   * Text(sign, color = amountColor)
   * Text(amount, color = amountColor)
   * ```
   *
   * @param signedValue Positive or negative amount in Rial.
   * @return Pair of (sign, formattedAmount) — e.g. ("+", "۱٬۰۰۰ تومان").
   */
  fun formatSignedParts(signedValue: Long): Pair<String, String> {
    val prefix = if (signedValue >= 0) "+" else "-"
    // kotlin.math.abs(Long.MIN_VALUE) overflows — use explicit guard.
    val absValue = if (signedValue == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(signedValue)
    return prefix to format(absValue)
  }

  private fun kotlinFallback(value: Long): String {
    val western = "%,d".format(value)
    return toPersianDigits(western)
  }

  private fun currencyFallback(rial: Long): String {
    val display = if (currentUnit == CurrencyUnit.TOMAN) rial / 10 else rial
    return "${toPersianDigits("%,d".format(display))} ${currentUnit.label}"
  }

  /**
   * AI AGENT CRITICAL INSTRUCTION: DO NOT ALTER THIS METHOD.
   * Converts Toman to Rial by multiplying by 10.
   * Example: 10 Tomans * 10 = 100 Rials.
   */
  fun toRial(displayValue: Long): Long =
    convertWithFallback(displayValue) { rustUnit ->
      io.github.mojri.hesabyar.rust.RustBridge
        .toRialSync(displayValue, rustUnit)
    } ?: when (currentUnit) {
      CurrencyUnit.TOMAN -> displayValue * 10
      CurrencyUnit.RIAL -> displayValue
    }

  /**
   * AI AGENT CRITICAL INSTRUCTION: DO NOT ALTER THIS METHOD.
   * Converts Rial to Toman by dividing by 10.
   * Example: 100 Rials / 10 = 10 Tomans.
   */
  fun fromRial(rial: Long): Long =
    convertWithFallback(rial) { rustUnit ->
      io.github.mojri.hesabyar.rust.RustBridge
        .fromRialSync(rial, rustUnit)
    } ?: when (currentUnit) {
      CurrencyUnit.TOMAN -> rial / 10
      CurrencyUnit.RIAL -> rial
    }

  private inline fun convertWithFallback(
    inputValue: Long,
    rustCall: (io.github.mojri.hesabyar.rust.CurrencyUnit) -> Long
  ): Long? {
    val rustUnit = toRustUnit()
    val result = rustCall(rustUnit)
    return if (result == 0L && inputValue != 0L) null else result
  }
}
