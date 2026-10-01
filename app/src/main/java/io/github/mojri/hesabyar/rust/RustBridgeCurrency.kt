package io.github.mojri.hesabyar.rust

// Currency domain of the RustBridge façade. See RustBridgeCore for the split.

/** Rial / display-unit conversions and Persian number formatting. */
internal interface RustBridgeCurrency : RustBridgeCore {
  /** Formats a Rial amount in the requested display unit. Empty string on failure. */
  fun formatCurrencySync(
    rial: Long,
    unit: CurrencyUnit
  ): String = rustCallSync("") { HesabyarCore.formatCurrency(rial, unit) }

  /** Converts a display-unit value to Rial. Zero on failure. */
  fun toRialSync(
    displayValue: Long,
    unit: CurrencyUnit
  ): Long = rustCallSync(0L) { HesabyarCore.toRial(displayValue, unit) }

  /** Converts a Rial amount to the requested display unit. Zero on failure. */
  fun fromRialSync(
    rial: Long,
    unit: CurrencyUnit
  ): Long = rustCallSync(0L) { HesabyarCore.fromRial(rial, unit) }

  /** Formats a number with Western digits and grouping. Empty string on failure. */
  fun formatNumberSync(value: Long): String = rustCallSync("") { HesabyarCore.formatNumber(value) }
}
