package com.tubelite

import android.content.Context

internal class PlaybackSettings(context: Context) {
  private val preferences = context.getSharedPreferences("playback_settings", Context.MODE_PRIVATE)
  fun isAutoplayEnabled() = preferences.getBoolean(KEY_AUTOPLAY, true)
  fun setAutoplayEnabled(enabled: Boolean) { preferences.edit().putBoolean(KEY_AUTOPLAY, enabled).apply() }
  private companion object { const val KEY_AUTOPLAY = "autoplay_enabled" }
}
