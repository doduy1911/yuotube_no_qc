package com.tubelite

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.AudioAttributes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.Executors

class PlaybackService : MediaSessionService() {
  private lateinit var player: ExoPlayer
  private var mediaSession: MediaSession? = null
  private var currentVideoId = ""
  private var currentQualityId = ""
  private var refreshAttempted = false
  private val resolver = Executors.newSingleThreadExecutor()
  private val mainHandler = Handler(Looper.getMainLooper())
  private val storage = Executors.newSingleThreadExecutor()
  private var currentMetadata: VideoMetadata? = null
  private val saveProgress = object : Runnable {
    override fun run() {
      persistPosition()
      if (currentVideoId.isNotBlank() && player.isPlaying) mainHandler.postDelayed(this, 5_000)
    }
  }

  override fun onCreate() {
    super.onCreate()
    player = ExoPlayer.Builder(this)
      .setAudioAttributes(AudioAttributes.DEFAULT, true)
      .setHandleAudioBecomingNoisy(true)
      .build()
    player.addListener(object : Player.Listener {
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        mainHandler.removeCallbacks(saveProgress)
        if (isPlaying) mainHandler.postDelayed(saveProgress, 5_000) else persistPosition()
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
          storage.execute { currentMetadata?.let { VideoHistoryStore.recordVideo(this@PlaybackService, it, 0L) } }
        }
      }

      override fun onPlayerError(error: PlaybackException) {
        if (!PlaybackPolicy.shouldRefreshSource(error.errorCode, refreshAttempted)) {
          val message = if (refreshAttempted) {
            "Không khôi phục được nguồn phát. Hãy mở lại video để thử lại."
          } else {
            "Video gặp lỗi phát. Hãy thử mở lại video."
          }
          mainHandler.post { Toast.makeText(this@PlaybackService, message, Toast.LENGTH_LONG).show() }
          return
        }
        val videoId = currentVideoId
        val qualityId = currentQualityId
        if (videoId.isBlank()) return

        refreshAttempted = true
        val position = player.currentPosition
        resolver.execute {
          runCatching { NewPipeVideoRepository.resolveVideo(videoId, qualityId) }
            .onSuccess { resolved ->
              mainHandler.post {
                if (currentVideoId != videoId || currentQualityId != qualityId) return@post
                setStream(resolved, videoId, position, resetRetry = false)
              }
            }
            .onFailure {
              mainHandler.post {
                Toast.makeText(
                  this@PlaybackService,
                  "Không khôi phục được nguồn phát. Hãy mở lại video để thử lại.",
                  Toast.LENGTH_LONG,
                ).show()
              }
            }
        }
      }
    })
    mediaSession = MediaSession.Builder(this, player).build()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_PLAY || intent?.action == ACTION_CHANGE_QUALITY) {
      val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL)
      if (!streamUrl.isNullOrBlank()) {
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
        val qualityId = intent.getStringExtra(EXTRA_QUALITY_ID).orEmpty()
        val resolved = ResolvedVideoStream(
          streamUrl,
          intent.getStringExtra(EXTRA_TITLE) ?: "TubeLite",
          qualityId,
          intent.getStringExtra(EXTRA_AUDIO_URL),
          VideoMetadata(
            videoId,
            intent.getStringExtra(EXTRA_TITLE) ?: "TubeLite",
            intent.getStringExtra(EXTRA_CHANNEL_TITLE).orEmpty(),
            intent.getStringExtra(EXTRA_THUMBNAIL_URL),
            intent.getLongExtra(EXTRA_DURATION_MS, 0L),
          ),
        )
        val position = if (intent.action == ACTION_CHANGE_QUALITY) player.currentPosition else intent.getLongExtra(EXTRA_START_POSITION_MS, 0L)
        setStream(resolved, videoId, position, resetRetry = true)
      }
    }
    return super.onStartCommand(intent, flags, startId)
  }

  override fun onDestroy() {
    persistPosition()
    mainHandler.removeCallbacks(saveProgress)
    mediaSession?.release()
    player.release()
    resolver.shutdownNow()
    storage.shutdown()
    super.onDestroy()
  }

  private fun setStream(
    resolved: ResolvedVideoStream,
    videoId: String,
    positionMs: Long,
    resetRetry: Boolean,
  ) {
    currentVideoId = videoId
    currentQualityId = resolved.qualityId
    currentMetadata = resolved.metadata
    storage.execute { VideoHistoryStore.recordVideo(this, resolved.metadata, positionMs) }
    if (resetRetry) refreshAttempted = false
    val metadata = MediaMetadata.Builder()
      .setTitle(resolved.title)
      .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
      .build()
    val item = MediaItem.Builder()
      .setMediaId(videoId)
      .setUri(resolved.videoUrl)
      .setMediaMetadata(metadata)
      .build()
    val audioUrl = resolved.audioUrl
    if (audioUrl.isNullOrBlank()) {
      player.setMediaItem(item, positionMs)
    } else {
      val sourceFactory = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(this))
      val videoSource = sourceFactory.createMediaSource(item)
      val audioSource = sourceFactory.createMediaSource(
        MediaItem.Builder()
          .setMediaId("$videoId-audio")
          .setUri(audioUrl)
          .build(),
      )
      player.setMediaSource(MergingMediaSource(videoSource, audioSource), positionMs)
    }
    player.prepare()
    player.play()
  }

  private fun persistPosition() {
    val id = currentVideoId
    val position = player.currentPosition
    if (id.isNotBlank()) storage.execute { VideoHistoryStore.savePosition(this, id, position) }
  }

  companion object {
    private const val ACTION_PLAY = "com.tubelite.action.PLAY"
    private const val ACTION_CHANGE_QUALITY = "com.tubelite.action.CHANGE_QUALITY"
    private const val EXTRA_STREAM_URL = "stream_url"
    private const val EXTRA_AUDIO_URL = "audio_url"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_VIDEO_ID = "video_id"
    private const val EXTRA_QUALITY_ID = "quality_id"
    private const val EXTRA_CHANNEL_TITLE = "channel_title"
    private const val EXTRA_THUMBNAIL_URL = "thumbnail_url"
    private const val EXTRA_DURATION_MS = "duration_ms"
    private const val EXTRA_START_POSITION_MS = "start_position_ms"

    internal fun start(context: Context, resolved: ResolvedVideoStream, videoId: String, startPositionMs: Long) {
      val intent = Intent(context, PlaybackService::class.java)
        .setAction(ACTION_PLAY)
        .putExtra(EXTRA_STREAM_URL, resolved.videoUrl)
        .putExtra(EXTRA_AUDIO_URL, resolved.audioUrl)
        .putExtra(EXTRA_TITLE, resolved.title)
        .putExtra(EXTRA_VIDEO_ID, videoId)
        .putExtra(EXTRA_QUALITY_ID, resolved.qualityId)
        .putExtra(EXTRA_CHANNEL_TITLE, resolved.metadata.channelTitle)
        .putExtra(EXTRA_THUMBNAIL_URL, resolved.metadata.thumbnailUrl)
        .putExtra(EXTRA_DURATION_MS, resolved.metadata.durationMs)
        .putExtra(EXTRA_START_POSITION_MS, startPositionMs)
      ContextCompat.startForegroundService(context, intent)
    }

    internal fun changeQuality(context: Context, resolved: ResolvedVideoStream, videoId: String) {
      val intent = Intent(context, PlaybackService::class.java)
        .setAction(ACTION_CHANGE_QUALITY)
        .putExtra(EXTRA_STREAM_URL, resolved.videoUrl)
        .putExtra(EXTRA_AUDIO_URL, resolved.audioUrl)
        .putExtra(EXTRA_TITLE, resolved.title)
        .putExtra(EXTRA_VIDEO_ID, videoId)
        .putExtra(EXTRA_QUALITY_ID, resolved.qualityId)
        .putExtra(EXTRA_CHANNEL_TITLE, resolved.metadata.channelTitle)
        .putExtra(EXTRA_THUMBNAIL_URL, resolved.metadata.thumbnailUrl)
        .putExtra(EXTRA_DURATION_MS, resolved.metadata.durationMs)
      ContextCompat.startForegroundService(context, intent)
    }
  }
}
