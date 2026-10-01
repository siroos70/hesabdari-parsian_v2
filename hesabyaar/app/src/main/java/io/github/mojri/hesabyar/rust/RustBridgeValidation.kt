package io.github.mojri.hesabyar.rust

import io.github.mojri.hesabyar.core.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Validation domain of the RustBridge façade. See RustBridgeCore for the split.

private const val TAG = "RustBridge"

/** Record validators and AI-advice / backup validation. */
internal interface RustBridgeValidation : RustBridgeCore {
  /** True when the transaction passes the Rust validator. */
  fun validateTransactionSync(transaction: Transaction): Boolean =
    validateBoolean { HesabyarCore.validateTransaction(transaction) }

  /** True when the loan passes the Rust validator. */
  fun validateLoanSync(loan: Loan): Boolean = validateBoolean { HesabyarCore.validateLoan(loan) }

  /** True when the installment passes the Rust validator. */
  fun validateInstallmentSync(installment: Installment): Boolean =
    validateBoolean { HesabyarCore.validateInstallment(installment) }

  /** True when the parsed result passes the Rust validator. */
  fun validateParsedResultSync(result: ParsedResult): Boolean =
    validateBoolean { HesabyarCore.validateParsedResult(result) }

  /** Validates and sanitizes an AI advice text off the main thread. */
  @Suppress("TooGenericExceptionCaught") // Safety net: the Rust FFI layer can throw unchecked runtime errors.
  suspend fun validateAiAdvice(text: String): AdviceValidation {
    if (!isAvailable) {
      return AdviceValidation(
        isValid = false,
        sanitizedText = text,
        warnings = listOf("Rust not available"),
        wasTruncated = false,
      )
    }
    return try {
      withContext(Dispatchers.Default) { HesabyarCore.validateAiAdvice(text) }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      AdviceValidation(
        isValid = false,
        sanitizedText = text,
        warnings = listOf("Rust validation failed"),
        wasTruncated = false,
      )
    }
  }

  /** Validates a backup payload off the main thread. A failure means "not validated". */
  @Suppress("TooGenericExceptionCaught") // Safety net: the Rust FFI layer can throw unchecked runtime errors.
  suspend fun validateBackup(payload: BackupPayload) {
    if (!isAvailable) return
    try {
      withContext(Dispatchers.Default) {
        HesabyarCore.validateBackup(payload)
      }
    } catch (e: Exception) {
      // Swallow non-cancellation failures so a Rust/FFI error doesn't break the
      // calling coroutine. A failed validation simply means "not validated".
      if (e is CancellationException) throw e
      AppLogger.e(TAG, "Rust backup validation failed (non-fatal, treated as not validated)", e)
    }
  }
}
