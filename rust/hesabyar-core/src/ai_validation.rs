use serde::Deserialize;

use crate::models::*;

// ===========================================================================
// Types
// ===========================================================================

/// Result of parsing and validating an AI-generated JSON transaction.
///
/// Wraps `ParsedResult` with repair metadata so the Kotlin layer can log
/// which fields were coerced to safe defaults.
#[derive(Debug, Clone, uniffi::Record)]
pub struct AiParsedTransaction {
    pub result: ParsedResult,
    pub was_repaired: bool,
    pub repair_notes: Vec<String>,
}

/// Validation result for free-form AI text (advice / forecast).
#[derive(Debug, Clone, Default, uniffi::Record)]
pub struct AdviceValidation {
    pub is_valid: bool,
    pub sanitized_text: String,
    pub warnings: Vec<String>,
    pub was_truncated: bool,
}

// ===========================================================================
// Internal: Serde-friendly mirror of the AI JSON schema
// ===========================================================================

#[derive(Debug, Deserialize)]
struct AiTransactionJson {
    #[serde(rename = "type", default)]
    tx_type: Option<String>,
    #[serde(default)]
    amount: Option<f64>,
    #[serde(default)]
    category: Option<String>,
    #[serde(rename = "personName", default)]
    person_name: Option<String>,
    #[serde(default)]
    description: Option<String>,
    #[serde(rename = "daysFromNow", default)]
    days_from_now: Option<i32>,
    #[serde(default)]
    title: Option<String>,
    #[serde(rename = "dateOffsetDays", default)]
    date_offset_days: Option<i32>,
    #[serde(default)]
    hour: Option<i32>,
    #[serde(default)]
    minute: Option<i32>,
    #[serde(default)]
    confidence: Option<f64>,
    #[serde(default)]
    notes: Option<String>,
}

// ===========================================================================
// Category normalization
// ===========================================================================

/// Maps extended/fine-grained categories to the 8 core DB categories.
const CATEGORY_MAP: &[(&str, &str)] = &[
    // Extended → core
    ("Personal Care", "Shopping"),
    ("Education", "Other"),
    ("Rent & Utilities", "Bills"),
    ("Loans & Debt", "Loans"),
    ("Events & Gifts", "Other"),
    ("Charity", "Other"),
    ("Investment", "Other"),
    // Core → core (pass-through, listed for completeness)
    ("Food", "Food"),
    ("Transportation", "Transportation"),
    ("Shopping", "Shopping"),
    ("Bills", "Bills"),
    ("Installments", "Installments"),
    ("Loans", "Loans"),
    ("Income", "Income"),
    ("Other", "Other"),
];

/// Normalize a category string to one of the 8 core DB categories.
///
/// Unknown or empty categories are mapped to "Other".
fn normalize_category(cat: &str) -> String {
    let trimmed = cat.trim();
    if trimmed.is_empty() {
        return "Other".to_string();
    }
    for &(from, to) in CATEGORY_MAP {
        if trimmed.eq_ignore_ascii_case(from) {
            return to.to_string();
        }
    }
    "Other".to_string()
}

// ===========================================================================
// Type mapping
// ===========================================================================

/// Map a type string to `TransactionType`. Unknown types → Expense.
fn map_tx_type(s: &str) -> TransactionType {
    match s.trim().to_uppercase().as_str() {
        "INCOME" => TransactionType::Income,
        "LOAN_DEBTOR" => TransactionType::LoanDebtor,
        "LOAN_CREDITOR" => TransactionType::LoanCreditor,
        "INSTALLMENT" => TransactionType::Installment,
        "TRANSFER" => TransactionType::Transfer,
        _ => TransactionType::Expense,
    }
}

// ===========================================================================
// Clamping helpers
// ===========================================================================

/// Clamp an optional i32 to a valid range, returning None if out of range.
fn clamp_optional_i32(
    val: Option<i32>,
    min: i32,
    max: i32,
    name: &str,
) -> (Option<i32>, Option<String>) {
    match val {
        Some(v) if v < min || v > max => {
            (None, Some(format!("{} clamped from {} to None", name, v)))
        }
        Some(v) => (Some(v), None),
        None => (None, None),
    }
}

