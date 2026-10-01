//! FFI safety utilities for UniFFI bindings.
//!
//! This module ensures that:
//! 1. Rust panics never cross the FFI boundary (converted to HesabyarError)
//! 2. A panic hook is installed to log panics before they are caught
//! 3. All public FFI functions are wrapped for safety
//! 4. Slice-referencing functions (`&[T]`) are wrapped with `Vec<T>` for UniFFI proc macro compatibility

use std::sync::Once;

use crate::ai_validation::{AdviceValidation, AiParsedTransaction};
use crate::excel::WorkbookData;
use crate::models::*;
use crate::search::{SearchQuery, SearchResponse};
use crate::validation::ValidationResult;

static INIT: Once = Once::new();

/// Initialize the FFI layer. Must be called once before any FFI function.
///
/// Installs a panic hook that:
/// - Prints the panic message to stderr (for Android logcat)
/// - Ensures panics are caught by UniFFI's catch_unwind wrapper
pub fn ensure_initialized() {
    INIT.call_once(|| {
        let default_hook = std::panic::take_hook();
        std::panic::set_hook(Box::new(move |panic_info| {
            eprintln!("[hesabyar-core] Rust panic: {}", panic_info);
            default_hook(panic_info);
        }));
    });
}

/// Wrap a closure that may panic into a Result.
///
/// If the closure panics, returns `HesabyarError::ParseError` with the panic message.
pub fn catch_unwind_safe<F, T>(f: F) -> Result<T, HesabyarError>
where
    F: std::panic::UnwindSafe + std::panic::RefUnwindSafe + FnOnce() -> T,
{
    ensure_initialized();
    match std::panic::catch_unwind(f) {
        Ok(result) => Ok(result),
        Err(panic) => {
            let msg = if let Some(s) = panic.downcast_ref::<&str>() {
                s.to_string()
            } else if let Some(s) = panic.downcast_ref::<String>() {
                s.clone()
            } else {
                "Unknown Rust panic".to_string()
            };
            Err(HesabyarError::ParseError { detail: msg })
        }
    }
}

// ===========================================================================
// FFI Wrappers for advisory/analytics/dashboard functions.
//
// These take Vec<T> (not &[T]) because UniFFI proc macros do not support
// slice references across the FFI boundary.
// ===========================================================================

/// Get offline budget advice based on local rules.
#[uniffi::export]
pub fn get_offline_budget_advice(
    transactions: Vec<Transaction>,
    categories: Vec<Category>,
    excluded_category_ids: Vec<i64>,
) -> String {
    catch_unwind_safe(|| {
        crate::advisory::get_offline_budget_advice(
            &transactions,
            &categories,
            &excluded_category_ids,
        )
    })
    .unwrap_or_default()
}

/// Get offline budget forecast.
#[uniffi::export]
pub fn get_offline_forecast(
    transactions: Vec<Transaction>,
    loans: Vec<Loan>,
    installments: Vec<Installment>,
    bank_loans: Vec<BankLoan>,
    excluded_category_ids: Vec<i64>,
) -> String {
    catch_unwind_safe(|| {
        crate::advisory::get_offline_forecast(
            &transactions,
            &loans,
            &installments,
            &bank_loans,
            &excluded_category_ids,
        )
    })
    .unwrap_or_default()
}

/// Calculate debt-to-income ratio.
#[uniffi::export]
pub fn calculate_debt_to_income_ratio(
    loans: Vec<Loan>,
    installments: Vec<Installment>,
    bank_loans: Vec<BankLoan>,
    monthly_income: i64,
) -> f64 {
    catch_unwind_safe(|| {
        crate::advisory::calculate_debt_to_income_ratio(
            &loans,
            &installments,
            &bank_loans,
            monthly_income,
        )
    })
    .unwrap_or(0.0)
}

