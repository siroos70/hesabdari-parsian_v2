package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.GeminiParser
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@Category(RustTest::class)
class GeminiParserLogLevelTest {
  @Before
  fun setUp() {
    ShadowLog.clear()
  }

  @Test
  fun parseSentenceOfflineLogsAtDebugLevel() {
    GeminiParser.parseSentenceOffline("امروز مرغ خریدم ۵ میلیون")

    val logs = ShadowLog.getLogs()
    val geminiLogs = logs.filter { it.tag == "GeminiParser" }
    assertTrue("Expected GeminiParser debug logs", geminiLogs.isNotEmpty())
    geminiLogs.forEach { log ->
      assertTrue("Log '${log.msg}' should be DEBUG level", log.type == android.util.Log.DEBUG)
    }
  }

  @Test
  fun parseSentenceOfflineLogsAreBriefWithoutFullAiResponseText() {
    GeminiParser.parseSentenceOffline("بنزین زدم ۶۰۰ هزار تومان")

    val logs = ShadowLog.getLogs()
    val geminiLogs = logs.filter { it.tag == "GeminiParser" }
    assertTrue("Expected GeminiParser logs", geminiLogs.isNotEmpty())
    geminiLogs.forEach { log ->
      val msg = log.msg
      assertTrue("Log '$msg' should be brief (under 100 chars)", msg.length < 100)
    }
  }
}
