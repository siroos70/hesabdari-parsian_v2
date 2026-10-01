use crate::models::CurrencyUnit;

/// Format a Rial amount as a string with thousand separators.
/// The output uses Persian locale formatting.
#[uniffi::export]
pub fn format_number(value: i64) -> String {
    let negative = value < 0;
    // Use the unsigned magnitude so that i64::MIN (whose negation would
    // overflow and panic in debug builds) formats correctly, and so the
    // separator logic below never counts the sign character.
    let magnitude = value.unsigned_abs();
    let s = magnitude.to_string();
    let mut result = String::new();
    let len = s.len();
    for (i, c) in s.chars().enumerate() {
        if i > 0 && (len - i).is_multiple_of(3) {
            result.push(',');
        }
        result.push(c);
    }
    if negative {
        result.insert(0, '-');
    }
    result
}

/// Format a Rial amount using the given currency unit.
#[uniffi::export]
pub fn format_currency(rial: i64, unit: CurrencyUnit) -> String {
    match unit {
        CurrencyUnit::Rial => format!("{} \u{0631}\u{06CC}\u{0627}\u{0644}", format_number(rial)),
        CurrencyUnit::Toman => format!(
            "{} \u{062A}\u{0648}\u{0645}\u{0627}\u{0646}",
            format_number(rial / 10)
        ),
    }
}

/// Convert a display value (in the given unit) to Rial.
///
/// **INVARIANT: 1 Toman == EXACTLY 10 Rials.**
/// - Toman → Rial: multiply by 10
/// - Rial → Rial: identity
///
/// This factor is **hardcoded** and must NEVER be changed.
/// Colloquial usage of "Toman" meaning "1000 Tomans" is irrelevant here;
/// this function operates on the display unit, not conversational shorthand.
#[uniffi::export]
pub fn to_rial(display_value: i64, unit: CurrencyUnit) -> i64 {
    match unit {
        CurrencyUnit::Rial => display_value,
        CurrencyUnit::Toman => display_value * 10,
    }
}