/// Predict time to reach a savings goal.
#[uniffi::export]
pub fn predict_time_to_goal(current_savings: i64, monthly_savings: i64, goal_amount: i64) -> i32 {
    catch_unwind_safe(|| {
        crate::advisory::predict_time_to_goal(current_savings, monthly_savings, goal_amount)
    })
    .unwrap_or(-1)
}

/// Calculate financial health score (0-100).
#[uniffi::export]
pub fn calculate_financial_health_score(
    transactions: Vec<Transaction>,
    loans: Vec<Loan>,
    installments: Vec<Installment>,
    bank_loans: Vec<BankLoan>,
    categories: Vec<Category>,
    excluded_category_ids: Vec<i64>,
) -> i32 {
    catch_unwind_safe(|| {
        crate::advisory::calculate_financial_health_score(
            &transactions,
            &loans,
            &installments,
            &bank_loans,
            &categories,
            &excluded_category_ids,
        )
    })
    .unwrap_or(0)
}

/// Compute analytics data from transactions, loans, installments, and categories.
///
/// Returns `None` when the Rust computation panics, so the Kotlin layer can
/// fall back to its local DB computation instead of receiving a blank default.
// The parameter list is the UniFFI FFI surface. Kotlin callers pass these
// arguments positionally through generated bindings, so it cannot change
// without a breaking release.
#[allow(clippy::too_many_arguments)]
#[uniffi::export]
pub fn compute_analytics(
    transactions: Vec<Transaction>,
    loans: Vec<Loan>,
    installments: Vec<Installment>,
    categories: Vec<Category>,
    bank_loans: Vec<BankLoan>,
    accounts: Vec<Account>,
    account_id: Option<i64>,
    include_archived: bool,
    excluded_category_ids: Vec<i64>,
) -> Option<AnalyticsData> {
    catch_unwind_safe(|| {
        crate::analytics::compute_analytics(
            &transactions,
            &loans,
            &installments,
            &categories,
            &bank_loans,
            &accounts,
            account_id,
            include_archived,
            &excluded_category_ids,
        )
    })
    .ok()
}

/// Compute dashboard data from transactions, loans, and installments.
///
/// Returns `None` when the Rust computation panics, so the Kotlin layer can
/// fall back to its local DB computation instead of receiving a blank default.
// The parameter list is the UniFFI FFI surface. Kotlin callers pass these
// arguments positionally through generated bindings, so it cannot change
// without a breaking release.
#[allow(clippy::too_many_arguments)]
#[uniffi::export]
pub fn compute_dashboard_data(
    transactions: Vec<Transaction>,
    loans: Vec<Loan>,
    installments: Vec<Installment>,
    bank_loans: Vec<BankLoan>,
    accounts: Vec<Account>,
    account_id: Option<i64>,
    include_archived: bool,
    now_ms: i64,
    excluded_category_ids: Vec<i64>,
) -> Option<DashboardData> {
    catch_unwind_safe(|| {
        crate::dashboard::compute_dashboard_data(
            &transactions,
            &loans,
            &installments,
            &bank_loans,
            &accounts,
            account_id,
            include_archived,
            now_ms,
            &excluded_category_ids,
        )
    })
    .ok()
}

// ===========================================================================
// FFI Wrappers for search functions.
// ===========================================================================

/// Search transactions with the given query.
///
/// Returns matching transactions sorted by relevance.
/// All filters are optional — omit a filter by setting its value to 0/false.
#[uniffi::export]
pub fn search_transactions(transactions: Vec<Transaction>, query: SearchQuery) -> SearchResponse {
    catch_unwind_safe(|| crate::search::search_transactions(&transactions, &query))
        .unwrap_or_default()
}

// ===========================================================================
// FFI Wrappers for crypto functions.
//
// Key management stays on the Kotlin/Android side.
// These functions receive the key as a parameter.
// ===========================================================================

/// Compute SHA-256 checksum of data.
///
/// Returns a 64-character hexadecimal string.
#[uniffi::export]
pub fn compute_checksum(data: &[u8]) -> String {
    catch_unwind_safe(|| crate::crypto::compute_checksum(data)).unwrap_or_default()
}

