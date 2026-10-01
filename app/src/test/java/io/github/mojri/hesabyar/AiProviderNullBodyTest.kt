package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.AiProvider
import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.AiProviderType
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AiProviderNullBodyTest {
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
  fun `generateContent returns Failure when HTTP response body is null`() =
    runTest {
      mockServer.enqueue(MockResponse().setResponseCode(204))

      val baseUrl = mockServer.url("/").toString().trimEnd('/')
      val config =
        AiProviderConfig(
          apiKey = "test-key",
          baseUrl = baseUrl,
          model = "test-model",
          providerType = AiProviderType.CUSTOM
        )

      val result = AiProvider.generateContent(config, "test prompt")

      assertTrue(result is AiProvider.ApiResult.Failure)
      val error = (result as AiProvider.ApiResult.Failure).error
      assertTrue("Expected error about empty/null body, got: $error", error.contains("Empty response body"))
    }
}
