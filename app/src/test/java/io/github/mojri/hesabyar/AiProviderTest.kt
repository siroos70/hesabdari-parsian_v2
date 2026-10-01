package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.AiProvider
import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.AiProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderTest {
  @Test
  fun `ApiResult has Success and Failure variants`() {
    val success = AiProvider.ApiResult.Success("result")
    val failure = AiProvider.ApiResult.Failure("error")
    assertTrue(success is AiProvider.ApiResult.Success)
    assertTrue(failure is AiProvider.ApiResult.Failure)
  }

  @Test
  fun `ApiResult Success carries text`() {
    val text = "test response"
    val result = AiProvider.ApiResult.Success(text)
    assertEquals(text, (result as AiProvider.ApiResult.Success).text)
  }

  @Test
  fun `ApiResult Failure carries error`() {
    val error = "API error 500"
    val result = AiProvider.ApiResult.Failure(error)
    assertEquals(error, (result as AiProvider.ApiResult.Failure).error)
  }

  @Test
  fun `AiProviderConfig has expected default values`() {
    val config = AiProviderConfig()
    assertEquals(AiProviderType.GEMINI, config.providerType)
    assertNotNull(config.id)
  }

  @Test
  fun `AiProviderConfig with all fields`() {
    val config =
      AiProviderConfig(
        apiKey = "test-key",
        label = "Test Provider",
        model = "test-model",
        baseUrl = "https://test.api.com",
        providerType = AiProviderType.OPENROUTER
      )
    assertEquals("test-key", config.apiKey)
    assertEquals("Test Provider", config.label)
    assertEquals("test-model", config.model)
    assertEquals("https://test.api.com", config.baseUrl)
    assertEquals(AiProviderType.OPENROUTER, config.providerType)
  }

  @Test
  fun `AiProviderType enum has correct display names and entries`() {
    assertEquals("Google Gemini", AiProviderType.GEMINI.displayName)
    assertEquals("OpenRouter", AiProviderType.OPENROUTER.displayName)
    assertEquals("Custom Endpoint", AiProviderType.CUSTOM.displayName)
    assertEquals(3, AiProviderType.entries.size)
  }
}
