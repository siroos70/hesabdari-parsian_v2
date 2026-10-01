package io.github.mojri.hesabyar.rust

import io.github.mojri.hesabyar.RustTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Locks in AI advice **validation + sanitization** via the real native core
 * ([RustBridge.validateAiAdvice], which delegates to Rust validate_ai_advice).
 *
 * The Rust implementation strips dangerous tags (script, script,
 * javascript) while keeping the text, flags over-short input as invalid, and
 * warns (without rejecting) when no Persian characters are present.
 *
 * Isolation is provided by [RustTest] category routing (forkEvery=1 in
 * testDebugUnitTestRust), NOT by [io.github.mojri.hesabyar.RustIsolationRule]
 * which would disable the native path this class needs to exercise.
 */
@Category(RustTest::class)
class AiAdviceSanitizationTest {
  @Test
  fun scriptTagIsStrippedAndFlagged() =
    runTest {
      val text = "نصیحت خوب <script>alert('x')</script> ادامه متن"
      val result = RustBridge.validateAiAdvice(text)

      assertNotNull(result)
      assertTrue("expected valid (only sanitization needed)", result.isValid)
      assertFalse("dangerous <script> tag must be removed", result.sanitizedText.contains("<script"))
      assertFalse("closing </script> must be removed", result.sanitizedText.contains("</script>"))
      assertTrue(
        "a warning about the removed pattern is expected",
        result.warnings.any { it.contains("script", ignoreCase = true) }
      )
    }

  @Test
  fun javascriptSchemeIsStripped() =
    runTest {
      val text = "برای صرفه جویی javascript:void(0) این کار را انجام دهید"
      val result = RustBridge.validateAiAdvice(text)
      assertTrue(result.isValid)
      assertFalse("javascript: scheme must be removed", result.sanitizedText.contains("javascript:"))
    }

  @Test
  fun tooShortAdviceIsRejected() =
    runTest {
      val result = RustBridge.validateAiAdvice("سلام")
      assertFalse(result.isValid)
      assertTrue(result.warnings.any { it.contains("short", ignoreCase = true) })
    }

  @Test
  fun validPersianAdvicePassesClean() =
    runTest {
      val text = "شما در ماه گذشته بیست درصد از درآمد خود را پس انداز کرده اید."
      val result = RustBridge.validateAiAdvice(text)
      assertTrue(result.isValid)
      assertTrue(result.warnings.isEmpty())
      assertEquals(text, result.sanitizedText)
    }

  @Test
  fun englishOnlyAdviceIsValidButWarned() =
    runTest {
      val text = "Your savings rate is excellent at twenty percent of income."
      val result = RustBridge.validateAiAdvice(text)
      assertTrue(result.isValid)
      assertTrue(
        "missing Persian content should produce a warning",
        result.warnings.any { it.contains("Persian", ignoreCase = true) }
      )
    }
}
