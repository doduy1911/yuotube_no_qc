package com.tubelite

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackActivityLayoutTest {
  @Test fun portraitLayoutShowsDiscoveryAndFullscreenBackRestoresDetails() {
    ActivityScenario.launch(PlaybackActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val labels = collectText(root)
        assertTrue(labels.contains("Video liên quan"))
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

  private fun findDescription(view: View, value: String): View? {
    if (view.contentDescription == value) return view
    if (view is ViewGroup) for (index in 0 until view.childCount) findDescription(view.getChildAt(index), value)?.let { return it }
    return null
  }
}
