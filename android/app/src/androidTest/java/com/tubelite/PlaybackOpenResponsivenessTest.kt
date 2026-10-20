package com.tubelite

import android.content.Intent
import android.os.SystemClock
import android.widget.TextView
import android.view.View
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class PlaybackOpenResponsivenessTest {
  @Test fun activityShowsPendingLoadingStateBeforeStreamExists() {
    val intent = Intent(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext, PlaybackActivity::class.java)
      .putExtra(PlaybackActivity.EXTRA_PENDING_VIDEO_ID, "dQw4w9WgXcQ")
    ActivityScenario.launch<PlaybackActivity>(intent).use { scenario ->
      val statusField = PlaybackActivity::class.java.getDeclaredField("playbackStatusView").apply { isAccessible = true }
      val idField = PlaybackActivity::class.java.getDeclaredField("currentVideoId").apply { isAccessible = true }
      val visible = AtomicBoolean(false)
      val deadline = SystemClock.elapsedRealtime() + 3_000
      while (SystemClock.elapsedRealtime() < deadline && !visible.get()) {
        scenario.onActivity { activity ->
          visible.set((statusField.get(activity) as TextView).text.toString() == "Đang tải video…")
          assertEquals("dQw4w9WgXcQ", idField.get(activity))
        }
        if (!visible.get()) SystemClock.sleep(40)
      }
      assertTrue("loading state should render without a stream", visible.get())
    }
  }

  @Test fun resolutionErrorShowsRetryAndClearsItWhenLoadingResumes() {
    val intent = Intent(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext, PlaybackActivity::class.java)
      .putExtra(PlaybackActivity.EXTRA_PENDING_VIDEO_ID, "dQw4w9WgXcQ")
    ActivityScenario.launch<PlaybackActivity>(intent).use { scenario ->
      scenario.onActivity { activity ->
        val render = PlaybackActivity::class.java.getDeclaredMethod("renderSnapshot", Bundle::class.java).apply { isAccessible = true }
        render.invoke(activity, Bundle().apply {
          putString("videoId", "dQw4w9WgXcQ"); putString("status", "error"); putString("error", "Không tải được video")
        })
        val status = PlaybackActivity::class.java.getDeclaredField("playbackStatusView").apply { isAccessible = true }.get(activity) as TextView
        val retry = PlaybackActivity::class.java.getDeclaredField("retryPlaybackButton").apply { isAccessible = true }.get(activity) as TextView
        assertEquals("Không tải được video", status.text)
        assertEquals(View.VISIBLE, retry.visibility)
        render.invoke(activity, Bundle().apply { putString("videoId", "dQw4w9WgXcQ"); putString("status", "loading") })
        assertEquals("Đang tải video…", status.text)
        assertEquals(View.GONE, retry.visibility)
      }
    }
  }
}
