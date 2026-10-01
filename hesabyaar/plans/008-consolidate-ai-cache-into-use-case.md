# Plan 008: Consolidate AI cache from ViewModel into a repository/use case

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 44dd519..HEAD -- app/src/main/java/io/github/mojri/hesabyar/ui/AiAssistantViewModel.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- Priority: P1
- Effort: M
- Risk: MED — changes the ViewModel's cache contract and SharedPreferences wiring; must preserve existing debounce/signature behavior exactly.
- Depends on: none
- Category: testability / architecture
- Planned at: commit `44dd519`, 2026-07-23

## Why this matters

`AiAssistantViewModel` keeps forecast and advice caches as in-memory fields plus raw `SharedPreferences` reads/writes. The logic (TTL expiry, signature invalidation, debounce, persistence) is correct but lives in a UI-scoped class, making it untestable without Robolectric. Extracting it into a small `AiForecastAdviceCache` interface lets the ViewModel become a thin orchestrator and lets unit tests verify cache behavior in isolation.

## Current state

`app/src/main/java/io/github/mojri/hesabyar/ui/AiAssistantViewModel.kt` — ViewModel owns ~30 lines of cache fields plus `invalidateCaches`, `persistForecastCache`, `persistAdviceCache`, and the TTL/signature logic used by `fetchBudgetForecast` and `fetchBudgetAdvice`.

Lines 117-149:
```kotlin
    private val aiCacheDurationMs = 10 * 60 * 1000L
    private var cachedForecast: String? = sharedPrefs.getString(KEY_CACHED_FORECAST, null)
    private var cachedAdvice: String? = sharedPrefs.getString(KEY_CACHED_ADVICE, null)
    private var lastForecastFetchTimeMs = sharedPrefs.getLong(KEY_FORECAST_FETCH_TIME, 0L)
    private var lastAdviceFetchTimeMs = sharedPrefs.getLong(KEY_ADVICE_FETCH_TIME, 0L)
    private var lastKnownForecastSignature = sharedPrefs.getString(KEY_FORECAST_SIGNATURE, "") ?: ""
    private var lastKnownAdviceSignature = sharedPrefs.getString(KEY_ADVICE_SIGNATURE, "") ?: ""
    ...
    private fun persistForecastCache() {
      sharedPrefs.edit()
        .putString(KEY_CACHED_FORECAST, cachedForecast)
        .putLong(KEY_FORECAST_FETCH_TIME, lastForecastFetchTimeMs)
        .putString(KEY_FORECAST_SIGNATURE, lastKnownForecastSignature)
        .apply()
    }

    private fun persistAdviceCache() {
      sharedPrefs.edit()
        .putString(KEY_CACHED_ADVICE, cachedAdvice)
        .putLong(KEY_ADVICE_FETCH_TIME, lastAdviceFetchTimeMs)
        .putString(KEY_ADVICE_SIGNATURE, lastKnownAdviceSignature)
        .apply()
    }
```

Lines 180-189:
```kotlin
    private fun invalidateCaches() {
      cachedAdvice = null
      cachedForecast = null
      lastKnownAdviceSignature = ""
      lastKnownForecastSignature = ""
      lastAdviceFetchTimeMs = 0L
      lastForecastFetchTimeMs = 0L
      persistAdviceCache()
      persistForecastCache()
    }
```

Cache reads in `fetchBudgetAdvice`/`fetchBudgetForecast`:
```kotlin
      if (!forceRefresh &&
        currentSignature == lastKnownAdviceSignature &&
        !cachedAdvice.isNullOrEmpty()
      ) {
        _advisorState.value = AdvisorUIState.Success(cachedAdvice.orEmpty())
        return
      }
```

Existing cache consumer in `ManageAiConfigUseCase`:
```kotlin
  suspend fun fetchModels(...): Result<List<String>> {
    val cached = aiConfigManager.getCachedModels(providerType, apiKey, resolvedBaseUrl)
    if (cached != null && !cached.isExpired) { return Result.success(cached.models) }
    ...
  }
```

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Kotlin tests | `./gradlew test --no-daemon` | all pass |
| Lint | `./gradlew ktlintCheck detekt --no-daemon` | exit 0 |

## Scope

In scope:
- `app/src/main/java/io/github/mojri/hesabyar/ui/AiAssistantViewModel.kt` — remove cache fields/delegates/companion constants from here
- New file `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/AiForecastAdviceCache.kt` — interface + SharedPreferences impl
- Any new `app/src/test/.../AiForecastAdviceCacheTest.kt` file

Out of scope:
- `ManageAiConfigUseCase`'s model cache (it's a separate stale list timer, not a forecast/advice cache).
- `AdviceSignature.kt` computation logic.
- Any cache-key rotation or encryption; this plan moves existing 10-minute TTL behavior unchanged.

## Steps

### Step 1: Define the cache interface

New file `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/AiForecastAdviceCache.kt`:

```kotlin
package io.github.mojri.hesabyar.domain.usecase

interface AiForecastAdviceCache {
  suspend fun getForecast(signature: String): CacheEntry?
  suspend fun putForecast(signature: String, forecast: String)

  suspend fun getAdvice(signature: String): CacheEntry?
  suspend fun putAdvice(signature: String, advice: String)

  suspend fun clear()
}

data class CacheEntry(
  val value: String,
  val fetchedAtMillis: Long
)
```

