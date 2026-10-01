# Plan 003: Add payment_histories to Rust BackupPayload schema

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 44dd519..HEAD -- rust/hesabyar-core/src/models/mod.rs app/src/main/java/io/github/mojri/hesabyar/domain/usecase/ManageBackupUseCase.kt app/src/main/java/io/github/mojri/hesabyar/rust/RustMappers.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- Priority: P1
- Effort: M
- Risk: MED — changes FFI schema; requires binding regeneration and touches both Rust and Kotlin mapper/test paths.
- Depends on: none
- Category: bug
- Planned at: commit `44dd519`, 2026-07-23

## Why this matters

Kotlin's `BackupPayload` includes `paymentHistories`, but Rust's `BackupPayload` does not. Serde silently discards unknown fields during deserialization, so any backup validated or parsed through `RustBridge.parseBackupJsonSync` / `validateBackupPayloadSync` silently loses payment histories. This means an apparently valid backup round-trip can still silently erase payment history records.

## Current state

`rust/hesabyar-core/src/models/mod.rs` — Rust backup schema.

Lines 254-279:
```rust
/// Backup payload for JSON export/import.
///
/// Serde's default behavior silently ignores unknown fields during
/// deserialization. This means Kotlin can pass a JSON containing extra keys
/// (e.g. `paymentHistories`, `budgets`, or any future field) and Rust will
/// parse it without error — the extra fields are simply discarded.
///
/// Missing fields default to empty collections via `#[serde(default)]`.
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
#[serde(rename_all = "camelCase")]
pub struct BackupPayload {
    pub version: i32,
    pub timestamp: i64,
    #[serde(alias = "appVersion")]
    pub app_version: String,
    #[serde(default)]
    pub transactions: Vec<Transaction>,
    #[serde(default)]
    pub loans: Vec<Loan>,
    #[serde(default)]
    pub installments: Vec<Installment>,
    #[serde(default)]
    pub bank_loans: Vec<BankLoan>,
    #[serde(default)]
    pub categories: Vec<Category>,
}
```

Lines 281-289 — default impl:
```rust
impl Default for BackupPayload {
    fn default() -> Self {
        Self {
            version: BACKUP_SCHEMA_VERSION,
            timestamp: 0,
            app_version: env!("CORE_VERSION").to_string(),
            transactions: Vec::new(),
            loans: Vec::new(),
            installments: Vec::new(),
            bank_loans: Vec::new(),
            categories: Vec::new(),
        }
    }
}
```

`app/src/main/java/io/github/mojri/hesabyar/data/BackupModels.kt` — Kotlin side already has the field:

Lines 17-28:
```kotlin
data class BackupPayload(
  val version: Int = BuildConfig.BACKUP_SCHEMA_VERSION,
  val timestamp: Long = System.currentTimeMillis(),
  val appVersion: String = BuildConfig.VERSION_NAME,
  val transactions: List<Transaction> = emptyList(),
  val loans: List<Loan> = emptyList(),
  val installments: List<Installment> = emptyList(),
  val paymentHistories: List<PaymentHistory> = emptyList(),
  val categories: List<Category> = emptyList(),
  val bankLoans: List<BankLoan> = emptyList(),
  val settings: BackupSettings = BackupSettings()
)
```

`app/src/main/java/io/github/mojri/hesabyar/domain/usecase/ManageBackupUseCase.kt` — `toRustPayload()` does not map `paymentHistories`:

Lines 230-250:
```kotlin
  private fun BackupPayload.toRustPayload(): io.github.mojri.hesabyar.rust.BackupPayload =
    io.github.mojri.hesabyar.rust.BackupPayload(
      version = version,
      timestamp = timestamp,
      appVersion = appVersion,
      transactions =
        io.github.mojri.hesabyar.rust.RustMappers
          .mapTransactions(transactions),
      loans =
        io.github.mojri.hesabyar.rust.RustMappers
          .mapLoans(loans),
      installments =
        io.github.mojri.hesabyar.rust.RustMappers
          .mapInstallments(installments),
      bankLoans =
        io.github.mojri.hesabyar.rust.RustMappers
          .mapBankLoans(bankLoans),
      categories =
        io.github.mojri.hesabyar.rust.RustMappers
          .mapCategories(categories)
    )
```

`app/src/main/java/io/github/mojri/hesabyar/rust/RustMappers.kt` — no `PaymentHistory` mappers exist yet.

Rust convention: all domain structs in this file use `#[serde(rename_all = "camelCase")]`, `#[serde(default)]` on collections, and derive `Debug, Clone, Serialize, Deserialize, uniffi::Record`.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Rust tests | `cargo test` | all pass |
| Bindings | `./gradlew :app:generateAndFixBindings --no-daemon` | bindings updated, Kotlin compiles |
| Kotlin tests | `./gradlew test --no-daemon` | all pass |
| Lint | `./gradlew ktlintCheck detekt --no-daemon` | exit 0 |

## Scope

