package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.rust.AdviceValidation

/**
 * Decides whether an AI advice result that failed local validation must be
 * discarded in favour of the offline fallback.
 *
 * When the native Rust validator is **unavailable** we cannot trust its verdict,
 * so an unvalidated (cloud-provided) result is returned as-is rather than thrown
 * away — otherwise every AI response would be silently replaced by offline advice
 * on devices where the core failed to load. When the validator **is** available and
 * rejects the text, we must not surface unsanitized AI output, so we fall back.
 *
 * Kept as a pure, Rust-independent helper so both branches can be regression-tested
 * deterministically (the unit-test JVM always loads the native core, so the
 * `rustAvailable = false` path cannot be reached via [io.github.mojri.hesabyar.rust.RustBridge.isAvailable] here).
 */
internal object AdviceValidationPolicy {
  fun shouldDiscardOnValidationFailure(
    validation: AdviceValidation,
    rustAvailable: Boolean
  ): Boolean = !validation.isValid && rustAvailable
}
