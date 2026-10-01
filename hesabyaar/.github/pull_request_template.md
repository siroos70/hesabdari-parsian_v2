## Summary

<!-- Briefly describe the purpose and outcome of this pull request. -->

## Changes

<!-- List specific changes made in this PR. -->
- 

## Type of Change

- [ ] `feat`: New feature
- [ ] `fix`: Bug fix
- [ ] `refactor`: Code refactoring without behavior change
- [ ] `perf`: Performance improvement
- [ ] `test`: Adding or updating tests
- [ ] `docs`: Documentation updates
- [ ] `chore`: Build, CI, or tooling maintenance

## Invariants & Quality Checklist

- [ ] **Architecture & Business Logic:** Business rules and financial calculations are located in `rust/hesabyar-core` (or belong to documented exceptions). No new business logic in Kotlin.
- [ ] **Money Representation:** Amounts use `i64` in Rust and `Long` in Kotlin (representing Rial). No `Float` or `Double` used for money.
- [ ] **Room Database & Migrations:** If schema/entities changed, explicit migration is included without `fallbackToDestructiveMigration()`. New JSON schema and `MigrationTestHelper` test are referenced.
- [ ] **Persian & RTL:** Persian-first UI, Jalali calendar via `JalaliCalendarHelper.kt`, and native RTL layout (LTR used only for numerical formatting where justified).
- [ ] **Secrets:** No API keys or credentials hardcoded.
- [ ] **Style & Detekt:** Code passes `./gradlew ktlintCheck detekt`. No `@Suppress` added to bypass new findings.

## Verification & Evidence

<!-- Paste exact verification commands and raw test/lint results below. -->

```bash
# Verification command (e.g. ./scripts/check-rust.sh, ./scripts/check-android.sh, or ./scripts/check-rust-bridge.sh)

```

```text
# Raw test output / evidence
```
