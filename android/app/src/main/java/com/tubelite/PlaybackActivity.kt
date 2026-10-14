package com.tubelite

import android.app.Activity
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
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
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class PlaybackActivity : Activity() {
  private lateinit var playerView: PlayerView
  private lateinit var qualityButton: TextView
  private lateinit var fullscreenButton: TextView
  private lateinit var detailsPanel: LinearLayout
  private lateinit var titleView: TextView
  private lateinit var channelView: TextView
  private lateinit var favoriteButton: TextView
  private lateinit var recommendationsList: LinearLayout
  private lateinit var recommendationsHeading: TextView
  private lateinit var recommendationsStatus: TextView
  private lateinit var rootLayout: LinearLayout
  private val executor = Executors.newSingleThreadExecutor()
  private val requests = PlaybackRequestGeneration()
  private var selectedQualityId = ""
  private var controllerFuture: ListenableFuture<MediaController>? = null
  private var isFullscreen = false
  private var currentVideoId = ""
  private val playerListener = object : Player.Listener {
    override fun onVideoSizeChanged(videoSize: VideoSize) = updatePipAspectRatio(videoSize)
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refreshFromController()
    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = refreshMetadata()
    override fun onPlaybackStateChanged(playbackState: Int) = updatePlaybackStatus()
    override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlaybackStatus()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.decorView.setBackgroundColor(Color.rgb(10, 10, 10))
    window.setStatusBarColor(Color.rgb(10, 10, 10)); window.setNavigationBarColor(Color.rgb(10, 10, 10))
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    buildLayout()
    val token = SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
    val future = MediaController.Builder(this, token).buildAsync(); controllerFuture = future
    future.addListener({ if (!isDestroyed) runCatching { future.get() }.onSuccess { controller ->
      playerView.player = controller; controller.addListener(playerListener); refreshFromController()
    } }, mainExecutor)
  }

  private fun buildLayout() {
    playerView = PlayerView(this).apply { useController = true; setBackgroundColor(Color.BLACK); setShutterBackgroundColor(Color.BLACK); resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT }
    qualityButton = overlay("⚙", "Chọn chất lượng video") { showQualityOptions() }
    fullscreenButton = overlay("⛶", "Toàn màn hình") { toggleFullscreen() }
    val video = FrameLayout(this).apply {
      setBackgroundColor(Color.BLACK); addView(playerView, FrameLayout.LayoutParams(-1, -1))
      addView(qualityButton, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = dp(8); marginEnd = dp(8) })
      addView(fullscreenButton, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { bottomMargin = dp(8); marginEnd = dp(8) })
    }
    rootLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(10, 10, 10)) }
    rootLayout.addView(video, LinearLayout.LayoutParams(-1, 0, 0.5625f))
    detailsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
    titleView = text(19f, Color.WHITE).apply { maxLines = 2 }
    channelView = text(14f, Color.LTGRAY).apply { setPadding(0, dp(4), 0, dp(8)) }
    favoriteButton = text(15f, Color.WHITE).apply { text = "♡  Yêu thích"; setPadding(dp(12), dp(8), dp(12), dp(8)); setOnClickListener { toggleFavorite() } }
    recommendationsHeading = text(17f, Color.WHITE).apply { text = "Video liên quan"; setPadding(0, dp(14), 0, dp(6)) }
    recommendationsStatus = text(14f, Color.LTGRAY)
    recommendationsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    body.addView(titleView); body.addView(channelView); body.addView(favoriteButton); body.addView(recommendationsHeading); body.addView(recommendationsStatus); body.addView(recommendationsList)
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
    val req = requests.next(id); recommendationsList.removeAllViews(); recommendationsStatus.text = "Đang tải gợi ý…"
    executor.execute {
      val repo = RecommendationRepository(NewPipeRelatedVideoSource(), CachedTrendingRecommendationSource(TrendingMusicRepository(YouTubeTrendingSource(), SharedPreferencesTrendingCache(this))))
      val result = runCatching { repo.get(id) }
      runOnUiThread {
        if (isDestroyed || !requests.isCurrent(req, id)) return@runOnUiThread
        result.onSuccess { set ->
          recommendationsHeading.text = if (set.source == "trending") "Khám phá thêm" else "Video liên quan"
          recommendationsStatus.text = if (set.items.isEmpty()) set.error ?: "Chưa có video gợi ý." else ""
          set.items.forEach(::addRecommendation)
        }.onFailure { recommendationsStatus.text = "Không tải được gợi ý. Thử lại sau." }
      }
    }
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
    val req = requests.next(id); currentVideoId = id; titleView.text = "Đang mở video…"; recommendationsList.removeAllViews(); recommendationsStatus.text = "Đang tải gợi ý…"
    executor.execute { runCatching { NewPipeVideoRepository.resolveVideo(id) }.onSuccess { stream -> runOnUiThread {
      if (!isDestroyed && requests.isCurrent(req, id)) { PlaybackService.start(this, stream, id, 0L); refreshFavorite() }
    } }.onFailure { runOnUiThread { if (!isDestroyed && requests.isCurrent(req, id)) Toast.makeText(this, "Không mở được video này", Toast.LENGTH_SHORT).show() } } }
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
    isFullscreen = !isFullscreen; detailsPanel.visibility = if (isFullscreen) View.GONE else View.VISIBLE
    window.decorView.systemUiVisibility = if (isFullscreen) View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY else View.SYSTEM_UI_FLAG_VISIBLE
    fullscreenButton.text = if (isFullscreen) "⤢" else "⛶"
  }
  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    applyOrientationLayout(newConfig)
  }
  private fun applyOrientationLayout(configuration: Configuration) {
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    rootLayout.getChildAt(0).layoutParams = LinearLayout.LayoutParams(-1, 0, if (landscape) 1f else 0.5625f)
    detailsPanel.visibility = if (landscape || isFullscreen) View.GONE else View.VISIBLE
    if (landscape) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    else if (!isFullscreen) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
  }
  @Deprecated("Back returns from fullscreen before leaving playback")
  override fun onBackPressed() { if (isFullscreen) toggleFullscreen() else super.onBackPressed() }
  override fun onDestroy() {
    (playerView.player as? MediaController)?.removeListener(playerListener); playerView.player = null
    controllerFuture?.let(MediaController::releaseFuture); controllerFuture = null; executor.shutdownNow(); super.onDestroy()
  }
  private fun updatePipAspectRatio(size: VideoSize) {
    if (size.width <= 0 || size.height <= 0) return
    val width = (size.width * size.pixelWidthHeightRatio).roundToInt(); val ratio = width.toFloat() / size.height
    if (ratio in (1f / 2.39f)..2.39f) setPictureInPictureParams(PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(width, size.height)).build())
  }
  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode) enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(16, 9)).build())
  }
  override fun onPictureInPictureModeChanged(pip: Boolean, config: Configuration) {
    super.onPictureInPictureModeChanged(pip, config)
    detailsPanel.visibility = if (pip || isFullscreen) View.GONE else View.VISIBLE
    qualityButton.visibility = if (pip) View.GONE else View.VISIBLE; fullscreenButton.visibility = if (pip) View.GONE else View.VISIBLE; playerView.useController = !pip
  }
  private fun showQualityOptions() {
    val player = playerView.player ?: return; val id = player.currentMediaItem?.mediaId?.takeIf(String::isNotBlank) ?: return
    qualityButton.isEnabled = false
    executor.execute { runCatching { NewPipeVideoRepository.getVideoDetails(id) }.onSuccess { details -> runOnUiThread {
      qualityButton.isEnabled = true; val qualities = details.optJSONArray("qualities") ?: return@runOnUiThread
      if (selectedQualityId.isBlank()) selectedQualityId = details.optString("defaultQualityId")
      val ids = mutableListOf<String>(); val labels = mutableListOf<String>()
      for (i in 0 until qualities.length()) { val item = qualities.optJSONObject(i) ?: continue; val qid = item.optString("id"); val label = item.optString("label")
        if (qid.isNotBlank() && label.isNotBlank()) { ids += qid; labels += if (qid == selectedQualityId) "$label  ✓" else label } }
      if (ids.isEmpty()) return@runOnUiThread
      AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Chất lượng video").setItems(labels.toTypedArray()) { _, index ->
        val qid = ids[index]; if (qid == selectedQualityId) return@setItems
        val resumePosition = player.currentPosition
        val resumePlayback = player.playWhenReady
        qualityButton.isEnabled = false
        executor.execute { runCatching { NewPipeVideoRepository.resolveVideo(id, qid) }.onSuccess { stream -> runOnUiThread {
          selectedQualityId = stream.qualityId; qualityButton.isEnabled = true; PlaybackService.changeQuality(this, stream, id, resumePosition, resumePlayback)
        } }.onFailure { runOnUiThread { qualityButton.isEnabled = true; Toast.makeText(this, "Không đổi được chất lượng video", Toast.LENGTH_SHORT).show() } } }
      }.setNegativeButton("Hủy", null).show()
    } }.onFailure { runOnUiThread { qualityButton.isEnabled = true; Toast.makeText(this, "Không tải được chất lượng video", Toast.LENGTH_SHORT).show() } } }
  }
}