/// Convert a Rial value to the given display unit.
///
/// **INVARIANT: 1 Toman == EXACTLY 10 Rials.**
/// - Rial → Toman: integer divide by 10
/// - Rial → Rial: identity
///
/// This factor is **hardcoded** and must NEVER be changed.
#[uniffi::export]
pub fn from_rial(rial: i64, unit: CurrencyUnit) -> i64 {
    match unit {
        CurrencyUnit::Rial => rial,
        CurrencyUnit::Toman => rial / 10,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // =====================================================================
    // format_number
    // =====================================================================

    #[test]
    fn test_format_number_zero() {
        assert_eq!(format_number(0), "0");
    }

    #[test]
    fn test_format_number_small() {
        assert_eq!(format_number(100), "100");
    }

    #[test]
    fn test_format_number_thousands() {
        assert_eq!(format_number(1000), "1,000");
    }

    #[test]
    fn test_format_number_millions() {
        assert_eq!(format_number(1234567), "1,234,567");
    }

    #[test]
    fn test_format_number_billions() {
        assert_eq!(format_number(1234567890), "1,234,567,890");
    }

    #[test]
    fn test_format_number_negative() {
        assert_eq!(format_number(-100), "-100");
        assert_eq!(format_number(-1000), "-1,000");
        assert_eq!(format_number(-1234567), "-1,234,567");
    }

    // =====================================================================
    // to_rial: Toman → Rial (multiply by 10)
    // =====================================================================

    #[test]
    fn test_to_rial_from_rial_identity() {
        assert_eq!(to_rial(1000, CurrencyUnit::Rial), 1000);
    }

    #[test]
    fn test_to_rial_from_toman_multiplies_by_10() {
        assert_eq!(to_rial(1000, CurrencyUnit::Toman), 10000);
    }

    #[test]
    fn test_to_rial_toman_zero() {
        assert_eq!(to_rial(0, CurrencyUnit::Toman), 0);
    }

    #[test]
    fn test_to_rial_toman_one() {
        assert_eq!(to_rial(1, CurrencyUnit::Toman), 10);
    }

    #[test]
    fn test_to_rial_toman_500000() {
        // 500,000 Toman == 5,000,000 Rial
        assert_eq!(to_rial(500_000, CurrencyUnit::Toman), 5_000_000);
    }

    #[test]
    fn test_to_rial_toman_10000() {
        // 10,000 Toman == 100,000 Rial
        assert_eq!(to_rial(10_000, CurrencyUnit::Toman), 100_000);
    }

    // =====================================================================
    // from_rial: Rial → Toman (divide by 10)
    // =====================================================================

    #[test]
    fn test_from_rial_to_rial_identity() {
        assert_eq!(from_rial(1000, CurrencyUnit::Rial), 1000);
    }

    #[test]
    fn test_from_rial_to_toman_divides_by_10() {
        assert_eq!(from_rial(1000, CurrencyUnit::Toman), 100);
    }

    #[test]
    fn test_from_rial_toman_zero() {
        assert_eq!(from_rial(0, CurrencyUnit::Toman), 0);
    }

    #[test]
    fn test_from_rial_small_amount() {
        // 9 Rial / 10 = 0 Toman (integer division)
        assert_eq!(from_rial(9, CurrencyUnit::Toman), 0);
    }

    #[test]
    fn test_from_rial_exactly_10() {
        // 10 Rial / 10 = 1 Toman
        assert_eq!(from_rial(10, CurrencyUnit::Toman), 1);
    }

    #[test]
    fn test_from_rial_500000() {
        // 500,000 Rial / 10 = 50,000 Toman
        assert_eq!(from_rial(500_000, CurrencyUnit::Toman), 50_000);
    }

    #[test]
    fn test_from_rial_5000000() {
        // 5,000,000 Rial / 10 = 500,000 Toman
        assert_eq!(from_rial(5_000_000, CurrencyUnit::Toman), 500_000);
    }

    // =====================================================================
    // Round-trip invariants
    // =====================================================================

    #[test]
    fn test_round_trip_toman_to_rial_and_back() {
        let original = 123_456_i64;
        let rial = to_rial(original, CurrencyUnit::Toman);
        let back = from_rial(rial, CurrencyUnit::Toman);
        assert_eq!(back, original);
    }

    #[test]
    fn test_round_trip_rial_to_toman_and_back() {
        let original = 1_234_560_i64;
        let toman = from_rial(original, CurrencyUnit::Toman);
        let back = to_rial(toman, CurrencyUnit::Toman);
        assert_eq!(back, original);
    }

    #[test]
    fn test_format_currency_rial() {
        let result = format_currency(1_234_560, CurrencyUnit::Rial);
        assert!(result.contains("1,234,560"));
        assert!(result.contains("\u{0631}\u{06CC}\u{0627}\u{0644}")); // ریال
    }

    #[test]
    fn test_format_currency_toman() {
        let result = format_currency(1_234_560, CurrencyUnit::Toman);
        assert!(result.contains("123,456"));
        assert!(result.contains("\u{062A}\u{0648}\u{0645}\u{0627}\u{0646}")); // تومان
    }

    // =====================================================================
    // Hardcoded invariant tests — CRITICAL: 1 Toman == EXACTLY 10 Rials
    // =====================================================================

    #[test]
    fn test_invariant_toman_to_rial_10000() {
        // The exact invariant the user specified
        assert_eq!(to_rial(10_000, CurrencyUnit::Toman), 100_000);
    }

    #[test]
    fn test_invariant_from_rial_500000() {
        // The exact invariant the user specified
        assert_eq!(from_rial(500_000, CurrencyUnit::Toman), 50_000);
    }

    #[test]
    fn test_invariant_toman_1_is_rial_10() {
        assert_eq!(to_rial(1, CurrencyUnit::Toman), 10);
        assert_eq!(from_rial(10, CurrencyUnit::Toman), 1);
    }

    #[test]
    fn test_invariant_rial_identity() {
        assert_eq!(to_rial(42, CurrencyUnit::Rial), 42);
        assert_eq!(from_rial(42, CurrencyUnit::Rial), 42);
    }

    #[test]
    fn test_invariant_large_amount() {
        // 1 billion Toman == 10 billion Rial
        assert_eq!(to_rial(1_000_000_000, CurrencyUnit::Toman), 10_000_000_000);
        assert_eq!(
            from_rial(10_000_000_000, CurrencyUnit::Toman),
            1_000_000_000
        );
    }
}
