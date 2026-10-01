# Plan 005: Extract generic async UI state type

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 44dd519..HEAD -- app/src/main/java/io/github/mojri/hesabyar/ui/UiState.kt app/src/main/java/io/github/mojri/hesabyar/ui/AiAssistantViewModel.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- Priority: P2
- Effort: M
- Risk: MED — changes the type of 4 state flows across 7 files; UI consumers pattern-match on sealed subtypes.
- Depends on: none
- Category: tech-debt / duplication
- Planned at: commit `44dd519`, 2026-07-23

## Why this matters

`AdvisorUIState`, `ForecastUIState`, and `ModelFetchState` are verbatim copies of the same Idle/Loading/Success/Error shape with different payload types. Every new async source repeats this boilerplate. A single generic `UiResult<T>` removes the duplication and gives callers a shared retry/error surface.

`ParserUIState` differs because it has an extra `Confirming(result: ParsedResult)` variant. Leave it unchanged to keep the plan detachable and auditable. Future work can migrate it to a custom `UiResult` extension if the extra variant becomes important.

## Current state

`app/src/main/java/io/github/mojri/hesabyar/ui/UiState.kt` — four duplicated sealed interfaces.

Lines 21-79:
```kotlin
sealed interface ParserUIState {
  object Idle : ParserUIState
  object Loading : ParserUIState
  data class Success(val result: ParsedResult) : ParserUIState
  data class Error(val message: String) : ParserUIState
  data class Confirming(val result: ParsedResult) : ParserUIState
}

sealed interface AdvisorUIState {
  object Idle : AdvisorUIState
  object Loading : AdvisorUIState
  data class Success(val advice: String) : AdvisorUIState
  data class Error(val message: String) : AdvisorUIState
}

sealed interface ForecastUIState {
  object Idle : ForecastUIState
  object Loading : ForecastUIState
  data class Success(val forecast: String) : ForecastUIState
  data class Error(val message: String) : ForecastUIState
}

sealed interface ModelFetchState {
  object Idle : ModelFetchState
  object Loading : ModelFetchState
  data class Success(val models: List<String>) : ModelFetchState
  data class Error(val message: String) : ModelFetchState
}
```

Live usage count: 103 matches across 7 Kotlin files (screens, ViewModel, dialogs).

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Kotlin tests | `./gradlew test --no-daemon` | all pass |
| Lint | `./gradlew ktlintCheck detekt --no-daemon` | exit 0 |

## Scope

In scope:
- `app/src/main/java/io/github/mojri/hesabyar/ui/UiState.kt` — add `UiResult<T>`, remove 3 interfaces
- `app/src/main/java/io/github/mojri/hesabyar/ui/AiAssistantViewModel.kt` — update 3 state flow types
- `app/src/main/java/io/github/mojri/hesabyar/ui/screens/SmartAssistantScreen.kt` — update `AdvisorUIState` usages
- `app/src/main/java/io/github/mojri/hesabyar/ui/screens/ReportsScreen.kt` — update `AdvisorUIState` usages
- `app/src/main/java/io/github/mojri/hesabyar/ui/screens/SettingsScreen.kt` — update `ModelFetchState` usages
- `app/src/main/java/io/github/mojri/hesabyar/ui/screens/dashboard/components/SmartForecastCard.kt` — update `ForecastUIState` usages
- `app/src/main/java/io/github/mojri/hesabyar/ui/screens/dashboard/dialogs/ForecastDetailDialog.kt` — update `ForecastUIState` usages

Out of scope:
- `ParserUIState` — keep it exactly as-is.
- `BackupOperationState`, `ExportState` in the same file — do not merge them.
- Any new retry/error-handling behavior; this plan only removes duplication.

## Steps

### Step 1: Introduce `UiResult<T>` and delete duplicates

In `UiState.kt`, add a generic sealed interface above the existing types, then remove `AdvisorUIState`, `ForecastUIState`, and `ModelFetchState`:

```kotlin
sealed interface UiResult<out T> {
  object Idle : UiResult<Nothing>
  object Loading : UiResult<Nothing>
  data class Success<T>(val data: T) : UiResult<T>()
  data class Error(val message: String?) : UiResult<Nothing>
}
```

Kotlin sealed interfaces disallow unused `data class` variants — only `Success` needs explicit `data class`. The executor must confirm `Error` compiles without `data` (or add `data` if detekt/ktlint requires it).

**Verify**: `./gradlew ktlintCheck detekt --no-daemon` on `UiState.kt` alone before touching callers.

### Step 2: Update `AiAssistantViewModel` state flows

In `AiAssistantViewModel.kt`, replace:

```kotlin
  private val _modelFetchState = MutableStateFlow<ModelFetchState>(ModelFetchState.Idle)
```
with:
```kotlin
  private val _modelFetchState = MutableStateFlow<UiResult<List<String>>>(UiResult.Idle)
```

Replace `_parserState` and `_advisorState` and `_forecastState` similarly, changing only the type and the `UiResult.*` qualifier. Do not touch `ParserUIState` here — it stays as-is.

**Verify**: `./gradlew test --no-daemon --tests "io.github.mojri.hesabyar.ui.AiAssistantViewModel"` → if no direct ViewModel test exists, run the full test suite; all must pass.

### Step 3: Replace consumer imports and pattern matches

Update each consumer to:
1. Replace the import to `UiResult`
2. Replace `XxxUIState.Idle/Loading/Success/Error` with `UiResult.Idle/Loading/Success/Error`

Files to update and verify individually:
- `SmartAssistantScreen.kt` — replace `AdvisorUIState` imports/uses only
- `ReportsScreen.kt` — replace `AdvisorUIState` imports/uses only
- `SettingsScreen.kt` — replace `ModelFetchState` imports/uses only
- `SmartForecastCard.kt` — replace `ForecastUIState` imports/uses only
- `ForecastDetailDialog.kt` — replace `ForecastUIState` imports/uses only

**STOP**: if any consumer also uses the generic pattern alongside a domain-specific state name, report exactly where and whether the domain-specific name adds semantic value.

**Verify after each file**: `./gradlew test --no-daemon` → all pass.

### Step 4: Lint

**Verify**: `./gradlew ktlintCheck detekt --no-daemon` → exit 0.

## Test plan

- Existing tests to re-run: full `./gradlew test --no-daemon` suite.
- No new tests required for this extraction, because behavior is unchanged.

## Done criteria

- [ ] `UiState.kt` contains exactly one `ParserUIState` and one `UiResult<T>`
- [ ] No references to `AdvisorUIState`, `ForecastUIState`, or `ModelFetchState` remain in source
- [ ] All 5 listed consumer files compile and pass existing tests
- [ ] `./gradlew test --no-daemon` exits 0
- [ ] `./gradlew ktlintCheck detekt --no-daemon` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

- `UiState.kt` or `AiAssistantViewModel.kt` doesn't match the excerpts.
- `ParserUIState` is changed at all.
- Any consumer file fails to compile after the type rename.
- A consumer adds custom subclasses of the old domain-specific interface.
- The test suite fails twice after a reasonable fix attempt.

## Maintenance notes

- When adding a new async source, use `UiResult<T>` directly instead of creating a new ADT.
- If `ParserUIState` is ever simplified (e.g., `Confirming` removed), migrate it to `UiResult` in a follow-up plan.
