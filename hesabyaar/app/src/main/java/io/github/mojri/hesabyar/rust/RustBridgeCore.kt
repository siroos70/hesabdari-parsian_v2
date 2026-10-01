package io.github.mojri.hesabyar.rust

import kotlinx.coroutines.CancellationException

/**
 * Contract every RustBridge domain interface builds on.
 *
 * RustBridge is a façade composed of one interface per FFI domain
 * (RustBridgeCurrency, RustBridgeParser, RustBridgeValidation, RustBridgeBudget,
 * RustBridgeAnalytics, RustBridgeSearch, RustBridgeBackup). Each domain file
 * carries the default safe-call implementations for its area, so the object
 * body stays small and each domain is readable on its own. Call sites keep the
 * `RustBridge.fooSync(...)` form because the object inherits these members.
 */
internal interface RustBridgeCore {
  /** True when the native core loaded and passed its UniFFI contract check. */
  val isAvailable: Boolean

  /**
   * Runs [block] on the native core. Returns [fallback] when Rust is
   * unavailable. A checked exception falls back too; cancellation,
   * interruption, VM errors, and runtime errors rethrow.
   */
  fun <T> rustCallSync(
    fallback: T,
    block: () -> T
  ): T

  /** Runs a Unit-returning Rust validator; true when it completes without throwing. */
  @Suppress("TooGenericExceptionCaught") // Safety net: the Rust FFI layer can throw unchecked runtime errors.
  fun validateBoolean(block: () -> Unit): Boolean =
    try {
      rustCallSync(false) {
        block()
        true
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      throw e
    } catch (e: RuntimeException) {
      throw e
    } catch (_: Exception) {
      false
    }
}