/// Clamp a confidence value to 0.0..1.0, defaulting to 0.8 if missing.
fn clamp_confidence(val: Option<f64>) -> (f32, Option<String>) {
    match val {
        Some(v) if v < 0.0 => (0.0, Some(format!("confidence clamped from {} to 0.0", v))),
        Some(v) if v > 1.0 => (1.0, Some(format!("confidence clamped from {} to 1.0", v))),
        Some(v) => (v as f32, None),
        None => (0.8, Some("confidence defaulted to 0.8".to_string())),
    }
}

/// Clamp days_from_now / date_offset_days to ±365.
fn clamp_days(val: Option<i32>, name: &str) -> (Option<i32>, Option<String>) {
    clamp_optional_i32(val, -365, 365, name)
}

/// Normalize an optional text field: trim it and map empty text to `None`.
fn normalize_optional(s: String) -> Option<String> {
    let trimmed = s.trim().to_string();
    if trimmed.is_empty() {
        None
    } else {
        Some(trimmed)
    }
}

// ===========================================================================
// Public API: Transaction JSON validation
// ===========================================================================

/// Parse and validate a raw AI JSON response into a `ParsedResult`.
///
/// This is the single entry point for all AI transaction JSON validation.
/// It:
/// 1. Parses JSON via serde
/// 2. Validates and coerces each field
/// 3. Normalizes category to 8 core DB values
/// 4. Clamps out-of-range values
/// 5. Returns structured result with repair metadata
///
/// # Errors
/// Returns `HesabyarError::ParseError` if the JSON is malformed.
/// Returns `HesabyarError::ValidationError` if the amount is ≤ 0 after extraction.
pub fn parse_ai_transaction_json(json: &str) -> Result<AiParsedTransaction, HesabyarError> {
    let raw: AiTransactionJson =
        serde_json::from_str(json).map_err(|e| HesabyarError::ParseError {
            detail: format!("Invalid AI JSON: {}", e),
        })?;

    let mut notes = Vec::new();
    let mut was_repaired = false;

    // --- Type ---
    let type_str = raw.tx_type.unwrap_or_else(|| "EXPENSE".to_string());
    let was_type_repaired = !matches!(
        type_str.trim().to_uppercase().as_str(),
        "EXPENSE" | "INCOME" | "LOAN_DEBTOR" | "LOAN_CREDITOR" | "INSTALLMENT" | "TRANSFER"
    );
    if was_type_repaired {
        notes.push(format!("type '{}' → EXPENSE (unknown type)", type_str));
        was_repaired = true;
    }
    let tx_type = map_tx_type(&type_str);

    // TRANSFER requires accountId/destinationAccountId which the AI parser
    // cannot provide. Mark as needing user approval so the Kotlin layer
    // can prompt for account selection before persisting.
    if tx_type == TransactionType::Transfer {
        notes.push("TRANSFER requires account selection before saving".to_string());
        was_repaired = true;
    }

    // --- Amount ---
    let amount = raw.amount.ok_or_else(|| HesabyarError::ValidationError {
        detail: "AI JSON missing 'amount' field".to_string(),
    })?;
    if !amount.is_finite() || amount > i64::MAX as f64 || amount < i64::MIN as f64 {
        return Err(HesabyarError::ValidationError {
            detail: format!("AI amount out of range: {}", amount),
        });
    }
    let amount_i64 = amount as i64;
    if amount_i64 <= 0 {
        return Err(HesabyarError::ValidationError {
            detail: format!("AI amount must be positive, got {}", amount_i64),
        });
    }
    // Check if f64→i64 lost precision (e.g. 500.7 → 500)
    if (amount - amount_i64 as f64).abs() > f64::EPSILON {
        notes.push(format!(
            "amount truncated from {} to {}",
            amount, amount_i64
        ));
        was_repaired = true;
    }

    // --- Category ---
    let raw_category = raw.category.unwrap_or_default();
    let normalized = normalize_category(&raw_category);
    if normalized != raw_category && !raw_category.is_empty() {
        notes.push(format!("category '{}' → '{}'", raw_category, normalized));
        was_repaired = true;
    } else if raw_category.is_empty() {
        notes.push("category defaulted to 'Other'".to_string());
        was_repaired = true;
    }

    // --- Person name ---
    let person_name = raw.person_name.and_then(normalize_optional);

    // --- Description ---
    let description = raw.description.unwrap_or_default().trim().to_string();

    // --- daysFromNow ---
    let (days_from_now, days_note) = clamp_days(raw.days_from_now, "daysFromNow");
    if let Some(note) = days_note {
        notes.push(note);
        was_repaired = true;
    }

    // --- Title ---
    let title = raw.title.and_then(normalize_optional);

    // --- dateOffsetDays ---
    let (date_offset_days, offset_note) = clamp_days(raw.date_offset_days, "dateOffsetDays");
    if let Some(note) = offset_note {
        notes.push(note);
        was_repaired = true;
    }

    // --- Hour ---
    let (hour, hour_note) = clamp_optional_i32(raw.hour, 0, 23, "hour");
    if let Some(note) = hour_note {
        notes.push(note);
        was_repaired = true;
    }

    // --- Minute ---
    let (minute, minute_note) = clamp_optional_i32(raw.minute, 0, 59, "minute");
    if let Some(note) = minute_note {
        notes.push(note);
        was_repaired = true;
    }

    // --- Confidence ---
    let (confidence, conf_note) = clamp_confidence(raw.confidence);
    if let Some(note) = conf_note {
        notes.push(note);
        was_repaired = true;
    }

    // --- Notes ---
    let result_notes = raw.notes.and_then(normalize_optional);

    let amount_toman =
        amount_i64
            .checked_mul(10)
            .ok_or_else(|| HesabyarError::ValidationError {
                detail: "Amount overflows i64 range after multiplication".to_string(),
            })?;

    let result = ParsedResult {
        tx_type,
        amount: amount_toman,
        category: normalized,
        person_name,
        description,
        days_from_now,
        title,
        date_offset_days,
        hour,
        minute,
        confidence,
        notes: result_notes,
    };

    // Final consistency check via existing validation
    crate::validation::validate_parsed_result(&result)
        .map_err(|e| HesabyarError::ValidationError { detail: e })?;

    Ok(AiParsedTransaction {
        result,
        was_repaired,
        repair_notes: notes,
    })
}

