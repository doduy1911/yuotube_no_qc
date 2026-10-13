package com.tubelite

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.core.content.ContextCompat

class PlaybackService : MediaSessionService() {
  private lateinit var player: ExoPlayer
  private var mediaSession: MediaSession? = null

  override fun onCreate() {
    super.onCreate()
    player = ExoPlayer.Builder(this).build()
    mediaSession = MediaSession.Builder(this, player).build()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_PLAY) {
      val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL)
      if (!streamUrl.isNullOrBlank()) {
        val metadata = MediaMetadata.Builder()
          .setTitle(intent.getStringExtra(EXTRA_TITLE) ?: "TubeLite")
          .build()
        player.setMediaItem(MediaItem.Builder().setUri(streamUrl).setMediaMetadata(metadata).build())
        player.prepare()
        player.play()
      }
    }
    return super.onStartCommand(intent, flags, startId)
  }

  override fun onDestroy() {
    mediaSession?.release()
    player.release()
    super.onDestroy()
  }

  companion object {
    private const val ACTION_PLAY = "com.tubelite.action.PLAY"
    private const val EXTRA_STREAM_URL = "stream_url"
    private const val EXTRA_TITLE = "title"

    fun start(context: Context, streamUrl: String, title: String) {
      val intent = Intent(context, PlaybackService::class.java)
        .setAction(ACTION_PLAY)
        .putExtra(EXTRA_STREAM_URL, streamUrl)
        .putExtra(EXTRA_TITLE, title)
      ContextCompat.startForegroundService(context, intent)
    }
  }
}
