package com.tubelite

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture

class PlaybackActivity : Activity() {
  private lateinit var playerView: PlayerView
  private var controllerFuture: ListenableFuture<MediaController>? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    playerView = PlayerView(this).apply {
      useController = true
      keepScreenOn = true
      setBackgroundColor(Color.BLACK)
      setShutterBackgroundColor(Color.BLACK)
    }
    window.decorView.setBackgroundColor(Color.BLACK)
    setContentView(playerView)

    val token = SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
    val future = MediaController.Builder(this, token).buildAsync()
    controllerFuture = future
    future.addListener({
      if (!isDestroyed) playerView.player = future.get()
    }, mainExecutor)
  }

  override fun onDestroy() {
    playerView.player = null
    controllerFuture?.let { MediaController.releaseFuture(it) }
    controllerFuture = null
    super.onDestroy()
  }
}
