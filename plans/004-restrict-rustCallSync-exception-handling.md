# Plan 004: Restrict rustCallSync exception swallowing

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 44dd519..HEAD -- app/src/main/java/io/github/mojri/hesabyar/rust/RustBridge.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- Priority: P1
- Effort: S
- Risk: MED — changes the FFI error boundary; existing callers rely on silent fallback for certain failure modes.
- Depends on: none
- Category: correctness
- Planned at: commit `44dd519`, 2026-07-23

## Why this matters

`rustCallSync` catches `Exception` and returns the fallback, which silently hides programming errors, FFI panics, and unexpected runtime exceptions. For critical paths like `validateTransactionSync`, `validateBackupPayloadSync`, and `parseSentenceOfflineSync`, returning a benign fallback while swallowing the real error makes debugging impossible and can mask data corruption.

## Current state

`app/src/main/java/io/github/mojri/hesabyar/rust/RustBridge.kt` — sync FFI wrapper.

Lines 45-54:
```kotlin
  @Suppress("TooGenericExceptionCaught")
  private fun <T> rustCallSync(name: String, fallback: T, block: () -> T): T {
    if (!isRustFeatureEnabled) {
      hesabyarLogMissingCore(TAG, name)
      return fallback
    }
    return try {
      block()
    } catch (e: Exception) {
      AppLogger.e(TAG, "Rust fallback $name: ${e.message}")
      fallback
    }
  }
```

The three documented deferred APIs from the plan template are already handled in the newer async path (`rustCall`, lines 60-121). Our scope is only `rustCallSync`, which does not check deferred.

Callers relying on this behavior:
- `validateTransactionSync`
- `validateBackupPayloadSync`
- `parseSentenceOfflineSync`
- plus many formatting/date/calendar paths that use string fallbacks.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Kotlin tests | `./gradlew test --no-daemon` | all pass |
| Lint | `./gradlew ktlintCheck detekt --no-daemon` | exit 0 |

## Scope

In scope:
- `app/src/main/java/io/github/mojri/hesabyar/rust/RustBridge.kt` — only `rustCallSync`

Out of scope:
- The async `rustCall` path (lines 60+)
- Any converter/provider/bridge file that calls `rustCallSync`. This plan only changes the boundary contract; caller behavior stays the same.

## Steps

### Step 1: Narrow the caught exception classes

Replace the broad `catch (e: Exception)` in `rustCallSync` with a narrow catch that still falls back for expected FFI failures but rethrows anything that should not be silently suppressed.

Target shape (illustrative):
```kotlin
    return try {
      block()
    } catch (e: CancellationException) {
      throw e
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      throw e
    } catch (e: VirtualMachineError) {
      throw e
    } catch (e: Exception) {
      AppLogger.e(TAG, "Rust fallback $name: fallback used due to ${e.javaClass.simpleName}: ${e.message}", e)
      fallback
    }
```

You do NOT need to attend to cancellation in this step — the async `rustCall` already handles it. This plan is about removing the blind `catch (Exception)` for non-cancellation runtime errors.

**STOP**: if `CancellationException` is not already imported in `RustBridge.kt:7`, add it; if it cannot be imported, stop and report.

**Verify**: `./gradlew test --no-daemon --tests "io.github.mojri.hesabyar.rust.RustBridgeTest"` → basic smoke tests still pass.

### Step 2: Add a unit test for the new boundary

In `app/src/test/java/io/github/mojri/hesabyar/rust/RustBridgeTest.kt`, add a test that verifies `rustCallSync` rethrows `NullPointerException` and `IllegalStateException` instead of returning the fallback.

Target shape (following `testRustCallSyncWithNullValuesReturnsFallback` pattern but inverted):
```kotlin
  @Test
  fun rustCallSync_rethrowsRuntimeExceptions() {
    val bridge = RustBridge()
    assertThrows<NullPointerException> {
      bridge.rustCallSync("test.rethrow", "fallback") {
        throw NullPointerException("boom")
      }
    }
  }
```

**Verify**: `./gradlew test --no-daemon --tests "io.github.mojri.hesabyar.rust.RustBridgeTest"` → new test passes.

### Step 3: Lint

**Verify**: `./gradlew ktlintCheck detekt --no-daemon` → exit 0.

## Test plan

- New test: `rustCallSync_rethrowsRuntimeExceptions`.
- Existing tests to re-run: `RustBridgeTest`.

## Done criteria

- [ ] `rustCallSync` no longer has a single naked `catch (e: Exception)` that always returns `fallback`
- [ ] New rethrow test passes
- [ ] `RustBridgeTest` passes
- [ ] `./gradlew test --no-daemon` exits 0
- [ ] `./gradlew ktlintCheck detekt --no-daemon` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

- The code at `RustBridge.kt:45-84` doesn't match the excerpt.
- The new test fails twice after a reasonable fix attempt.
- Refactoring `rustCallSync` requires changing any caller beyond error behavior.

## Maintenance notes

- If a new async `rustCall` replaces `rustCallSync` in the future, migrate callers first, then delete `rustCallSync`.
- If UniFFI ever introduces a more specific checked exception, update the narrow catch list accordingly.
