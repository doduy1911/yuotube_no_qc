package com.tubelite

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PlaybackAutoplaySessionSmokeTest {
  @Test fun mediaSessionSelectsVideoAndPersistsAutoplayToggle() {
    ActivityScenario.launch(PlaybackActivity::class.java).use { scenario ->
      lateinit var controller: MediaController
      scenario.onActivity { activity ->
        val field = PlaybackActivity::class.java.getDeclaredField("playerView").apply { isAccessible = true }
        controller = (field.get(activity) as androidx.media3.ui.PlayerView).player as MediaController
      }
      val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
      val settings = PlaybackSettings(context)
      val previous = settings.isAutoplayEnabled()
      lateinit var command: ListenableFuture<SessionResult>
      try {
        scenario.onActivity { command = controller.sendCustomCommand(
          SessionCommand(PlaybackService.ACTION_SELECT_VIDEO, Bundle.EMPTY), Bundle().apply { putString("videoId", "dQw4w9WgXcQ") },
        ) }
        command.get(5, TimeUnit.SECONDS)
        val deadline = SystemClock.uptimeMillis() + 45_000
        var currentId: String? = null
        while (currentId != "dQw4w9WgXcQ" && SystemClock.uptimeMillis() < deadline) {
          scenario.onActivity { currentId = controller.currentMediaItem?.mediaId }
          if (currentId != "dQw4w9WgXcQ") SystemClock.sleep(200)
        }
        assertEquals("dQw4w9WgXcQ", currentId)
        val recommendationsDeadline = SystemClock.uptimeMillis() + 45_000
        var recommendationsJson = ""
        fun recommendationCount() = runCatching { org.json.JSONObject(recommendationsJson).optJSONArray("items")?.length() ?: 0 }.getOrDefault(0)
        while (recommendationCount() == 0 && SystemClock.uptimeMillis() < recommendationsDeadline) {
          scenario.onActivity { recommendationsJson = controller.sessionExtras.getString("recommendationsJson").orEmpty() }
          if (recommendationCount() == 0) SystemClock.sleep(200)
        }
        assertTrue("Expected service snapshot to include related videos", recommendationCount() > 0)
        scenario.onActivity { command = controller.sendCustomCommand(SessionCommand(PlaybackService.ACTION_SET_AUTOPLAY, Bundle.EMPTY), Bundle().apply { putBoolean("enabled", false) }) }
        command.get(5, TimeUnit.SECONDS)
        assertTrue(!settings.isAutoplayEnabled())
        scenario.onActivity { command = controller.sendCustomCommand(SessionCommand(PlaybackService.ACTION_SET_AUTOPLAY, Bundle.EMPTY), Bundle().apply { putBoolean("enabled", true) }) }
        command.get(5, TimeUnit.SECONDS)
        assertTrue(settings.isAutoplayEnabled())
        var readyToSeek = false
        val playbackDeadline = SystemClock.uptimeMillis() + 30_000
        while (!readyToSeek && SystemClock.uptimeMillis() < playbackDeadline) {
          scenario.onActivity { readyToSeek = controller.isPlaying && controller.duration > 2_000 }
          if (!readyToSeek) SystemClock.sleep(250)
        }
        assertTrue("Expected the selected public video to be playing before the autoplay check", readyToSeek)
        scenario.onActivity { controller.seekTo((controller.duration - 800).coerceAtLeast(0)) }
        val oldId = "dQw4w9WgXcQ"
        val autoplayDeadline = SystemClock.uptimeMillis() + 60_000
        var nextId: String? = oldId
        while (nextId == oldId && SystemClock.uptimeMillis() < autoplayDeadline) {
          scenario.onActivity { nextId = controller.currentMediaItem?.mediaId }
          if (nextId == oldId) SystemClock.sleep(250)
        }
        assertTrue("Expected end of the video to autoplay one recommendation", nextId != oldId && !nextId.isNullOrBlank())
      } finally {
        scenario.onActivity { command = controller.sendCustomCommand(SessionCommand(PlaybackService.ACTION_SET_AUTOPLAY, Bundle.EMPTY), Bundle().apply { putBoolean("enabled", previous) }) }
        command.get(5, TimeUnit.SECONDS)
        scenario.onActivity { activity -> activity.findViewById<View>(android.R.id.content).clearFocus() }
      }
    }
  }
}
