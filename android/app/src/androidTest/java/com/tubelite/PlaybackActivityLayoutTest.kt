package com.tubelite

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.SystemClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackActivityLayoutTest {
  @Test fun autoplayButtonTogglesOffAndOn() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val settings = PlaybackSettings(context)
    val original = settings.isAutoplayEnabled()
    settings.setAutoplayEnabled(true)
    try {
      ActivityScenario.launch(PlaybackActivity::class.java).use { scenario ->
        val button = PlaybackActivity::class.java.getDeclaredField("autoplayButton").apply { isAccessible = true }
        assertTrue(waitForLabel(scenario, button, "Tự phát bài tiếp theo: Bật"))
        scenario.onActivity { (button.get(it) as TextView).performClick() }
        assertTrue(waitFor(5_000) { !settings.isAutoplayEnabled() })
        assertTrue(waitForLabel(scenario, button, "Tự phát bài tiếp theo: Tắt"))
        scenario.onActivity { (button.get(it) as TextView).performClick() }
        assertTrue(waitFor(5_000) { settings.isAutoplayEnabled() })
        assertTrue(waitForLabel(scenario, button, "Tự phát bài tiếp theo: Bật"))
      }
    } finally {
      settings.setAutoplayEnabled(original)
    }
  }

  @Test fun portraitLayoutShowsDiscoveryAndFullscreenBackRestoresDetails() {
    ActivityScenario.launch(PlaybackActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val labels = collectText(root)
        assertTrue(labels.contains("Video liên quan"))
        assertTrue(labels.any { it.startsWith("Tự phát bài tiếp theo:") })
        val fullscreen = findDescription(root, "Toàn màn hình")
        assertTrue(fullscreen != null)
        fullscreen!!.performClick()
        val details = PlaybackActivity::class.java.getDeclaredField("detailsPanel").apply { isAccessible = true }.get(activity) as View
        assertEquals(View.GONE, details.visibility)
        activity.onBackPressed()
        assertEquals(View.VISIBLE, details.visibility)
      }
    }
  }

  private fun collectText(view: View): List<String> = when (view) {
    is TextView -> listOf(view.text.toString())
    is ViewGroup -> (0 until view.childCount).flatMap { collectText(view.getChildAt(it)) }
    else -> emptyList()
  }

  private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
      if (condition()) return true
      SystemClock.sleep(50)
    }
    return condition()
  }

  private fun waitForLabel(scenario: ActivityScenario<PlaybackActivity>, field: java.lang.reflect.Field, expected: String): Boolean {
    val matches = AtomicBoolean(false)
    return waitFor(5_000) {
      scenario.onActivity { matches.set((field.get(it) as TextView).text.toString() == expected) }
      matches.get()
    }
  }

  private fun findDescription(view: View, value: String): View? {
    if (view.contentDescription == value) return view
    if (view is ViewGroup) for (index in 0 until view.childCount) findDescription(view.getChildAt(index), value)?.let { return it }
    return null
  }
}