// ===========================================================================
// Public API: Advice text validation
// ===========================================================================

const MAX_ADVICE_LENGTH: usize = 10_000;
const MIN_ADVICE_LENGTH: usize = 10;

/// Validate and sanitize free-form AI advice/forecast text.
///
/// Checks:
/// - Minimum length (rejects empty / too-short text)
/// - Maximum length (truncates with warning)
/// - Markdown safety (strips dangerous tags)
/// - Persian content detection (warning only, not rejection)
pub fn validate_ai_advice(text: &str) -> AdviceValidation {
    let mut warnings = Vec::new();
    let mut was_truncated = false;

    let trimmed = text.trim();

    // Empty / too short
    if trimmed.len() < MIN_ADVICE_LENGTH {
        return AdviceValidation {
            is_valid: false,
            sanitized_text: String::new(),
            warnings: vec![format!(
                "Text too short ({} chars, minimum {})",
                trimmed.len(),
                MIN_ADVICE_LENGTH
            )],
            was_truncated: false,
        };
    }

    let mut result = trimmed.to_string();

    // Length clamp — find a safe char boundary before truncating
    if result.len() > MAX_ADVICE_LENGTH {
        let mut truncate_at = MAX_ADVICE_LENGTH;
        while truncate_at > 0 && !result.is_char_boundary(truncate_at) {
            truncate_at -= 1;
        }
        result.truncate(truncate_at);
        was_truncated = true;
        warnings.push(format!(
            "Text truncated from {} to {} chars",
            trimmed.len(),
            result.chars().count()
        ));
    }

    // Markdown safety: strip <script> and javascript: tags
    let dangerous_patterns = ["<script", "</script>", "javascript:"];
    for pattern in &dangerous_patterns {
        let lower_result = result.to_lowercase();
        if lower_result.contains(pattern) {
            // Case-insensitive removal — iterate chars from the original
            // string and compare case-insensitively to keep positions aligned.
            let pattern_chars: Vec<char> = pattern.chars().collect();
            let result_chars: Vec<char> = result.chars().collect();
            let mut new_result = String::new();
            let mut i = 0;
            while i < result_chars.len() {
                if i + pattern_chars.len() <= result_chars.len()
                    && result_chars[i..i + pattern_chars.len()]
                        .iter()
                        .zip(pattern_chars.iter())
                        .all(|(a, b)| a.to_lowercase().eq(b.to_lowercase()))
                {
                    i += pattern_chars.len();
                } else {
                    new_result.push(result_chars[i]);
                    i += 1;
                }
            }
            result = new_result;
            // Re-truncate if replacement made it longer (shouldn't happen)
            if result.len() > MAX_ADVICE_LENGTH {
                let mut truncate_at = MAX_ADVICE_LENGTH;
                while truncate_at > 0 && !result.is_char_boundary(truncate_at) {
                    truncate_at -= 1;
                }
                result.truncate(truncate_at);
            }
            warnings.push(format!("Removed dangerous pattern: {}", pattern));
        }
    }

    // Persian content detection
    let has_persian = result
        .chars()
        .any(|c| ('\u{0600}'..='\u{06FF}').contains(&c));
    if !has_persian {
        warnings.push("No Persian characters detected".to_string());
    }

    AdviceValidation {
        is_valid: true,
        sanitized_text: result,
        warnings,
        was_truncated,
    }
}

