package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.AiProvider
import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.AiProviderType
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AiProviderLogicTest {
  private val mockServer = MockWebServer()

  @Before
  fun setUp() {
    mockServer.start()
  }

  @After
  fun tearDown() {
    mockServer.shutdown()
  }

  @Test
  fun `generateContent returns Failure when apiKey is blank`() =
    runTest {
      val baseUrl = mockServer.url("/").toString().trimEnd('/')
      val config =
        AiProviderConfig(
          apiKey = "",
          baseUrl = baseUrl,
          model = "test-model",
          providerType = AiProviderType.CUSTOM
        )

      val result = AiProvider.generateContent(config, "test prompt")

      assertTrue(result is AiProvider.ApiResult.Failure)
      assertEquals("API key not configured", (result as AiProvider.ApiResult.Failure).error)
    }

  @Test
  fun `generateContent adds Bearer header when apiKey is valid`() =
    runTest {
      mockServer.enqueue(
        MockResponse().setBody("""{"choices":[{"message":{"content":"ok"}}]}""")
      )
      val baseUrl = mockServer.url("/").toString().trimEnd('/')
      val config =
        AiProviderConfig(
          apiKey = "valid-key-123",
          baseUrl = baseUrl,
          model = "test-model",
          providerType = AiProviderType.CUSTOM
        )

      AiProvider.generateContent(config, "test prompt")

      val request = mockServer.takeRequest(2, TimeUnit.SECONDS)!!
      assertEquals("Bearer valid-key-123", request.getHeader("Authorization"))
    }

  @Test
  fun `generateContent does not add Authorization header when apiKey contains newline`() =
    runTest {
      mockServer.enqueue(
        MockResponse().setBody("""{"choices":[{"message":{"content":"ok"}}]}""")
      )
      val baseUrl = mockServer.url("/").toString().trimEnd('/')
      val config =
        AiProviderConfig(
          apiKey = "key\nwith\rnewline",
          baseUrl = baseUrl,
          model = "test-model",
          providerType = AiProviderType.CUSTOM
        )

      AiProvider.generateContent(config, "test prompt")

      val request = mockServer.takeRequest(2, TimeUnit.SECONDS)!!
      assertNull("Authorization header should be null for key with newlines", request.getHeader("Authorization"))
    }

  @Test
  fun `generateContent returns Failure with HTTP error code on 401`() =
    runTest {
      mockServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
      val baseUrl = mockServer.url("/").toString().trimEnd('/')
      val config =
        AiProviderConfig(
          apiKey = "valid-key",
          baseUrl = baseUrl,
          model = "test-model",
          providerType = AiProviderType.CUSTOM
        )

      val result = AiProvider.generateContent(config, "test prompt")

      assertTrue(result is AiProvider.ApiResult.Failure)
      val error = (result as AiProvider.ApiResult.Failure).error
      assertTrue("Expected error to contain HTTP 401, got: $error", error.contains("API error 401"))
    }

  @Test
  fun `generateContent returns Failure with HTTP error code on 500`() =
    runTest {
      mockServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
      val baseUrl = mockServer.url("/").toString().trimEnd('/')
      val config =
        AiProviderConfig(
          apiKey = "valid-key",
          baseUrl = baseUrl,
          model = "test-model",
          providerType = AiProviderType.CUSTOM
        )

      val result = AiProvider.generateContent(config, "test prompt")

      assertTrue(result is AiProvider.ApiResult.Failure)
      val error = (result as AiProvider.ApiResult.Failure).error
      assertTrue("Expected error to contain HTTP 500, got: $error", error.contains("API error 500"))
    }

  @Test
  fun `generateContent sends correct JSON payload for OpenAI-compatible provider`() =
    runTest {
      mockServer.enqueue(
        MockResponse().setBody("""{"choices":[{"message":{"content":"response text"}}]}""")
      )
      val baseUrl = mockServer.url("/").toString().trimEnd('/')
      val config =
        AiProviderConfig(
          apiKey = "valid-key",
          baseUrl = baseUrl,
          model = "gpt-test",
          providerType = AiProviderType.CUSTOM
        )

      AiProvider.generateContent(config, "hello world")

      val request = mockServer.takeRequest(2, TimeUnit.SECONDS)!!
      val body = JSONObject(request.body.readUtf8())
      assertEquals("gpt-test", body.getString("model"))
      assertTrue(body.has("messages"))
      val messages = body.getJSONArray("messages")
      assertEquals(1, messages.length())
      val msg = messages.getJSONObject(0)
      assertEquals("user", msg.getString("role"))
      assertEquals("hello world", msg.getString("content"))
      assertTrue(body.has("temperature"))
    }

  @Test
  fun `generateContent includes system instruction in payload`() =
    runTest {
      mockServer.enqueue(
        MockResponse().setBody("""{"choices":[{"message":{"content":"response"}}]}""")
      )
      val baseUrl = mockServer.url("/").toString().trimEnd('/')
      val config =
        AiProviderConfig(
          apiKey = "valid-key",
          baseUrl = baseUrl,
          model = "gpt-test",
          providerType = AiProviderType.CUSTOM
        )

      AiProvider.generateContent(config, "hello", systemInstruction = "Be helpful")

      val request = mockServer.takeRequest(2, TimeUnit.SECONDS)!!
      val body = JSONObject(request.body.readUtf8())
      val messages = body.getJSONArray("messages")
      assertEquals(2, messages.length())
      val sysMsg = messages.getJSONObject(0)
      assertEquals("system", sysMsg.getString("role"))
      assertEquals("Be helpful", sysMsg.getString("content"))
      val userMsg = messages.getJSONObject(1)
      assertEquals("user", userMsg.getString("role"))
    }

  @Test
  fun `fetchModels does not add Authorization header when apiKey is blank for custom provider`() =
    runTest {
      val modelsResponse = """{"data":[{"id":"model-1","name":"Model 1"}]}"""
      mockServer.enqueue(MockResponse().setBody(modelsResponse))
      val baseUrl = mockServer.url("/").toString().trimEnd('/')

      val result = AiProvider.fetchModels(AiProviderType.CUSTOM, "", baseUrl)

      assertTrue("Expected success with empty models list for blank key", result.isSuccess)
      val request = mockServer.takeRequest(2, TimeUnit.SECONDS)!!
      assertNull("Authorization header should be null for blank key", request.getHeader("Authorization"))
    }

  @Test
  fun `fetchModels adds Bearer header when apiKey is valid for custom provider`() =
    runTest {
      val modelsResponse = """{"data":[{"id":"model-1","name":"Model 1"}]}"""
      mockServer.enqueue(MockResponse().setBody(modelsResponse))
      val baseUrl = mockServer.url("/").toString().trimEnd('/')

      val result = AiProvider.fetchModels(AiProviderType.CUSTOM, "valid-key", baseUrl)

      assertTrue(result.isSuccess)
      val request = mockServer.takeRequest(2, TimeUnit.SECONDS)!!
      assertEquals("Bearer valid-key", request.getHeader("Authorization"))
    }

  @Test
  fun `fetchModels does not add Authorization header when apiKey contains newline for custom provider`() =
    runTest {
      val modelsResponse = """{"data":[{"id":"model-1","name":"Model 1"}]}"""
      mockServer.enqueue(MockResponse().setBody(modelsResponse))
      val baseUrl = mockServer.url("/").toString().trimEnd('/')

      val result = AiProvider.fetchModels(AiProviderType.CUSTOM, "key\nwith\rnewline", baseUrl)

      assertTrue("Expected success with empty models list for key with newlines", result.isSuccess)
      val request = mockServer.takeRequest(2, TimeUnit.SECONDS)!!
      assertNull("Authorization header should be null for key with newlines", request.getHeader("Authorization"))
    }

  @Test
  fun `generateContent returns Failure when response body is empty`() =
    runTest {
      mockServer.enqueue(MockResponse().setResponseCode(200).setBody(""))
      val baseUrl = mockServer.url("/").toString().trimEnd('/')
      val config =
        AiProviderConfig(
          apiKey = "valid-key",
          baseUrl = baseUrl,
          model = "test-model",
          providerType = AiProviderType.CUSTOM
        )

      val result = AiProvider.generateContent(config, "test prompt")

      assertTrue(result is AiProvider.ApiResult.Failure)
      val error = (result as AiProvider.ApiResult.Failure).error
      assertTrue("Expected error about empty body, got: $error", error.contains("Empty response body"))
    }
}
