package com.tubelite

import android.app.Activity
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class PlaybackActivity : Activity() {
  companion object {
    internal const val EXTRA_PENDING_VIDEO_ID = "pending_video_id"
    internal const val EXTRA_QUALITY_ID = "quality_id"
    internal const val ACTION_COLLAPSE_TO_MINI_PLAYER = "com.tubelite.COLLAPSE_TO_MINI_PLAYER"
  }

  private lateinit var playerView: PlayerView
  private lateinit var qualityButton: TextView
  private lateinit var fullscreenButton: TextView
  private lateinit var detailsPanel: LinearLayout
  private lateinit var titleView: TextView
  private lateinit var channelView: TextView
  private lateinit var favoriteButton: TextView
  private lateinit var autoplayButton: TextView
  private lateinit var recommendationsList: LinearLayout
  private lateinit var recommendationsHeading: TextView
  private lateinit var recommendationsStatus: TextView
  private lateinit var playbackStatusView: TextView
  private lateinit var retryPlaybackButton: TextView
  private lateinit var rootLayout: LinearLayout
  private lateinit var videoFrame: PlaybackVideoFrame
  private val executor = Executors.newSingleThreadExecutor()
  private var qualityRequestGeneration = 0L
  private var selectedQualityId = ""
  private var qualityForVideoId = ""
  private var controllerFuture: ListenableFuture<MediaController>? = null
  private var isFullscreen = false
  private var currentVideoId = ""
  private var gestureStartX = 0f
  private var gestureStartY = 0f
  private var gestureStartRegion = PlaybackTouchRegion.OTHER
  private var collapseRequested = false
  private var collapsingToMiniPlayer = false
  private val playerListener = object : Player.Listener {
    override fun onVideoSizeChanged(videoSize: VideoSize) = updatePipAspectRatio(videoSize)
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
      qualityRequestGeneration++
      val newId = mediaItem?.mediaId.orEmpty()
      if (newId != qualityForVideoId) { qualityForVideoId = newId; selectedQualityId = "" }
      qualityButton.isEnabled = true
      refreshFromController()
    }
    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = refreshMetadata()
    override fun onPlaybackStateChanged(playbackState: Int) = updatePlaybackStatus()
    override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlaybackStatus()
  }
  private val sessionListener = object : MediaController.Listener {
    override fun onExtrasChanged(controller: MediaController, extras: android.os.Bundle) = renderSnapshot(extras)
    override fun onCustomCommand(controller: MediaController, command: SessionCommand, args: android.os.Bundle): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
      renderSnapshot(args)
      return com.google.common.util.concurrent.Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    currentVideoId = intent.getStringExtra(EXTRA_PENDING_VIDEO_ID).orEmpty()
    window.decorView.setBackgroundColor(Color.rgb(10, 10, 10))
    window.setStatusBarColor(Color.rgb(10, 10, 10)); window.setNavigationBarColor(Color.rgb(10, 10, 10))
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    buildLayout()
    val token = SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
    val future = MediaController.Builder(this, token).setListener(sessionListener).buildAsync(); controllerFuture = future
    future.addListener({ if (!isDestroyed) runCatching { future.get() }.onSuccess { controller ->
      playerView.player = controller; controller.addListener(playerListener); refreshFromController()
      requestScreenSnapshot(controller)
    } }, mainExecutor)
  }

  private fun buildLayout() {
    playerView = PlayerView(this).apply { useController = true; setBackgroundColor(Color.BLACK); setShutterBackgroundColor(Color.BLACK); resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT }
    playbackStatusView = text(14f, Color.WHITE).apply {
      gravity = Gravity.CENTER
      text = if (currentVideoId.isNotBlank()) "Đang tải video…" else ""
    }
    retryPlaybackButton = text(14f, Color.WHITE).apply {
      text = "Thử lại"
      gravity = Gravity.CENTER
      visibility = View.GONE
      setOnClickListener {
        (playerView.player as? MediaController)?.sendCustomCommand(
          SessionCommand(PlaybackService.ACTION_SELECT_VIDEO, android.os.Bundle.EMPTY),
          android.os.Bundle().apply {
            putString("videoId", currentVideoId)
            putString("qualityId", intent.getStringExtra(EXTRA_QUALITY_ID).orEmpty())
          },
        )
      }
    }
    qualityButton = overlay("⚙", "Chọn chất lượng video") { showQualityOptions() }
    fullscreenButton = overlay("⛶", "Toàn màn hình") { toggleFullscreen() }
    videoFrame = PlaybackVideoFrame(this).apply {
      setBackgroundColor(Color.BLACK); addView(playerView, FrameLayout.LayoutParams(-1, -1))
      addView(playbackStatusView, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
      addView(retryPlaybackButton, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER).apply { topMargin = dp(44) })
      addView(qualityButton, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = dp(8); marginEnd = dp(8) })
      addView(fullscreenButton, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { bottomMargin = dp(8); marginEnd = dp(8) })
    }
    videoFrame.onMotionEvent = { event, width, height ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          gestureStartX = event.x
          gestureStartY = event.y
          gestureStartRegion = when {
            event.y >= height - dp(88) -> PlaybackTouchRegion.SEEK_BAR
            event.x >= width - dp(72) && event.y <= dp(64) -> PlaybackTouchRegion.CONTROLS
            event.x >= width - dp(88) && event.y >= height - dp(88) -> PlaybackTouchRegion.CONTROLS
            else -> PlaybackTouchRegion.VIDEO
          }
        }
        MotionEvent.ACTION_UP -> {
          val decision = PlaybackGesturePolicy.evaluate(gestureStartX, gestureStartY, event.x, event.y, gestureStartRegion, dp(88).toFloat())
          if (decision == PlaybackGestureDecision.COLLAPSE && !collapseRequested) {
            collapseRequested = true
            collapsePlayback()
          }
          gestureStartRegion = PlaybackTouchRegion.OTHER
        }
        MotionEvent.ACTION_CANCEL -> gestureStartRegion = PlaybackTouchRegion.OTHER
      }
    }
    rootLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(10, 10, 10)) }
    rootLayout.addView(videoFrame, LinearLayout.LayoutParams(-1, 0))
    detailsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
    titleView = text(19f, Color.WHITE).apply { maxLines = 2 }
    channelView = text(14f, Color.LTGRAY).apply { setPadding(0, dp(4), 0, dp(8)) }
    favoriteButton = text(15f, Color.WHITE).apply { text = "♡  Yêu thích"; setPadding(dp(12), dp(8), dp(12), dp(8)); setOnClickListener { toggleFavorite() } }
    autoplayButton = text(14f, Color.WHITE).apply { text = "Tự phát bài tiếp theo: Bật"; setPadding(dp(12), dp(8), dp(12), dp(8)); setOnClickListener { toggleAutoplay() } }
    recommendationsHeading = text(17f, Color.WHITE).apply { text = "Video liên quan"; setPadding(0, dp(14), 0, dp(6)) }
    recommendationsStatus = text(14f, Color.LTGRAY)
    recommendationsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    body.addView(titleView); body.addView(channelView); body.addView(favoriteButton); body.addView(autoplayButton); body.addView(recommendationsHeading); body.addView(recommendationsStatus); body.addView(recommendationsList)
    detailsPanel.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
    rootLayout.addView(detailsPanel, LinearLayout.LayoutParams(-1, 0, 0.4375f)); setContentView(rootLayout)
    applyOrientationLayout(resources.configuration)
  }
  private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
  private fun text(size: Float, color: Int) = TextView(this).apply { textSize = size; setTextColor(color) }
  private fun overlay(label: String, description: String, action: () -> Unit) = text(20f, Color.WHITE).apply {
    text = label; setPadding(dp(12), dp(8), dp(12), dp(8)); setBackgroundColor(Color.argb(165, 20, 20, 20)); contentDescription = description; setOnClickListener { action() }
  }

  private fun refreshFromController() {
    val player = playerView.player ?: return
    val id = player.currentMediaItem?.mediaId.orEmpty(); if (id.isBlank()) return
    refreshMetadata(); updatePlaybackStatus()
    if (id != currentVideoId) { currentVideoId = id; requestRecommendations(id); refreshFavorite() }
  }
  private fun refreshMetadata() {
    val player = playerView.player ?: return
    titleView.text = player.mediaMetadata.title?.toString().orEmpty().ifBlank { "TubeLite" }
    channelView.text = player.mediaMetadata.artist?.toString().orEmpty()
  }
  private fun updatePlaybackStatus() {
    val player = playerView.player ?: return
    val state = when { player.playbackState == Player.STATE_ENDED -> "Đã phát xong"; player.isPlaying -> "Đang phát"; player.playbackState == Player.STATE_BUFFERING -> "Đang tải video…"; else -> "Đang tạm dừng" }
    if (currentVideoId.isNotBlank()) channelView.text = listOf(player.mediaMetadata.artist?.toString().orEmpty(), state).filter(String::isNotBlank).joinToString(" · ")
  }
  private fun requestRecommendations(id: String) {
    recommendationsList.removeAllViews(); recommendationsStatus.text = "Đang tải gợi ý…"
    (playerView.player as? MediaController)?.let(::requestScreenSnapshot)
  }
  private fun requestScreenSnapshot(controller: MediaController) {
    val future = controller.sendCustomCommand(SessionCommand(PlaybackService.ACTION_GET_PLAYBACK_SCREEN_STATE, android.os.Bundle.EMPTY), android.os.Bundle.EMPTY)
    future.addListener({ if (!isDestroyed) runCatching { future.get().extras }.getOrNull()?.let(::renderSnapshot) }, mainExecutor)
  }
  private fun renderSnapshot(extras: android.os.Bundle) {
    if (isDestroyed) return
    val id = extras.getString("videoId").orEmpty()
    val status = extras.getString("status").orEmpty()
    if (id.isNotBlank() && id == currentVideoId) {
      when (status) {
        "loading" -> { playbackStatusView.text = "Đang tải video…"; retryPlaybackButton.visibility = View.GONE }
        "error" -> { playbackStatusView.text = extras.getString("error") ?: "Không mở được video này."; retryPlaybackButton.visibility = View.VISIBLE }
        else -> { playbackStatusView.text = ""; retryPlaybackButton.visibility = View.GONE }
      }
    }
    autoplayButton.text = "Tự phát bài tiếp theo: ${if (extras.getBoolean("autoplayEnabled", true)) "Bật" else "Tắt"}"
    if (id.isNotBlank() && id == currentVideoId && extras.getBoolean("autoplayTransition", false)) recommendationsStatus.text = "Đang chuyển sang bài tiếp theo…"
    if (id != currentVideoId || id.isBlank()) return
    if (extras.getBoolean("recommendationsLoading", false)) { recommendationsStatus.text = "Đang tải gợi ý…"; return }
    val raw = extras.getString("recommendationsJson") ?: return
    runCatching {
      val json = org.json.JSONObject(raw)
      if (json.optString("videoId") != id) return
      val source = json.optString("source")
      val items = json.optJSONArray("items") ?: org.json.JSONArray()
      recommendationsHeading.text = if (source == "trending") "Khám phá thêm" else "Video liên quan"
      recommendationsList.removeAllViews()
      for (index in 0 until items.length()) {
        val item = items.optJSONObject(index) ?: continue
        addRecommendation(VideoSummary(item.optString("videoId"), item.optString("title"), item.optString("channelTitle"), item.optString("thumbnailUrl").takeUnless { it == "null" }, null, item.optLong("durationMs").takeIf { !item.isNull("durationMs") }))
      }
      recommendationsStatus.text = if (items.length() == 0) json.optString("error").ifBlank { extras.getString("error") ?: "Chưa có video gợi ý." } else ""
    }
  }
  private fun toggleAutoplay() {
    val controller = playerView.player as? MediaController ?: return
    val enabled = autoplayButton.text.toString().endsWith("Tắt")
    controller.sendCustomCommand(SessionCommand(PlaybackService.ACTION_SET_AUTOPLAY, android.os.Bundle.EMPTY), android.os.Bundle().apply { putBoolean("enabled", enabled) })
  }
  private fun addRecommendation(item: VideoSummary) {
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
    val image = ImageView(this).apply { setBackgroundColor(Color.rgb(38, 38, 38)); scaleType = ImageView.ScaleType.CENTER_CROP }
    row.addView(image, LinearLayout.LayoutParams(dp(144), dp(81)))
    val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(8), 0) }
    copy.addView(text(15f, Color.WHITE).apply { text = item.title; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
    copy.addView(text(13f, Color.LTGRAY).apply { text = item.channelTitle.ifBlank { "YouTube" }; setPadding(0, dp(4), 0, 0) })
    row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f)); row.setOnClickListener { selectVideo(item.videoId) }; recommendationsList.addView(row)
    item.thumbnailUrl?.let { url -> executor.execute {
      val bmp = runCatching { java.net.URL(url).openStream().use(android.graphics.BitmapFactory::decodeStream) }.getOrNull()
      if (bmp != null) runOnUiThread { if (!isDestroyed) image.setImageBitmap(bmp) }
    } }
  }
  private fun selectVideo(id: String) {
    val controller = playerView.player as? MediaController ?: return
    controller.sendCustomCommand(SessionCommand(PlaybackService.ACTION_SELECT_VIDEO, android.os.Bundle.EMPTY), android.os.Bundle().apply { putString("videoId", id) })
  }
  private fun refreshFavorite() {
    val id = currentVideoId; if (id.isBlank()) return
    executor.execute { val favorite = runCatching { VideoHistoryDatabase.get(this).videoHistoryDao().getById(id)?.isFavorite == true }.getOrDefault(false)
      runOnUiThread { if (!isDestroyed && currentVideoId == id) favoriteButton.text = if (favorite) "♥  Đã yêu thích" else "♡  Yêu thích" } }
  }
  private fun toggleFavorite() {
    val id = currentVideoId; val player = playerView.player ?: return; if (id.isBlank()) return
    val title = player.mediaMetadata.title?.toString().orEmpty(); val channel = player.mediaMetadata.artist?.toString().orEmpty()
    val duration = player.duration.coerceAtLeast(0)
    executor.execute { runCatching {
      val dao = VideoHistoryDatabase.get(this).videoHistoryDao(); val old = dao.getById(id)?.isFavorite == true
      if (!old) VideoHistoryStore.recordVideo(this, VideoMetadata(id, title, channel, null, duration))
      VideoHistoryStore.setFavorite(this, id, !old); !old
    }.onSuccess { enabled -> runOnUiThread { if (!isDestroyed && currentVideoId == id) favoriteButton.text = if (enabled) "♥  Đã yêu thích" else "♡  Yêu thích" } } }
  }
  private fun toggleFullscreen() {
    isFullscreen = !isFullscreen
    applyOrientationLayout(resources.configuration)
    fullscreenButton.text = if (isFullscreen) "⤢" else "⛶"
  }
  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    applyOrientationLayout(newConfig)
  }
  private fun applyOrientationLayout(configuration: Configuration) {
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    resizeVideoFrame(landscape)
    detailsPanel.visibility = if (landscape || isFullscreen) View.GONE else View.VISIBLE
    if (landscape || isFullscreen) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    else window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
  }
  private fun resizeVideoFrame(landscape: Boolean) {
    val video = rootLayout.getChildAt(0)
    if (landscape || isFullscreen || isInPictureInPictureMode) {
      video.layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
    } else rootLayout.post {
      video.layoutParams = LinearLayout.LayoutParams(-1, rootLayout.width * 9 / 16)
    }
  }
  @Deprecated("Back returns from fullscreen before leaving playback")
  override fun onBackPressed() { if (isFullscreen) toggleFullscreen() else if (currentVideoId.isNotBlank()) collapsePlayback() else super.onBackPressed() }
  override fun onDestroy() {
    (playerView.player as? MediaController)?.removeListener(playerListener); playerView.player = null
    controllerFuture?.let(MediaController::releaseFuture); controllerFuture = null; executor.shutdownNow(); super.onDestroy()
  }
  private fun collapsePlayback() {
    collapsingToMiniPlayer = true
    startActivity(Intent(this, MainActivity::class.java).setAction(ACTION_COLLAPSE_TO_MINI_PLAYER)
      .putExtra(EXTRA_PENDING_VIDEO_ID, currentVideoId)
      .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    finish()
  }
  internal class PlaybackVideoFrame(context: android.content.Context) : FrameLayout(context) {
    var onMotionEvent: ((MotionEvent, Int, Int) -> Unit)? = null
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
      onMotionEvent?.invoke(event, width, height)
      return super.dispatchTouchEvent(event)
    }
  }
  private fun updatePipAspectRatio(size: VideoSize) {
    if (size.width <= 0 || size.height <= 0) return
    val width = (size.width * size.pixelWidthHeightRatio).roundToInt(); val ratio = width.toFloat() / size.height
    if (ratio in (1f / 2.39f)..2.39f) setPictureInPictureParams(PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(width, size.height)).build())
  }
  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    if (!collapsingToMiniPlayer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode) enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(16, 9)).build())
  }
  override fun onPictureInPictureModeChanged(pip: Boolean, config: Configuration) {
    super.onPictureInPictureModeChanged(pip, config)
    detailsPanel.visibility = if (pip || isFullscreen || config.orientation == Configuration.ORIENTATION_LANDSCAPE) View.GONE else View.VISIBLE
    resizeVideoFrame(config.orientation == Configuration.ORIENTATION_LANDSCAPE)
    qualityButton.visibility = if (pip) View.GONE else View.VISIBLE; fullscreenButton.visibility = if (pip) View.GONE else View.VISIBLE; playerView.useController = !pip
  }
  private fun showQualityOptions() {
    val player = playerView.player ?: return; val id = player.currentMediaItem?.mediaId?.takeIf(String::isNotBlank) ?: return
    val request = ++qualityRequestGeneration
    qualityButton.isEnabled = false
    executor.execute { runCatching { NewPipeVideoRepository.getVideoDetails(id) }.onSuccess { details -> runOnUiThread {
      if (isDestroyed || request != qualityRequestGeneration || (playerView.player as? Player)?.currentMediaItem?.mediaId != id) return@runOnUiThread
      qualityButton.isEnabled = true; val qualities = details.optJSONArray("qualities") ?: return@runOnUiThread
      if (selectedQualityId.isBlank()) selectedQualityId = details.optString("defaultQualityId")
      val ids = mutableListOf<String>(); val labels = mutableListOf<String>()
      for (i in 0 until qualities.length()) { val item = qualities.optJSONObject(i) ?: continue; val qid = item.optString("id"); val label = item.optString("label")
        if (qid.isNotBlank() && label.isNotBlank()) { ids += qid; labels += if (qid == selectedQualityId) "$label  ✓" else label } }
      if (ids.isEmpty()) return@runOnUiThread
      AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Chất lượng video").setItems(labels.toTypedArray()) { _, index ->
        if (request != qualityRequestGeneration || (playerView.player as? Player)?.currentMediaItem?.mediaId != id) return@setItems
        val qid = ids[index]; if (qid == selectedQualityId) return@setItems
        val qualityRequest = ++qualityRequestGeneration
        val resumePosition = player.currentPosition
        val resumePlayback = player.playWhenReady
        qualityButton.isEnabled = false
        executor.execute { runCatching { NewPipeVideoRepository.resolveVideo(id, qid) }.onSuccess { stream -> runOnUiThread {
          if (isDestroyed || qualityRequest != qualityRequestGeneration || (playerView.player as? Player)?.currentMediaItem?.mediaId != id) return@runOnUiThread
          selectedQualityId = stream.qualityId; qualityButton.isEnabled = true; PlaybackService.changeQuality(this, stream, id, resumePosition, resumePlayback)
        } }.onFailure { runOnUiThread { qualityButton.isEnabled = true; Toast.makeText(this, "Không đổi được chất lượng video", Toast.LENGTH_SHORT).show() } } }
      }.setNegativeButton("Hủy", null).show()
    } }.onFailure { runOnUiThread { qualityButton.isEnabled = true; Toast.makeText(this, "Không tải được chất lượng video", Toast.LENGTH_SHORT).show() } } }
  }
}
