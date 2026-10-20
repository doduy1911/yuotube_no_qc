package com.tubelite

import android.widget.FrameLayout
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class MiniPlayerHostTest {
  @Test fun collapseAttachesOneOverlayAboveHomeAndConnectsPlaybackSession() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val host = host(activity)
        host.show("dQw4w9WgXcQ")
        assertTrue(host.isVisible)
        assertEquals("dQw4w9WgXcQ", host.currentVideoId)
        val content = activity.findViewById<FrameLayout>(android.R.id.content)
        val initialChildren = content.childCount
        host.show("dQw4w9WgXcQ")
        assertEquals(initialChildren, content.childCount)
      }
      val connected = AtomicBoolean(false)
      assertTrue(waitFor(5_000) {
        scenario.onActivity { connected.set(host(it).isConnected) }
        connected.get()
      })
    }
  }

  @Test fun closeRemovesOverlay() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val host = host(activity)
        host.show("dQw4w9WgXcQ")
        host.close()
        assertFalse(host.isVisible)
        assertEquals("", host.currentVideoId)
      }
    }
  }

  @Test fun collapseIntentFromPlaybackShowsMiniPlayerOnMainActivity() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        activity.handlePlaybackCollapseIntent(Intent(activity, MainActivity::class.java)
          .setAction(PlaybackActivity.ACTION_COLLAPSE_TO_MINI_PLAYER)
          .putExtra(PlaybackActivity.EXTRA_PENDING_VIDEO_ID, "dQw4w9WgXcQ"))
        assertTrue(host(activity).isVisible)
        assertEquals("dQw4w9WgXcQ", host(activity).currentVideoId)
      }
    }
  }

  private fun host(activity: MainActivity): MiniPlayerHost =
    MainActivity::class.java.getDeclaredField("miniPlayerHost").apply { isAccessible = true }.get(activity) as MiniPlayerHost

  private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
    val end = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < end) {
      if (condition()) return true
      SystemClock.sleep(50)
    }
    return condition()
  }
}
