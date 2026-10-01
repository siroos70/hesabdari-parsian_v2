# Template: New Rust Core Method

Use this template when adding new business logic, domain calculations, or validations to `rust/hesabyar-core`.

## Metadata

- **Method name:** 
- **Business rule:** 
- **Input:** 
- **Output:** 

## Workflow Checklist

Follow this canonical sequence in order:

- [ ] 1. Write the Rust function in `rust/hesabyar-core/src/`.
- [ ] 2. Write Rust unit tests covering valid cases and edge cases (`#[cfg(test)]`).
- [ ] 3. Run Rust unit tests: `cargo test -p hesabyar-core`
- [ ] 4. Run Rust static analysis: `cargo clippy -p hesabyar-core -- -D warnings`
- [ ] 5. Regenerate UniFFI bindings: if exposed to Kotlin, ensure `#[uniffi::export]` is set (and update `app/buildSrc/template/HesabyarCore.template.kt` if signature requires defaults), then run `./gradlew --no-daemon :app:generateAndFixBindings --rerun-tasks`
- [ ] 6. Write Kotlin caller in the appropriate UseCase via `RustBridge`.
- [ ] 7. Write Kotlin unit test for the UseCase caller.

## Money Representation Checklist

- [ ] Rust canonical money representation uses `i64` (Rial).
- [ ] Kotlin canonical money representation uses `Long` (Rial).
- [ ] `BigDecimal` is used only for intermediate decimal calculations if strictly necessary.
- [ ] Zero `Float` or `Double` used in canonical money storage, FFI contracts, or business calculations.
- [ ] FFI function signatures use the approved integer types for money amounts.