/// Verify SHA-256 checksum of data.
///
/// Uses constant-time comparison to prevent timing attacks.
#[uniffi::export]
pub fn verify_checksum(data: &[u8], expected: &str) -> bool {
    catch_unwind_safe(|| crate::crypto::verify_checksum(data, expected)).unwrap_or(false)
}

// ===========================================================================
// FFI Wrappers for validation functions.
// ===========================================================================

/// Validate a single transaction.
///
/// Returns `Ok(())` if valid, or `HesabyarError::ValidationError` if invalid.
#[uniffi::export]
pub fn validate_transaction(transaction: Transaction) -> Result<(), HesabyarError> {
    catch_unwind_safe(|| {
        crate::validation::validate_transaction(&transaction)
            .map_err(|e| HesabyarError::ValidationError { detail: e })
    })?
}

/// Validate a single loan.
///
/// Returns `Ok(())` if valid, or `HesabyarError::ValidationError` if invalid.
#[uniffi::export]
pub fn validate_loan(loan: Loan) -> Result<(), HesabyarError> {
    catch_unwind_safe(|| {
        crate::validation::validate_loan(&loan)
            .map_err(|e| HesabyarError::ValidationError { detail: e })
    })?
}

/// Validate a single installment.
///
/// Returns `Ok(())` if valid, or `HesabyarError::ValidationError` if invalid.
#[uniffi::export]
pub fn validate_installment(installment: Installment) -> Result<(), HesabyarError> {
    catch_unwind_safe(|| {
        crate::validation::validate_installment(&installment)
            .map_err(|e| HesabyarError::ValidationError { detail: e })
    })?
}

/// Validate a ParsedResult (AI parser output).
///
/// Returns `Ok(())` if valid, or `HesabyarError::ValidationError` if invalid.
#[uniffi::export]
pub fn validate_parsed_result(result: ParsedResult) -> Result<(), HesabyarError> {
    catch_unwind_safe(|| {
        crate::validation::validate_parsed_result(&result)
            .map_err(|e| HesabyarError::ValidationError { detail: e })
    })?
}

/// Validate an entire backup payload. Collects all errors from all entities.
///
/// Returns a `ValidationResult` with `is_valid` flag and list of error messages.
#[uniffi::export]
pub fn validate_backup_payload(payload: BackupPayload) -> ValidationResult {
    catch_unwind_safe(|| crate::validation::validate_backup_payload(&payload)).unwrap_or_default()
}

// ===========================================================================
// FFI Wrappers for Excel generation.
// ===========================================================================

/// Generate an XLSX workbook from pre-formatted sheet data.
///
/// Returns raw XLSX bytes (a valid .xlsx file).
#[uniffi::export]
pub fn generate_excel(workbook: WorkbookData) -> Result<Vec<u8>, HesabyarError> {
    catch_unwind_safe(|| crate::excel::generate_excel(&workbook))?
}

// ===========================================================================
// FFI Wrappers for AI validation functions (Phase 6).
// ===========================================================================

/// Parse and validate a raw AI JSON response into a clean `ParsedResult`.
///
/// Validates amount, normalizes category to 8 core DB values, clamps
/// out-of-range fields, and returns repair metadata.
#[uniffi::export]
pub fn parse_ai_transaction_json(json: &str) -> Result<AiParsedTransaction, HesabyarError> {
    catch_unwind_safe(|| crate::ai_validation::parse_ai_transaction_json(json))?
}

/// Validate and sanitize free-form AI advice/forecast text.
///
/// Checks minimum/maximum length, strips dangerous HTML tags, and detects
/// Persian content. Returns sanitized text with warnings.
#[uniffi::export]
pub fn validate_ai_advice(text: &str) -> AdviceValidation {
    catch_unwind_safe(|| crate::ai_validation::validate_ai_advice(text)).unwrap_or_default()
}
