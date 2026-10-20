package com.tubelite

import android.content.Intent
import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {
  private lateinit var miniPlayerHost: MiniPlayerHost

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    miniPlayerHost = MiniPlayerHost(this)
    if (savedInstanceState?.getBoolean(STATE_MINI_PLAYER_VISIBLE) == true) {
      miniPlayerHost.show(savedInstanceState.getString(STATE_MINI_PLAYER_VIDEO_ID).orEmpty())
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handlePlaybackCollapseIntent(intent)
  }

  internal fun handlePlaybackCollapseIntent(intent: Intent) {
    if (intent.action == PlaybackActivity.ACTION_COLLAPSE_TO_MINI_PLAYER) {
      val videoId = intent.getStringExtra(PlaybackActivity.EXTRA_PENDING_VIDEO_ID).orEmpty()
      miniPlayerHost.show(videoId)
      window.decorView.post { PlaybackUiEvents.emit(this, PlaybackUiEvents.MINI_PLAYER_COLLAPSED) }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    if (::miniPlayerHost.isInitialized && miniPlayerHost.isVisible) {
      outState.putBoolean(STATE_MINI_PLAYER_VISIBLE, true)
      outState.putString(STATE_MINI_PLAYER_VIDEO_ID, miniPlayerHost.currentVideoId)
    }
    super.onSaveInstanceState(outState)
  }

  override fun onDestroy() {
    if (::miniPlayerHost.isInitialized) miniPlayerHost.release()
    super.onDestroy()
  }

  private companion object {
    const val STATE_MINI_PLAYER_VISIBLE = "mini_player_visible"
    const val STATE_MINI_PLAYER_VIDEO_ID = "mini_player_video_id"
  }

  /**
   * Returns the name of the main component registered from JavaScript. This is used to schedule
   * rendering of the component.
   */
  override fun getMainComponentName(): String = "TubeLite"

  /**
   * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
   * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
   */
  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)
}