In scope:
- `rust/hesabyar-core/src/models/mod.rs`
- `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/ManageBackupUseCase.kt` — only `toRustPayload()`
- `app/src/main/java/io/github/mojri/hesabyar/rust/RustMappers.kt` — add payment-history mappers only
- `rust/hesabyar-core/src/models/mod.rs` tests block
- Auto-generated `app/src/main/java/io/github/mojri/hesabyar/rust/hesabyar_core.kt` (do not hand-edit)

Out of scope:
- `BackupModels.kt` schema change — keep `paymentHistories` as-is.
- `settings` / `BackupSettings` Rust wiring — defer to a follow-up plan.
- `ManageBackupUseCase.kt` import/export JSON text paths that bypass Rust — do not refactor them.

## Steps

### Step 1: Add the Rust `PaymentHistory` struct

In `rust/hesabyar-core/src/models/mod.rs`, add a new `PaymentHistory` struct after `BankLoan`, matching the Kotlin shape:

```rust
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
#[serde(rename_all = "camelCase")]
pub struct PaymentHistory {
    pub id: i64,
    pub loanId: i64,
    pub amount: i64,
    pub date: i64,
    pub notes: String,
}
```

Do not change existing struct field names/types.

**Verify**: `cargo test` → all pass (build must succeed before bindings can be regenerated).

### Step 2: Wire `payment_histories` into `BackupPayload`

Update `BackupPayload` to include:

```rust
    #[serde(default)]
    pub payment_histories: Vec<PaymentHistory>,
```

Update the `Default` impl:

```rust
            payment_histories: Vec::new(),
```

**Verify**: `cargo test` → all pass.

### Step 3: Update Kotlin mappers and conversion

In `app/src/main/java/io/github/mojri/hesabyar/rust/RustMappers.kt`, add:

```kotlin
  fun mapPaymentHistory(ph: PaymentHistory): io.github.mojri.hesabyar.rust.PaymentHistory =
    io.github.mojri.hesabyar.rust.PaymentHistory(
      id = ph.id,
      loanId = ph.loanId,
      amount = ph.amount,
      date = ph.date,
      notes = ph.notes
    )

  fun mapPaymentHistories(list: List<PaymentHistory>): List<io.github.mojri.hesabyar.rust.PaymentHistory> =
    list.map { mapPaymentHistory(it) }
```

In `ManageBackupUseCase.kt` `toRustPayload()`, add:

```kotlin
      paymentHistories =
        io.github.mojri.hesabyar.rust.RustMappers
          .mapPaymentHistories(paymentHistories),
```

**Verify**: `./gradlew :app:generateAndFixBindings --no-daemon` → succeeds; `./gradlew test --no-daemon` → all pass.

### Step 4: Update Rust backup tests

In `rust/hesabyar-core/src/models/mod.rs` tests block:

- Update `test_backup_payload_ignores_unknown_fields`: the JSON already includes `paymentHistories`; change the assertion to assert `payload.payment_histories.len() == 1` and remove the "unknown fields are silently discarded" wording.
- Update `test_backup_payload_defaults_missing_collections`: add `assert!(payload.payment_histories.is_empty());`.
- Update `test_backup_payload_valid_round_trip` and `test_backup_payload_parses_camel_case_export` and `test_backup_payload_round_trips_bank_loans` to include at least one `payment_history` field in the JSON / struct.
- Add `test_backup_payload_round_trips_payment_histories`: build a `BackupPayload` with a populated `payment_histories`, serialize, deserialize, assert len and field equality.

**Verify**: `cargo test` → all pass.

### Step 5: Lint

**Verify**: `./gradlew ktlintCheck detekt --no-daemon` → exit 0.

## Test plan

- Rust: new and updated tests in `models/mod.rs` covering round-trip, defaulting, and camelCase parsing for `payment_histories`.
- Kotlin: existing `ManageBackupUseCaseTest` and `ManageBackupUseCaseValidationTest` must still pass after binding regeneration.

## Done criteria

- [ ] `PaymentHistory` struct exists in `models/mod.rs` with correct serde/uniffi derives
- [ ] `BackupPayload` includes `payment_histories` defaulting to empty vec
- [ ] `RustMappers` has `mapPaymentHistory` / `mapPaymentHistories`
- [ ] `ManageBackupUseCase.toRustPayload()` passes `paymentHistories`
- [ ] `cargo test` exits 0
- [ ] `./gradlew :app:generateAndFixBindings --no-daemon` exits 0
- [ ] `./gradlew test --no-daemon` exits 0
- [ ] `./gradlew ktlintCheck detekt --no-daemon` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

- The structs at `models/mod.rs:59-132` or `:264-279` don't match the excerpts.
- `cargo test` fails twice after a reasonable fix attempt.
- Binding generation fails because `uniffi` cannot expose the new struct; stop and report the exact UniFFI error.
- You discover `paymentHistories` is no longer present in Kotlin `BackupPayload`.
- You discover `ManageBackupUseCase.toRustPayload()` is dead code no longer used by any caller.

## Maintenance notes

- Future additions to `BackupPayload` must also update `toRustPayload()` and `RustMappers`; consider extracting the mapper map construction to reduce this drift.
- `settings` / `BackupSettings` is still unwired; only add it after this plan lands if the maintainer asks.
- `BACKUP_SCHEMA_VERSION` must not be bumped for this additive, backward-compatible change.
