package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.AdviceValidationPolicy
import io.github.mojri.hesabyar.rust.AdviceValidation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Locks in the AI-advice validation-failure decision
 * ([AdviceValidationPolicy.shouldDiscardOnValidationFailure]).
 *
 * This is the regression guard for the deliberate change that, when the native
 * Rust validator is unavailable, an unvalidated cloud result is returned as-is
 * instead of being discarded for offline advice. The unit-test JVM always loads
 * the native core, so the `rustAvailable = false` branch cannot be exercised
 * through [io.github.mojri.hesabyar.rust.RustBridge.isAvailable]; it is covered
 * here directly.
 */
@Category(RustTest::class)
class AdviceValidationPolicyTest {
  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  @Before
  fun setUp() {
    HesabyarApp.setRustInitializedForTesting(true)
  }

  private fun validation(isValid: Boolean) =
    AdviceValidation(
      isValid = isValid,
      sanitizedText = "داده خام هوش مصنوعی",
      warnings = if (isValid) emptyList() else listOf("too short"),
      wasTruncated = false
    )

  @Test
  fun invalidAdviceIsDiscardedWhenRustValidatorIsAvailable() {
    assertTrue(
      AdviceValidationPolicy.shouldDiscardOnValidationFailure(
        validation(isValid = false),
        rustAvailable = true
      )
    )
  }

  @Test
  fun invalidAdviceIsKeptWhenRustValidatorIsUnavailable() {
    // Regression guard: the unavailable engine must NOT force a silent fallback
    // to offline advice, or every AI response would be discarded on devices
    // where the native core failed to load.
    assertFalse(
      "invalid + unavailable must keep the AI text, not fall back",
      AdviceValidationPolicy.shouldDiscardOnValidationFailure(
        validation(isValid = false),
        rustAvailable = false
      )
    )
  }

  @Test
  fun validAdviceIsAlwaysKeptRegardlessOfRustAvailability() {
    assertFalse(
      AdviceValidationPolicy.shouldDiscardOnValidationFailure(
        validation(isValid = true),
        rustAvailable = true
      )
    )
    assertFalse(
      AdviceValidationPolicy.shouldDiscardOnValidationFailure(
        validation(isValid = true),
        rustAvailable = false
      )
    )
  }
}
