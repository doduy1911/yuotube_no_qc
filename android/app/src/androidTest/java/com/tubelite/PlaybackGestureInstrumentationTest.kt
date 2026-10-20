package com.tubelite

import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackGestureInstrumentationTest {
  @Test fun downwardSwipeCollapsesPlaybackActivity() {
    ActivityScenario.launch(PlaybackActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val player = PlaybackActivity::class.java.getDeclaredField("videoFrame").apply { isAccessible = true }.get(activity) as View
        val x = player.width / 2f
        val y = player.height / 2f
        player.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0))
        player.dispatchTouchEvent(MotionEvent.obtain(0, 200, MotionEvent.ACTION_UP, x + 2f, y + 320f, 0))
        assertTrue(activity.isFinishing)
      }
    }
  }

  @Test fun horizontalSwipeAndSeekAreaDoNotCollapse() {
    ActivityScenario.launch(PlaybackActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val player = PlaybackActivity::class.java.getDeclaredField("videoFrame").apply { isAccessible = true }.get(activity) as View
        val x = player.width / 2f
        val y = player.height / 2f
        player.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0))
        player.dispatchTouchEvent(MotionEvent.obtain(0, 200, MotionEvent.ACTION_UP, x + 140f, y + 8f, 0))
        assertFalse(activity.isFinishing)
        val seekY = player.height - 20f
        player.dispatchTouchEvent(MotionEvent.obtain(300, 300, MotionEvent.ACTION_DOWN, x, seekY, 0))
        player.dispatchTouchEvent(MotionEvent.obtain(300, 500, MotionEvent.ACTION_UP, x, seekY + 320f, 0))
        assertFalse(activity.isFinishing)
      }
    }
  }
}
