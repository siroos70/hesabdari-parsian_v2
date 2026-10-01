package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.AiProviderType
import io.github.mojri.hesabyar.api.ModelCacheEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiConfigTest {
  @Test
  fun `isConfigured - blank key is not configured`() {
    val config = AiProviderConfig(apiKey = "")
    assertFalse(config.isConfigured)
  }

  @Test
  fun `isConfigured - placeholder key is not configured`() {
    val config = AiProviderConfig(apiKey = "MY_GEMINI_API_KEY")
    assertFalse(config.isConfigured)
  }

  @Test
  fun `isConfigured - valid key is configured`() {
    val config = AiProviderConfig(apiKey = "AIzaSyB_test_key_12345")
    assertTrue(config.isConfigured)
  }

  @Test
  fun `displayName uses label when provided`() {
    val config = AiProviderConfig(label = "My Custom Label")
    assertEquals("My Custom Label", config.displayName)
  }

  @Test
  fun `displayName defaults to provider type name`() {
    val geminiConfig = AiProviderConfig(providerType = AiProviderType.GEMINI)
    assertEquals("Google Gemini", geminiConfig.displayName)

    val openRouterConfig = AiProviderConfig(providerType = AiProviderType.OPENROUTER)
    assertEquals("OpenRouter", openRouterConfig.displayName)

    val customConfig = AiProviderConfig(providerType = AiProviderType.CUSTOM)
    assertEquals("Custom", customConfig.displayName)
  }

  @Test
  fun `displayName - blank label uses default`() {
    val config = AiProviderConfig(label = "")
    assertEquals("Google Gemini", config.displayName)
  }

  @Test
  fun `AiProviderType enum has correct display names`() {
    assertEquals("Google Gemini", AiProviderType.GEMINI.displayName)
    assertEquals("OpenRouter", AiProviderType.OPENROUTER.displayName)
    assertEquals("Custom Endpoint", AiProviderType.CUSTOM.displayName)
  }

  @Test
  fun `AiProviderType enum has 3 entries`() {
    assertEquals(3, AiProviderType.entries.size)
  }

  @Test
  fun `AiProviderConfig default values`() {
    val config = AiProviderConfig()
    assertEquals("", config.apiKey)
    assertEquals("", config.model)
    assertEquals("", config.baseUrl)
    assertEquals("", config.label)
    assertEquals(AiProviderType.GEMINI, config.providerType)
  }

  @Test
  fun `AiProviderConfig id is unique per instance`() {
    val c1 = AiProviderConfig()
    val c2 = AiProviderConfig()
    assertNotEquals(c1.id, c2.id)
  }

  @Test
  fun `AiProviderConfig copy preserves id`() {
    val original = AiProviderConfig(id = "test-id-123", apiKey = "key1")
    val copied = original.copy(apiKey = "key2")
    assertEquals("test-id-123", copied.id)
    assertEquals("key2", copied.apiKey)
  }

  @Test
  fun `ModelCacheEntry - not expired when recent`() {
    val entry =
      ModelCacheEntry(
        models = listOf("model1"),
        fetchedAt = System.currentTimeMillis()
      )
    assertFalse(entry.isExpired)
  }

  @Test
  fun `ModelCacheEntry - expired when older than 24h`() {
    val entry =
      ModelCacheEntry(
        models = listOf("model1"),
        fetchedAt = System.currentTimeMillis() - 25 * 60 * 60 * 1000L
      )
    assertTrue(entry.isExpired)
  }

  @Test
  fun `ModelCacheEntry - expired at exactly 24h plus one ms`() {
    val entry =
      ModelCacheEntry(
        models = listOf("model1"),
        fetchedAt = System.currentTimeMillis() - (24 * 60 * 60 * 1000L + 1)
      )
    assertTrue(entry.isExpired)
  }

  @Test
  fun `ModelCacheEntry - just under 24h is not expired`() {
    val entry =
      ModelCacheEntry(
        models = listOf("model1"),
        fetchedAt = System.currentTimeMillis() - 23 * 60 * 60 * 1000L
      )
    assertFalse(entry.isExpired)
  }

  @Test
  fun `AiProviderConfig with all fields`() {
    val config =
      AiProviderConfig(
        id = "my-id",
        providerType = AiProviderType.OPENROUTER,
        apiKey = "sk-test",
        model = "gpt-4",
        baseUrl = "https://api.openai.com/v1",
        label = "My OpenAI"
      )
    assertEquals("my-id", config.id)
    assertEquals(AiProviderType.OPENROUTER, config.providerType)
    assertEquals("sk-test", config.apiKey)
    assertEquals("gpt-4", config.model)
    assertEquals("https://api.openai.com/v1", config.baseUrl)
    assertEquals("My OpenAI", config.label)
    assertTrue(config.isConfigured)
  }

  @Test
  fun `ModelCacheEntry stores model list`() {
    val entry = ModelCacheEntry(models = listOf("gpt-4", "gpt-3.5-turbo", "claude-3"))
    assertEquals(3, entry.models.size)
    assertEquals("gpt-4", entry.models[0])
  }

  @Test
  fun `ModelCacheEntry empty models`() {
    val entry = ModelCacheEntry(models = emptyList())
    assertTrue(entry.models.isEmpty())
  }
}
