package com.tubelite

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackSettingsTest {
  @Test fun autoplayDefaultsOnAndPersistsAcrossSettingsInstances() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val preferences = context.getSharedPreferences("playback_settings", android.content.Context.MODE_PRIVATE)
    val oldValue = preferences.all["autoplay_enabled"] as? Boolean
    try {
      preferences.edit().remove("autoplay_enabled").commit()
      assertTrue(PlaybackSettings(context).isAutoplayEnabled())
      PlaybackSettings(context).setAutoplayEnabled(false)
      assertFalse(PlaybackSettings(context).isAutoplayEnabled())
    } finally {
      val editor = preferences.edit()
      if (oldValue == null) editor.remove("autoplay_enabled") else editor.putBoolean("autoplay_enabled", oldValue)
      editor.commit()
    }
  }
}