`SharedPreferences` impl implements all of these by reading/writing the same keys currently in `AiAssistantViewModel`.

**Verify**: `./gradlew ktlintCheck detekt --no-daemon` → exit 0.

### Step 2: Add the SharedPreferences-backed implementation

Inside the same file, an inner or sibling class:

```kotlin
class SharedPrefsAiForecastAdviceCache(
  private val sharedPrefs: SharedPreferences
) : AiForecastAdviceCache {
  private val cacheDurationMs = 10 * 60 * 1000L

  override suspend fun getForecast(signature: String): CacheEntry? { ... }
  override suspend fun putForecast(signature: String, forecast: String) { ... }
  override suspend fun getAdvice(signature: String): CacheEntry? { ... }
  override suspend fun putAdvice(signature: String, advice: String) { ... }
  override suspend fun clear() { ... }
}
```

Constructor MUST take `SharedPreferences` as a constructor parameter, not read it from inside the cache class, to keep the class testable with a fake or in-memory prefs wrapper.

**STOP**: if the project uses a central/shared SharedPreferences singleton that makes constructor injection impossible, stop and report the exact path/singleton. Do not fall back to reading Android context inside the cache class.

**Verify**: `./gradlew ktlintCheck detekt --no-daemon` → exit 0.

### Step 3: Rehost the ViewModel cache fields and methods

In `AiAssistantViewModel.kt`:
1. Remove all `cachedForecast`, `cachedAdvice`, `lastForecastFetchTimeMs`, `lastAdviceFetchTimeMs`, `lastKnown*Signature`, `_lastForecastFetchTime`, `_lastAdviceFetchTime`, `persistForecastCache`, `persistAdviceCache`, `invalidateCaches`, and companion keys (`KEY_CACHED_*`, `KEY_*_FETCH_TIME`, `KEY_*_SIGNATURE`).
2. Add a `private val aiForecastAdviceCache: AiForecastAdviceCache` constructor parameter (injected via `@ViewModelInject`/`@HiltViewModel` whichever is current).
3. Rewrite `fetchBudgetForecast`:
   - Call `val cached = aiForecastAdviceCache.getForecast(signature)`
   - If `cached != null && !isExpired`, set success and return.
   - Otherwise fetch, then `aiForecastAdviceCache.putForecast(signature, result)`.
4. Rewrite `fetchBudgetAdvice` identically using `putAdvice`/`getAdvice`.
5. Keep `onFinancialDataChanged`'s debounce/timer behavior unchanged.

**STOP**: if removing the companion constants breaks a test, report the exact test and whether the test is asserting on `companion.KEY_*`.

**Verify**: `./gradlew test --no-daemon` → all pass.

### Step 4: Add cache tests without Robolectric

In `app/src/test/java/io/github/mojri/hesabyar/domain/usecase/AiForecastAdviceCacheTest.kt`:

- Build a `SharedPreferences` via `androidx.test.core.app.ApplicationProvider.getApplicationContext()` if available, or an in-memory prefs wrapper if the project already has one.
- Assert: `put` then `get` returns the same value.
- Assert: expired entry returns `null`.
- Assert: `clear` empties both forecasts and advice.

Executor: do NOT introduce Robolectric. Use plain JUnit + in-memory prefs or the existing in-memory `HesabyarRepository` pattern. If Android prefs are unavoidable, report that and stop — the goal is zero-Robolectric cache tests.

**Verify**: `./gradlew test --no-daemon --tests "io.github.mojri.hesabyar.domain.usecase.AiForecastAdviceCacheTest"` → all pass.

### Step 5: Lint

**Verify**: `./gradlew ktlintCheck detekt --no-daemon` → exit 0.

## Test plan

- New test file: `AiForecastAdviceCacheTest`
- Existing tests to re-run: `./gradlew test --no-daemon` (AiAssistantViewModel consumer tests, if any)

## Done criteria

- [ ] `AiAssistantViewModel` no longer contains SharedPreferences cache reads/writes
- [ ] `AiForecastAdviceCache` interface exists in `domain/usecase/`
- [ ] `SharedPrefsAiForecastAdviceCache` takes `SharedPreferences` via constructor
- [ ] `fetchBudgetForecast` and `fetchBudgetAdvice` both call the cache interface
- [ ] New unit test passes without Robolectric
- [ ] `./gradlew test --no-daemon` exits 0
- [ ] `./gradlew ktlintCheck detekt --no-daemon` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

- `AiAssistantViewModel.kt` lines 117-189 don't match the excerpts.
- The project requires Hilt/Dagger module registration for the new class; stop and report the exact module file.
- Adding the cache interface causes a ViewModel test failure twice after a reasonable fix attempt.
- You discover the cache is also read from a non-ViewModel class that cannot take a constructor parameter.

## Maintenance notes

- If more AI caches appear later (e.g. parsed-sentence cache), extend this interface or create a sibling `AiParsedSentenceCache`. Do not merge them unless they share the same TTL/signature semantics.
- The debounce logic in `onFinancialDataChanged` should remain in the ViewModel because it depends on `viewModelScope` and UI-state flow ordering; only the TTL/signature persistence moves.
