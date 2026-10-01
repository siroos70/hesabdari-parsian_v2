package io.github.mojri.hesabyar

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [HesabyarApp.ensureRustInitialized] exception handling.
 *
 * The function stores true in the private `rustInitialized` flag after the
 * first successful initialization. Each test resets that flag and the
 * test-only override before it runs, so results do not depend on other
 * tests in the same JVM.
 */
class HesabyarAppInitTest {
  private var previousAction: (() -> Unit)? = null
  private var previousRustInitialized = false

  @Before
  fun setUp() {
    previousAction = HesabyarApp.rustNativeInitAction
    previousRustInitialized = HesabyarApp.getRustInitializedRawForTesting()
    HesabyarApp.setRustInitializedForTesting(null)
    HesabyarApp.setRustInitializedRawForTesting(false)
  }

  @After
  fun tearDown() {
    previousAction?.let { HesabyarApp.rustNativeInitAction = it }
    HesabyarApp.setRustInitializedForTesting(null)
    HesabyarApp.setRustInitializedRawForTesting(previousRustInitialized)
  }

  @Test
  fun ensureRustInitializedReturnsFalseWhenRuntimeExceptionThrownDuringInit() {
    // Simulate a UniFFI contract/checksum mismatch: HesabyarCore.initialize()
    // throws a RuntimeException (a plain RuntimeException in the generated
    // bindings) during initialization of UniffiLib.INSTANCE. IllegalStateException
    // is a RuntimeException subclass, so the same catch branch handles it.
    HesabyarApp.rustNativeInitAction = {
      throw IllegalStateException("simulated UniFFI checksum/contract mismatch")
    }

    val result = HesabyarApp.ensureRustInitialized()

    assertFalse(
      "RuntimeException during init must be caught and return false (fallback), not propagate",
      result,
    )
  }

  @Test
  fun ensureRustInitializedOverrideShortCircuitsWithoutInit() {
    HesabyarApp.setRustInitializedForTesting(true)
    assertTrue(HesabyarApp.ensureRustInitialized())
    HesabyarApp.setRustInitializedForTesting(false)
    assertFalse(HesabyarApp.ensureRustInitialized())
  }
}