// ===========================================================================
// Tests
// ===========================================================================

#[cfg(test)]
mod tests {
    use super::*;

    // =====================================================================
    // Helper
    // =====================================================================

    fn minimal_json(amount: i64) -> String {
        format!(
            r#"{{"amount": {}, "type": "EXPENSE", "category": "Food"}}"#,
            amount
        )
    }

    fn full_json() -> &'static str {
        r#"{
            "type": "INCOME",
            "amount": 20000000,
            "category": "Income",
            "personName": "علی",
            "description": "دریافت حقوق",
            "daysFromNow": 0,
            "title": null,
            "dateOffsetDays": 0,
            "hour": 14,
            "minute": 30,
            "confidence": 0.95,
            "notes": "salary"
        }"#
    }

    // =====================================================================
    // JSON parsing tests
    // =====================================================================

    #[test]
    fn test_valid_full_json() {
        let result = parse_ai_transaction_json(full_json()).unwrap();
        assert_eq!(result.result.tx_type, TransactionType::Income);
        assert_eq!(result.result.amount, 200_000_000); // 20M toman * 10 = 200M rial
        assert_eq!(result.result.category, "Income");
        assert_eq!(result.result.person_name, Some("علی".to_string()));
        assert_eq!(result.result.description, "دریافت حقوق");
        assert_eq!(result.result.hour, Some(14));
        assert_eq!(result.result.minute, Some(30));
        assert!((result.result.confidence - 0.95).abs() < 0.001);
        assert!(!result.was_repaired);
        assert!(result.repair_notes.is_empty());
    }

    #[test]
    fn test_valid_minimal_json() {
        let result = parse_ai_transaction_json(&minimal_json(50000)).unwrap();
        assert_eq!(result.result.tx_type, TransactionType::Expense);
        assert_eq!(result.result.amount, 500000); // 50000 toman * 10 = 500000 rial
        assert_eq!(result.result.category, "Food");
        // Defaults applied
        assert_eq!(result.result.confidence, 0.8);
    }

    #[test]
    fn test_invalid_json_rejected() {
        let err = parse_ai_transaction_json("{not valid json}").unwrap_err();
        match err {
            HesabyarError::ParseError { detail } => {
                assert!(detail.contains("Invalid AI JSON"));
            }
            _ => panic!("Expected ParseError"),
        }
    }

    #[test]
    fn test_empty_string_rejected() {
        assert!(parse_ai_transaction_json("").is_err());
    }

    #[test]
    fn test_empty_object_rejected() {
        // Missing amount
        assert!(parse_ai_transaction_json("{}").is_err());
    }

    #[test]
    fn test_amount_as_string_handled() {
        // serde will fail to parse "500" string as f64
        let json = r#"{"type": "EXPENSE", "amount": "500", "category": "Food"}"#;
        assert!(parse_ai_transaction_json(json).is_err());
    }

    // =====================================================================
    // Type coercion tests
    // =====================================================================

    #[test]
    fn test_all_valid_types() {
        for (type_str, expected) in [
            ("EXPENSE", TransactionType::Expense),
            ("INCOME", TransactionType::Income),
            ("LOAN_DEBTOR", TransactionType::LoanDebtor),
            ("LOAN_CREDITOR", TransactionType::LoanCreditor),
            ("INSTALLMENT", TransactionType::Installment),
            ("TRANSFER", TransactionType::Transfer),
        ] {
            let json = format!(
                r#"{{"type": "{}", "amount": 1000, "category": "Food"}}"#,
                type_str
            );
            let result = parse_ai_transaction_json(&json).unwrap();
            assert_eq!(result.result.tx_type, expected);
        }
    }

    #[test]
    fn test_unknown_type_repaired_to_expense() {
        let json = r#"{"type": "UNKNOWN_TYPE", "amount": 1000, "category": "Food"}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.tx_type, TransactionType::Expense);
        assert!(result.was_repaired);
        assert!(result
            .repair_notes
            .iter()
            .any(|n| n.contains("UNKNOWN_TYPE")));
    }

    #[test]
    fn test_missing_type_defaults_to_expense() {
        let json = r#"{"amount": 1000, "category": "Food"}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.tx_type, TransactionType::Expense);
    }

    #[test]
    fn test_transfer_type_recognized() {
        let json =
            r#"{"type": "TRANSFER", "amount": 1000, "category": "Other", "confidence": 0.8}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.tx_type, TransactionType::Transfer);
        // TRANSFER is always marked as repaired because the AI parser
        // cannot set accountId/destinationAccountId — the user must
        // select accounts before saving.
        assert!(result.was_repaired);
        assert!(result.repair_notes.iter().any(|n| n.contains("TRANSFER")));
    }

    #[test]
    fn test_type_case_insensitive() {
        let json = r#"{"type": "income", "amount": 5000, "category": "Income"}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.tx_type, TransactionType::Income);
    }

    // =====================================================================
    // Amount validation tests
    // =====================================================================

    #[test]
    fn test_positive_amount_passes() {
        let result = parse_ai_transaction_json(&minimal_json(50000)).unwrap();
        assert_eq!(result.result.amount, 500000); // 50000 toman * 10 = 500000 rial
    }

    #[test]
    fn test_zero_amount_rejected() {
        let err = parse_ai_transaction_json(&minimal_json(0)).unwrap_err();
        match err {
            HesabyarError::ValidationError { detail } => {
                assert!(detail.contains("positive"));
            }
            _ => panic!("Expected ValidationError"),
        }
    }

    #[test]
    fn test_negative_amount_rejected() {
        let err = parse_ai_transaction_json(&minimal_json(-500)).unwrap_err();
        match err {
            HesabyarError::ValidationError { detail } => {
                assert!(detail.contains("positive"));
            }
            _ => panic!("Expected ValidationError"),
        }
    }

    #[test]
    fn test_float_amount_truncated() {
        let json = r#"{"type": "EXPENSE", "amount": 500.7, "category": "Food"}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.amount, 5000); // 500 toman * 10 = 5000 rial
        assert!(result.was_repaired);
        assert!(result.repair_notes.iter().any(|n| n.contains("truncated")));
    }

    #[test]
    fn test_large_amount() {
        let json = r#"{"type": "INCOME", "amount": 50000000, "category": "Income"}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.amount, 500_000_000); // 50M toman * 10 = 500M rial
    }

    #[test]
    fn test_amount_overflow_after_multiplication() {
        // Amount is deserialized as f64, so pick a value that is *exactly*
        // representable as f64 to avoid relying on rounding direction near the
        // boundary. 1e18 is exact (10^18 = 2^18 * 5^18, mantissa fits in 52 bits),
        // exceeds i64::MAX/10 (922337203685477580), and 1e18 * 10 = 1e19 overflows i64.
        let json = r#"{"type": "EXPENSE", "amount": 1000000000000000000, "category": "Food"}"#;
        let err = parse_ai_transaction_json(json).unwrap_err();
        match err {
            HesabyarError::ValidationError { detail } => {
                assert!(detail.contains("overflows"));
            }
            _ => panic!("Expected ValidationError for overflow"),
        }
    }

    // =====================================================================
    // Category normalization tests
    // =====================================================================

    #[test]
    fn test_core_categories_passthrough() {
        for cat in [
            "Food",
            "Transportation",
            "Shopping",
            "Bills",
            "Installments",
            "Loans",
            "Income",
            "Other",
        ] {
            let json = format!(
                r#"{{"type": "EXPENSE", "amount": 1000, "category": "{}"}}"#,
                cat
            );
            let result = parse_ai_transaction_json(&json).unwrap();
            assert_eq!(result.result.category, cat);
        }
    }

    #[test]
    fn test_extended_personal_care() {
        let json = r#"{"type": "EXPENSE", "amount": 1000, "category": "Personal Care"}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.category, "Shopping");
        assert!(result.was_repaired);
    }

    #[test]
    fn test_extended_education() {
        let json = r#"{"type": "EXPENSE", "amount": 1000, "category": "Education"}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.category, "Other");
    }

    #[test]
    fn test_extended_rent_utilities() {
        let json = r#"{"type": "EXPENSE", "amount": 1000, "category": "Rent & Utilities"}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.category, "Bills");
    }

    #[test]
    fn test_extended_loans_debt() {
        let json = r#"{"type": "EXPENSE", "amount": 1000, "category": "Loans & Debt"}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.category, "Loans");
    }

    #[test]
    fn test_extended_events_gifts() {
        let json = r#"{"type": "EXPENSE", "amount": 1000, "category": "Events & Gifts"}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.category, "Other");
    }

    #[test]
    fn test_extended_charity() {
        let json = r#"{"type": "EXPENSE", "amount": 1000, "category": "Charity"}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.category, "Other");
    }

    #[test]
    fn test_extended_investment() {
        let json = r#"{"type": "EXPENSE", "amount": 1000, "category": "Investment"}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.category, "Other");
    }

    #[test]
    fn test_unknown_category_mapped_to_other() {
        let json = r#"{"type": "EXPENSE", "amount": 1000, "category": "Cryptocurrency"}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.category, "Other");
        assert!(result.was_repaired);
    }

    #[test]
    fn test_empty_category_mapped_to_other() {
        let json = r#"{"type": "EXPENSE", "amount": 1000, "category": ""}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.category, "Other");
        assert!(result.was_repaired);
    }

    // =====================================================================
    // Field clamping tests
    // =====================================================================

    #[test]
    fn test_hour_out_of_range() {
        let json = r#"{"type": "EXPENSE", "amount": 1000, "category": "Food", "hour": 25}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.hour, None);
        assert!(result.was_repaired);
    }

    #[test]
    fn test_hour_negative() {
        let json = r#"{"type": "EXPENSE", "amount": 1000, "category": "Food", "hour": -1}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.hour, None);
        assert!(result.was_repaired);
    }

    #[test]
    fn test_minute_out_of_range() {
        let json = r#"{"type": "EXPENSE", "amount": 1000, "category": "Food", "minute": 60}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.minute, None);
        assert!(result.was_repaired);
    }

    #[test]
    fn test_minute_negative() {
        let json = r#"{"type": "EXPENSE", "amount": 1000, "category": "Food", "minute": -1}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.minute, None);
        assert!(result.was_repaired);
    }

    #[test]
    fn test_valid_hour_minute() {
        let json =
            r#"{"type": "EXPENSE", "amount": 1000, "category": "Food", "hour": 14, "minute": 30}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.hour, Some(14));
        assert_eq!(result.result.minute, Some(30));
    }

    #[test]
    fn test_confidence_clamped_high() {
        let json = r#"{"type": "EXPENSE", "amount": 1000, "category": "Food", "confidence": 1.5}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert!((result.result.confidence - 1.0).abs() < f32::EPSILON);
        assert!(result.was_repaired);
    }

    #[test]
    fn test_confidence_clamped_low() {
        let json = r#"{"type": "EXPENSE", "amount": 1000, "category": "Food", "confidence": -0.5}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert!((result.result.confidence - 0.0).abs() < f32::EPSILON);
        assert!(result.was_repaired);
    }

    #[test]
    fn test_confidence_defaulted() {
        let json = r#"{"type": "EXPENSE", "amount": 1000, "category": "Food"}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert!((result.result.confidence - 0.8).abs() < f32::EPSILON);
        assert!(result.was_repaired);
        assert!(result.repair_notes.iter().any(|n| n.contains("defaulted")));
    }

    #[test]
    fn test_days_from_now_clamped() {
        let json =
            r#"{"type": "EXPENSE", "amount": 1000, "category": "Food", "daysFromNow": 9999}"#;
        let result = parse_ai_transaction_json(json).unwrap();
        assert_eq!(result.result.days_from_now, None);
        assert!(result.was_repaired);
    }

    // =====================================================================
    // Advice validation tests
    // =====================================================================

    #[test]
    fn test_valid_persian_advice() {
        let text = "شما در ماه گذشته ۲۰٪ از درآمد خود را پس‌انداز کرده‌اید. این عملکرد عالی است.";
        let result = validate_ai_advice(text);
        assert!(result.is_valid);
        assert!(!result.was_truncated);
        assert!(result.warnings.is_empty());
    }

    #[test]
    fn test_empty_advice_rejected() {
        let result = validate_ai_advice("");
        assert!(!result.is_valid);
        assert!(result.warnings.iter().any(|w| w.contains("short")));
    }

    #[test]
    fn test_short_advice_rejected() {
        let result = validate_ai_advice("سلام");
        assert!(!result.is_valid);
    }

    #[test]
    fn test_whitespace_only_rejected() {
        let result = validate_ai_advice("   \n\t  ");
        assert!(!result.is_valid);
    }

    #[test]
    fn test_long_advice_truncated() {
        let long_text = "س".repeat(15_000);
        let result = validate_ai_advice(&long_text);
        assert!(result.is_valid);
        assert!(result.was_truncated);
        assert!(result.sanitized_text.len() <= MAX_ADVICE_LENGTH);
        assert!(result.warnings.iter().any(|w| w.contains("truncated")));
    }

    #[test]
    fn test_script_tag_sanitized() {
        let text = "some advice <script>alert('x')</script> more advice here for testing";
        let result = validate_ai_advice(text);
        assert!(result.is_valid);
        assert!(!result.sanitized_text.contains("<script"));
        assert!(result.warnings.iter().any(|w| w.contains("script")));
    }

    #[test]
    fn test_javascript_tag_sanitized() {
        let text = "click javascript:void(0) for more advice and details about finance";
        let result = validate_ai_advice(text);
        assert!(result.is_valid);
        assert!(!result.sanitized_text.contains("javascript:"));
    }

    #[test]
    fn test_english_only_warning() {
        let text = "Your savings rate is excellent at 20 percent of income";
        let result = validate_ai_advice(text);
        assert!(result.is_valid);
        assert!(result.warnings.iter().any(|w| w.contains("Persian")));
    }

    // =====================================================================
    // Category normalization unit tests
    // =====================================================================

    #[test]
    fn test_normalize_category_case_insensitive() {
        assert_eq!(normalize_category("food"), "Food");
        assert_eq!(normalize_category("FOOD"), "Food");
        assert_eq!(normalize_category("Food"), "Food");
    }

    #[test]
    fn test_normalize_category_unknown() {
        assert_eq!(normalize_category("UnknownCategory"), "Other");
    }

    #[test]
    fn test_normalize_category_empty() {
        assert_eq!(normalize_category(""), "Other");
        assert_eq!(normalize_category("   "), "Other");
    }

    // =====================================================================
    // Map tx type unit tests
    // =====================================================================

    #[test]
    fn test_map_tx_type_all_variants() {
        assert_eq!(map_tx_type("EXPENSE"), TransactionType::Expense);
        assert_eq!(map_tx_type("expense"), TransactionType::Expense);
        assert_eq!(map_tx_type("INCOME"), TransactionType::Income);
        assert_eq!(map_tx_type("income"), TransactionType::Income);
        assert_eq!(map_tx_type("LOAN_DEBTOR"), TransactionType::LoanDebtor);
        assert_eq!(map_tx_type("LOAN_CREDITOR"), TransactionType::LoanCreditor);
        assert_eq!(map_tx_type("INSTALLMENT"), TransactionType::Installment);
        assert_eq!(map_tx_type("TRANSFER"), TransactionType::Transfer);
    }

    #[test]
    fn test_map_tx_type_unknown() {
        assert_eq!(map_tx_type("UNKNOWN"), TransactionType::Expense);
        assert_eq!(map_tx_type(""), TransactionType::Expense);
    }
}
