package io.github.mojri.hesabyar.rust

import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.ui.JalaliNativeBridge
import kotlinx.coroutines.CancellationException

/**
 * Kotlin wrapper around the Rust shared core (hesabyar-core).
 *
 * This object owns the engine state, the [rustCallSync] safe-call helper, and
 * the [JalaliNativeBridge] calendar calls. Every other FFI domain lives in its
 * own file as an interface with default safe-call implementations
 * ([RustBridgeCurrency], [RustBridgeParser], [RustBridgeValidation],
 * [RustBridgeBudget], [RustBridgeAnalytics], [RustBridgeSearch],
 * [RustBridgeBackup]). The object inherits those members, so call sites keep
 * the `RustBridge.fooSync(...)` form.
 *
 * Bridge calls are synchronous: they run on the caller's thread through
 * [rustCallSync], with no coroutine hop. A synchronous call can block the
 * calling thread (e.g. the main thread) if invoked from UI code. If the Rust
 * library failed to load, every function returns a safe fallback. The two
 * `suspend` members ([RustBridgeValidation.validateAiAdvice],
 * [RustBridgeValidation.validateBackup]) dispatch on Dispatchers.Default.
 *
 * Naming convention: functions mirror the Rust API 1:1.
 * Generated UniFFI bindings live under [HesabyarCore].
 */
internal object RustBridge :
  JalaliNativeBridge,
  RustBridgeCore,
  RustBridgeCurrency,
  RustBridgeParser,
  RustBridgeValidation,
  RustBridgeBudget,
  RustBridgeAnalytics,
  RustBridgeSearch,
  RustBridgeBackup {
  private const val TAG = "RustBridge"

  private val available: Boolean
    get() = HesabyarApp.ensureRustInitialized()

  /** Public view of [available] so callers can decide whether a local
   *  validation result reflects a real check or merely an uninitialized engine. */
  override val isAvailable: Boolean get() = available

  @Suppress("Detekt.ThrowsCount", "TooGenericExceptionCaught")
  override fun <T> rustCallSync(
    fallback: T,
    block: () -> T
  ): T {
    if (!available) return fallback
    return try {
      block()
    } catch (e: CancellationException) {
      throw e
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      throw e
    } catch (e: VirtualMachineError) {
      throw e
    } catch (e: RuntimeException) {
      throw e
    } catch (e: Exception) {
      AppLogger.e(TAG, "Rust fallback: fallback used due to ${e.javaClass.simpleName}: ${e.message}", e)
      fallback
    }
  }

  // ===========================================================================
  // Calendar
  // ===========================================================================

  override fun gregorianToJalaliSync(timestampMs: Long): Long =
    rustCallSync(0L) {
      HesabyarCore.gregorianToJalali(timestampMs)
    }

  override fun jalaliToGregorianSync(
    year: Int,
    month: Int,
    day: Int
  ): Long = rustCallSync(Long.MIN_VALUE) { HesabyarCore.jalaliToGregorian(year, month, day) }

  // Returns the native month length, or -1 if the Rust core is unavailable or
  // the call fails. -1 is an explicit failure sentinel (valid Jalali months are
  // always >= 29); callers must fall back to local calendar logic instead of
  // treating it as a real length.
  override fun getJalaliDaysInMonthSync(
    year: Int,
    month: Int
  ): Int = rustCallSync(-1) { HesabyarCore.getJalaliDaysInMonth(year, month) }

  override fun isJalaliLeapYearSync(year: Int): Boolean = rustCallSync(false) { HesabyarCore.isJalaliLeapYear(year) }
}
