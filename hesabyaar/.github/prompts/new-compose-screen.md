# Template: New Compose Screen

Use this template when designing and implementing a new Jetpack Compose screen in Hesabyar.

## Metadata

- **Screen name:** 
- **Purpose:** 
- **Navigation entry point:** 
- **Data source:** <!-- Select one: Rust core | Room direct -->

## Architecture Boundaries

- The ViewModel orchestrates data loading and state mapping only.
- The ViewModel contains zero business calculations.
- No business logic or financial calculations live in the Composable function.
- If data source is `Rust core`, business calculations route via `UseCase → RustBridge → hesabyar-core`.
- If data source is `Room direct`, operations are restricted to pure CRUD / queries without business rules.

## Design & UI Checklist

- [ ] Used semantic Material 3 tokens (`MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`).
- [ ] No hardcoded colors, manual alphas (e.g. `copy(alpha = 0.5f)`), or magic numbers.
- [ ] Checked `ui/components/` before creating new components.
- [ ] Reused existing components where applicable; extracted shared UI to `ui/components/`.
- [ ] Tested RTL layout. Screen works natively in RTL.
- [ ] Any LTR overrides (`LocalLayoutDirection provides LayoutDirection.Ltr`) are justified for financial/numerical formatting only.
- [ ] Used `JalaliCalendarHelper.kt` for all user-facing date presentation and calculations.
- [ ] No direct use of `java.time.LocalDate` or `java.util.Date` for user-facing dates.

## Verification Checklist

- [ ] Added or updated Roborazzi screenshot test for the screen.
- [ ] Static analysis passes: `./gradlew --no-daemon ktlintCheck detekt`
- [ ] Unit tests pass: `./gradlew --no-daemon testDebugUnitTest`
