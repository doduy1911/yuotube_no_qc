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
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.Executors
import com.google.common.util.concurrent.Futures
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class PlaybackService : MediaSessionService() {
  private lateinit var player: ExoPlayer
  private var mediaSession: MediaSession? = null
  private var currentVideoId = ""
  private var currentQualityId = ""
  private var refreshAttempted = false
  private val resolver = Executors.newSingleThreadExecutor()
  private val mainHandler = Handler(Looper.getMainLooper())
  private val storage = Executors.newSingleThreadExecutor()
  private val recommendationsExecutor = Executors.newSingleThreadExecutor()
  private lateinit var playbackSettings: PlaybackSettings
  private lateinit var autoplayCoordinator: AutoplayCoordinator
  private var recommendationIds: List<String> = emptyList()
  private var recommendationsFor = ""
  private var recommendationsLoading = false
  private var recommendationError: String? = null
  private var recommendationSource = "related"
  private var recommendationGeneration = 0L
  private var waitingEndedVideoId: String? = null
  private var autoplayToken: Long? = null
  private var autoplayTransition = false
  private var autoplayFetchGeneration = 0L
  private var recommendationItems: List<VideoSummary> = emptyList()
  private var currentMetadata: VideoMetadata? = null
  private val selectGeneration = PlaybackRequestGeneration()
  private val selectionCoordinator = PlaybackSelectionCoordinator<Pair<ResolvedVideoStream, Long>>(resolver)
  private var selectionScreenState = PlaybackScreenState()
  private val saveProgress = object : Runnable {
    override fun run() {
      persistPosition()
      if (currentVideoId.isNotBlank() && player.isPlaying) mainHandler.postDelayed(this, 5_000)
    }
  }

  override fun onCreate() {
    super.onCreate()
    playbackSettings = PlaybackSettings(this)
    autoplayCoordinator = AutoplayCoordinator(playbackSettings.isAutoplayEnabled())
    player = ExoPlayer.Builder(this)
      .setAudioAttributes(AudioAttributes.DEFAULT, true)
      .setHandleAudioBecomingNoisy(true)
      .build()
    player.addListener(object : Player.Listener {
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        mainHandler.removeCallbacks(saveProgress)
        if (isPlaying) mainHandler.postDelayed(saveProgress, 5_000) else persistPosition()
        publishPlaybackState()
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
          val endedMetadata = currentMetadata
          if (endedMetadata != null) storage.execute { VideoHistoryStore.recordVideo(this@PlaybackService, endedMetadata, 0L) }
          handleTrackEnded()
        }
        publishPlaybackState()
      }

      override fun onPlayerError(error: PlaybackException) {
        if (!PlaybackPolicy.shouldRefreshSource(error.errorCode, refreshAttempted)) {
          val failedAutoplayToken = autoplayToken
          if (failedAutoplayToken != null) {
            tryNextAutoplayCandidate(failedAutoplayToken)
            return
          }
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
    mediaSession = MediaSession.Builder(this, player).setCallback(object : MediaSession.Callback {
      override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
        if (playerCommand == Player.COMMAND_STOP) invalidatePendingPlayback()
        return SessionResult.RESULT_SUCCESS
      }

      override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val commands = SessionCommands.Builder()
          .add(SessionCommand(ACTION_GET_PLAYBACK_SCREEN_STATE, android.os.Bundle.EMPTY))
          .add(SessionCommand(ACTION_SELECT_VIDEO, android.os.Bundle.EMPTY))
          .add(SessionCommand(ACTION_SET_AUTOPLAY, android.os.Bundle.EMPTY))
          .add(SessionCommand(ACTION_CLOSE_PLAYBACK, android.os.Bundle.EMPTY))
          .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
          .setAvailableSessionCommands(commands)
          .build()
      }

      override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: android.os.Bundle,
      ): com.google.common.util.concurrent.ListenableFuture<SessionResult> {
        return when (customCommand.customAction) {
          ACTION_GET_PLAYBACK_SCREEN_STATE -> {
            val state = android.os.Bundle().apply {
              putString("videoId", currentVideoId)
              putString("title", currentMetadata?.title)
              putString("channelTitle", currentMetadata?.channelTitle)
              putString("status", playbackStatus())
              putLong("positionMs", player.currentPosition.coerceAtLeast(0L))
              putBoolean("autoplayEnabled", autoplayCoordinator.isEnabled())
              putBoolean("recommendationsLoading", recommendationsLoading)
              putBoolean("autoplayTransition", autoplayTransition)
              putString("recommendationsJson", recommendationSetJson())
              putString("error", selectionScreenState.error ?: recommendationError)
            }
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, state))
          }
          ACTION_SELECT_VIDEO -> {
            selectVideo(args.getString("videoId").orEmpty(), args.getString("qualityId").orEmpty())
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
          }
          ACTION_SET_AUTOPLAY -> {
            val enabled = args.getBoolean("enabled", true)
            autoplayFetchGeneration++
            autoplayCoordinator.setEnabled(enabled)
            playbackSettings.setAutoplayEnabled(enabled)
            autoplayToken = null
            autoplayTransition = false
            if (!enabled) waitingEndedVideoId = null
            if (enabled && player.playbackState == Player.STATE_ENDED && currentVideoId.isNotBlank() && !recommendationsLoading) beginAutoplay(currentVideoId)
            publishPlaybackState()
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
          }
          ACTION_CLOSE_PLAYBACK -> {
            persistPosition()
            invalidatePendingPlayback()
            player.pause()
            player.clearMediaItems()
            currentVideoId = ""
            currentQualityId = ""
            currentMetadata = null
            selectionScreenState = PlaybackScreenState()
            recommendationIds = emptyList()
            recommendationItems = emptyList()
            recommendationsFor = ""
            recommendationsLoading = false
            publishPlaybackState()
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
          }
          else -> Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
      }
    }).build()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_SELECT_VIDEO) {
      selectVideo(intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty(), intent.getStringExtra(EXTRA_QUALITY_ID).orEmpty())
    } else if (intent?.action == ACTION_PLAY || intent?.action == ACTION_CHANGE_QUALITY) {
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
        val position = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L)
        val shouldPlay = intent.getBooleanExtra(EXTRA_SHOULD_PLAY, true)
        selectGeneration.next(videoId)
        if (intent.action == ACTION_PLAY) autoplayFetchGeneration++
        setStream(resolved, videoId, position, resetRetry = true, shouldPlay = shouldPlay, manualSelection = intent.action == ACTION_PLAY)
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
    recommendationsExecutor.shutdownNow()
    storage.shutdown()
    super.onDestroy()
  }

  private fun setStream(
    resolved: ResolvedVideoStream,
    videoId: String,
    positionMs: Long,
    resetRetry: Boolean,
    shouldPlay: Boolean = true,
    manualSelection: Boolean = false,
  ) {
    val previousMetadata = currentMetadata
    val previousId = currentVideoId
    val previousPosition = player.currentPosition
    if (previousId.isNotBlank() && previousId != videoId && previousMetadata != null && player.playbackState != Player.STATE_ENDED) {
      storage.execute { VideoHistoryStore.recordVideo(this, previousMetadata, previousPosition) }
    }
    currentVideoId = videoId
    currentQualityId = resolved.qualityId
    currentMetadata = resolved.metadata
    selectionScreenState = PlaybackScreenState(
      videoId = videoId,
      title = resolved.metadata.title,
      channelTitle = resolved.metadata.channelTitle,
      status = "loading",
      autoplayEnabled = autoplayCoordinator.isEnabled(),
    )
    val metadataSnapshot = resolved.metadata
    storage.execute { VideoHistoryStore.recordVideo(this, metadataSnapshot, positionMs) }
    if (resetRetry) refreshAttempted = false
    if (manualSelection) autoplayCoordinator.manualSelection(videoId)
    if (manualSelection || previousId != videoId) autoplayToken = null
    autoplayTransition = false
    if (recommendationsFor != videoId) {
      recommendationIds = emptyList()
      recommendationItems = emptyList()
      recommendationError = null
      recommendationSource = "related"
      recommendationsFor = ""
      recommendationsLoading = false
    }
    loadRecommendations(videoId)
    val metadata = MediaMetadata.Builder()
      .setTitle(resolved.title)
      .setArtist(resolved.metadata.channelTitle)
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
    player.playWhenReady = shouldPlay
    if (shouldPlay) player.play() else player.pause()
    publishPlaybackState()
  }

  private fun persistPosition() {
    val id = currentVideoId
    val position = if (player.playbackState == Player.STATE_ENDED) 0L else player.currentPosition
    if (id.isNotBlank()) storage.execute { VideoHistoryStore.savePosition(this, id, position) }
  }

  private fun selectVideo(videoId: String, qualityId: String = "") {
    if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return
    val request = selectGeneration.next(videoId)
    autoplayFetchGeneration++
    autoplayCoordinator.manualSelection(videoId)
    waitingEndedVideoId = null
    autoplayToken = null
    autoplayTransition = false
    recommendationGeneration++
    recommendationsFor = ""
    val previousId = currentVideoId
    val previousPosition = if (player.playbackState == Player.STATE_ENDED) 0L else player.currentPosition
    val previousMetadata = currentMetadata
    if (previousId.isNotBlank() && previousId != videoId && previousMetadata != null) {
      storage.execute { VideoHistoryStore.recordVideo(this, previousMetadata, previousPosition) }
    }
    currentVideoId = videoId
    currentMetadata = null
    currentQualityId = ""
    selectionScreenState = PlaybackScreenState(videoId = videoId).selecting(videoId)
    player.pause()
    player.clearMediaItems()
    recommendationItems = emptyList()
    recommendationIds = emptyList()
    recommendationsLoading = false
    recommendationsFor = ""
    selectionCoordinator.select(
      selectedVideoId = videoId,
      qualityId = qualityId,
      resolver = { selectedId, selectedQuality ->
        val row = runCatching { VideoHistoryDatabase.get(this).videoHistoryDao().getById(selectedId) }.getOrNull()
        val resumePosition = row?.positionMs?.takeIf { row.durationMs == 0L || it < row.durationMs - 10_000L } ?: 0L
        NewPipeVideoRepository.resolveVideo(selectedId, selectedQuality) to resumePosition
      },
      onState = { state -> mainHandler.post { selectionScreenState = state; publishPlaybackState() } },
      onSuccess = { (resolved, resumePosition) -> mainHandler.post {
        if (selectGeneration.isCurrent(request, videoId)) setStream(resolved, videoId, resumePosition, resetRetry = true, manualSelection = true)
      } },
      onFailure = { error -> mainHandler.post {
        if (!selectGeneration.isCurrent(request, videoId)) return@post
        selectionScreenState = selectionScreenState.failed(error.message ?: "Không mở được video này. Hãy thử lại.")
        recommendationsLoading = false
        publishPlaybackState()
      } },
    )
    selectionScreenState = selectionScreenState.selecting(videoId)
    publishPlaybackState()
  }

  private fun recommendationSetJson() = RecommendationSet(currentVideoId, recommendationItems, recommendationSource, recommendationError).toJson().toString()

  private fun loadRecommendations(videoId: String) {
    if (recommendationsFor == videoId) return
    val request = ++recommendationGeneration
    recommendationsFor = videoId
    recommendationsLoading = true
    recommendationError = null
    publishPlaybackState()
    recommendationsExecutor.execute {
      val result = runCatching {
        RecommendationRepository(
          NewPipeRelatedVideoSource(),
          CachedTrendingRecommendationSource(TrendingMusicRepository(YouTubeTrendingSource(), SharedPreferencesTrendingCache(this))),
        ).get(videoId)
      }
      mainHandler.post {
        if (request != recommendationGeneration || currentVideoId != videoId) return@post
        recommendationsLoading = false
        if (result.isSuccess) {
          val set = result.getOrThrow()
          recommendationItems = set.items
          recommendationIds = set.items.map { it.videoId }
          recommendationSource = set.source
          recommendationError = set.error
        } else {
          recommendationItems = emptyList()
          recommendationIds = emptyList()
          recommendationError = "Không tải được bài gợi ý."
        }
        publishPlaybackState()
        if (waitingEndedVideoId == videoId) {
          waitingEndedVideoId = null
          beginAutoplay(videoId)
        }
      }
    }
  }

  private fun handleTrackEnded() {
    val videoId = currentVideoId
    if (!autoplayCoordinator.isEnabled() || videoId.isBlank()) return
    if (recommendationsLoading || recommendationsFor != videoId) waitingEndedVideoId = videoId
    else beginAutoplay(videoId)
  }

  private fun beginAutoplay(videoId: String) {
    if (recommendationSource != "related" || autoplayCoordinator.freshCandidateCount(videoId, recommendationIds) > 0) {
      beginAutoplayWithCandidates(videoId, recommendationIds, emptyList())
      return
    }
    val request = ++autoplayFetchGeneration
    val relatedIds = recommendationIds
    recommendationsExecutor.execute {
      val trendingIds = runCatching {
        val repository = TrendingMusicRepository(YouTubeTrendingSource(), SharedPreferencesTrendingCache(this))
        val vn = runCatching { repository.get("vn", forceRefresh = false).items }.getOrDefault(emptyList())
        val international = runCatching { repository.get("international", forceRefresh = false).items }.getOrDefault(emptyList())
        (vn + international)
          .map { it.videoId }.distinct().filter { it != videoId }
      }.getOrDefault(emptyList())
      mainHandler.post {
        if (request != autoplayFetchGeneration || currentVideoId != videoId || player.playbackState != Player.STATE_ENDED || !autoplayCoordinator.isEnabled()) return@post
        beginAutoplayWithCandidates(videoId, relatedIds, trendingIds)
      }
    }
  }

  private fun beginAutoplayWithCandidates(videoId: String, related: List<String>, trending: List<String>) {
    when (val decision = autoplayCoordinator.onEnded(videoId, related, isNetworkAvailable(), trending)) {
      is AutoplayDecision.Resolve -> resolveAutoplayCandidate(decision)
      is AutoplayDecision.NeedFallback -> loadAutoplayFallback(decision.token)
      AutoplayDecision.Stop -> { autoplayToken = null; autoplayTransition = false; publishPlaybackState() }
    }
  }

  private fun loadAutoplayFallback(token: Long) {
    if (!autoplayCoordinator.isCurrent(token)) return
    val request = ++autoplayFetchGeneration
    recommendationsExecutor.execute {
      val repository = runCatching { TrendingMusicRepository(YouTubeTrendingSource(), SharedPreferencesTrendingCache(this)) }.getOrNull()
      val vn = repository?.let { runCatching { it.get("vn", forceRefresh = false).items }.getOrDefault(emptyList()) }.orEmpty()
      val international = repository?.let { runCatching { it.get("international", forceRefresh = false).items }.getOrDefault(emptyList()) }.orEmpty()
      val trendingIds = (vn + international).map { it.videoId }.distinct()
      mainHandler.post {
        if (request != autoplayFetchGeneration || !autoplayCoordinator.isCurrent(token)) return@post
        when (val decision = autoplayCoordinator.supplyFallback(token, trendingIds, isNetworkAvailable())) {
          is AutoplayDecision.Resolve -> resolveAutoplayCandidate(decision)
          is AutoplayDecision.NeedFallback, AutoplayDecision.Stop -> {
            autoplayToken = null; autoplayTransition = false
            recommendationError = "Không phát được bài kế tiếp."
            publishPlaybackState()
          }
        }
      }
    }
  }

  private fun invalidatePendingPlayback() {
    autoplayFetchGeneration++
    autoplayCoordinator.cancelPending()
    autoplayToken = null
    autoplayTransition = false
    waitingEndedVideoId = null
    selectGeneration.next("")
    publishPlaybackState()
  }

  private fun resolveAutoplayCandidate(decision: AutoplayDecision.Resolve) {
    if (!autoplayCoordinator.isCurrent(decision.token)) return
    autoplayToken = decision.token
    autoplayTransition = true
    publishPlaybackState()
    resolver.execute {
      runCatching { NewPipeVideoRepository.resolveVideo(decision.videoId) }
        .onSuccess { resolved -> mainHandler.post {
          if (autoplayCoordinator.completed(decision.token, decision.videoId)) {
            autoplayTransition = false
            setStream(resolved, decision.videoId, 0L, resetRetry = true)
            autoplayToken = decision.token
            publishPlaybackState()
          }
        } }
        .onFailure { mainHandler.post { tryNextAutoplayCandidate(decision.token) } }
    }
  }

  private fun tryNextAutoplayCandidate(token: Long) {
    if (!autoplayCoordinator.isCurrent(token)) return
    when (val next = autoplayCoordinator.failed(token, isNetworkAvailable())) {
      is AutoplayDecision.Resolve -> resolveAutoplayCandidate(next)
      is AutoplayDecision.NeedFallback -> loadAutoplayFallback(next.token)
      AutoplayDecision.Stop -> {
        autoplayToken = null
        autoplayTransition = false
        recommendationError = "Không phát được bài kế tiếp."
        publishPlaybackState()
      }
    }
  }

  private fun isNetworkAvailable(): Boolean {
    val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivity.activeNetwork ?: return false
    val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  private fun publishPlaybackState() {
    val extras = android.os.Bundle().apply {
      putString("videoId", currentVideoId)
      putString("title", currentMetadata?.title)
      putString("channelTitle", currentMetadata?.channelTitle)
      putString("status", playbackStatus())
      putLong("positionMs", if (::player.isInitialized) player.currentPosition.coerceAtLeast(0L) else 0L)
      putBoolean("autoplayEnabled", if (::autoplayCoordinator.isInitialized) autoplayCoordinator.isEnabled() else true)
      putBoolean("recommendationsLoading", recommendationsLoading)
      putBoolean("autoplayTransition", autoplayTransition)
      putString("recommendationsJson", recommendationSetJson())
      putString("error", selectionScreenState.error ?: recommendationError)
    }
    mediaSession?.setSessionExtras(extras)
  }

  private fun playbackStatus(): String = when {
    selectionScreenState.videoId == currentVideoId && selectionScreenState.status == "error" -> "error"
    selectionScreenState.videoId == currentVideoId && selectionScreenState.status == "loading" && player.playbackState == Player.STATE_IDLE -> "loading"
    !::player.isInitialized -> "idle"
    player.playbackState == Player.STATE_ENDED -> "ended"
    player.isPlaying -> "playing"
    player.playbackState == Player.STATE_BUFFERING -> "loading"
    currentVideoId.isBlank() -> "idle"
    else -> "paused"
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
    private const val EXTRA_SHOULD_PLAY = "should_play"
    internal const val ACTION_GET_PLAYBACK_SCREEN_STATE = "com.tubelite.GET_PLAYBACK_SCREEN_STATE"
    internal const val ACTION_SELECT_VIDEO = "com.tubelite.SELECT_VIDEO"
    internal const val ACTION_SET_AUTOPLAY = "com.tubelite.SET_AUTOPLAY"
    internal const val ACTION_CLOSE_PLAYBACK = "com.tubelite.CLOSE_PLAYBACK"

    internal fun select(context: Context, videoId: String, qualityId: String) {
      val intent = Intent(context, PlaybackService::class.java)
        .setAction(ACTION_SELECT_VIDEO)
        .putExtra(EXTRA_VIDEO_ID, videoId)
        .putExtra(EXTRA_QUALITY_ID, qualityId)
      ContextCompat.startForegroundService(context, intent)
    }

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

    internal fun changeQuality(context: Context, resolved: ResolvedVideoStream, videoId: String, positionMs: Long, shouldPlay: Boolean) {
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
        .putExtra(EXTRA_START_POSITION_MS, positionMs)
        .putExtra(EXTRA_SHOULD_PLAY, shouldPlay)
      ContextCompat.startForegroundService(context, intent)
    }
  }
}
