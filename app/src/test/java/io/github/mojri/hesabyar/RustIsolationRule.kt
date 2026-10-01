package io.github.mojri.hesabyar

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Ensures the Rust bridge global state is clean at test-class boundaries.
 *
 * Usage: add this rule to any test class that calls
 * [HesabyarApp.setRustInitializedForTesting].
 *
 * Before the annotated item the test override is cleared (no override — the
 * real load-based availability applies) and the previous override value is
 * saved; after it the saved value is restored, so neither a forced-fallback
 * class nor a forced-Rust class can leak its override to the next class even
 * when a test method or [org.junit.After] crashes.
 */
class RustIsolationRule : TestRule {
  override fun apply(
    base: Statement,
    description: Description,
  ): Statement =
    object : Statement() {
      override fun evaluate() {
        val previousOverride = HesabyarApp.getRustInitializedOverrideForTesting()
        HesabyarApp.setRustInitializedForTesting(null)
        try {
          base.evaluate()
        } finally {
          HesabyarApp.setRustInitializedForTesting(previousOverride)
        }
      }
    }
}
