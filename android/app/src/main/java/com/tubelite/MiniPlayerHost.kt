package com.tubelite

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture

internal class MiniPlayerHost(private val activity: MainActivity) {
  private var state = MiniPlayerHostState()
  private var overlay: FrameLayout? = null
  private var playerView: PlayerView? = null
  private var titleView: TextView? = null
  private var statusView: TextView? = null
  private var playButton: TextView? = null
  private var controller: MediaController? = null
  private var controllerFuture: ListenableFuture<MediaController>? = null
  private var playerListener: Player.Listener? = null

  val isVisible: Boolean get() = overlay?.parent != null
  val isConnected: Boolean get() = controller?.isConnected == true
  val currentVideoId: String get() = state.videoId

  fun show(videoId: String) {
    val next = state.collapse(videoId)
    if (!next.shouldAttach) {
      state = next
      hideOverlay()
      return
    }
    state = next
    if (!state.shouldAttachAgain && isVisible) return
    hideOverlay()
    state = state.attached()
    attachOverlay()
    connectController()
  }

  fun hideForExpansion() {
    state = state.expand()
    hideOverlay()
  }

  fun close() {
    val closeCommand = controller?.sendCustomCommand(
      SessionCommand(PlaybackService.ACTION_CLOSE_PLAYBACK, android.os.Bundle.EMPTY), android.os.Bundle.EMPTY,
    )
    state = state.close()
    hideOverlay(releaseController = closeCommand == null)
    closeCommand?.addListener({ releaseController() }, activity.mainExecutor)
    PlaybackUiEvents.emit(activity, PlaybackUiEvents.MINI_PLAYER_CLOSED)
  }

  fun release() = hideOverlay()

  private fun attachOverlay() {
    val root = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
    val mini = FrameLayout(activity).apply {
      setBackgroundColor(Color.rgb(28, 28, 28))
      elevation = dp(12).toFloat()
      isClickable = true
    }
    val row = LinearLayout(activity).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(8), dp(5), dp(8), dp(5))
    }
    val video = PlayerView(activity).apply {
      useController = false
      setBackgroundColor(Color.BLACK)
      setShutterBackgroundColor(Color.BLACK)
      resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
    playerView = video
    row.addView(video, LinearLayout.LayoutParams(dp(116), dp(66)))

    val details = LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(10), 0, dp(4), 0)
    }
    titleView = text(13f, Color.WHITE).apply { maxLines = 1; typeface = Typeface.DEFAULT_BOLD; text = "TubeLite" }
    statusView = text(11f, Color.LTGRAY).apply { maxLines = 1; visibility = View.VISIBLE; text = "Đang tải video…" }
    details.addView(titleView)
    details.addView(statusView)
    row.addView(details, LinearLayout.LayoutParams(0, -1, 1f))

    playButton = text(22f, Color.WHITE).apply {
      gravity = Gravity.CENTER
      contentDescription = "Phát hoặc tạm dừng"
      setOnClickListener {
        val current = controller ?: return@setOnClickListener
        if (current.isPlaying) current.pause() else current.play()
      }
    }
    row.addView(playButton, LinearLayout.LayoutParams(dp(44), -1))
    val closeButton = text(24f, Color.WHITE).apply {
      text = "×"
      gravity = Gravity.CENTER
      contentDescription = "Đóng mini-player"
      setOnClickListener { close() }
    }
    row.addView(closeButton, LinearLayout.LayoutParams(dp(38), -1))
    mini.addView(row, FrameLayout.LayoutParams(-1, -1))
    mini.setOnClickListener { expand() }
    video.setOnClickListener { expand() }

    root.addView(mini, FrameLayout.LayoutParams(-1, dp(78), Gravity.BOTTOM).apply { bottomMargin = dp(66) })
    overlay = mini
  }

  private fun connectController() {
    val token = SessionToken(activity, android.content.ComponentName(activity, PlaybackService::class.java))
    val future = MediaController.Builder(activity, token).setListener(object : MediaController.Listener {
      override fun onExtrasChanged(controller: MediaController, extras: android.os.Bundle) = render(controller, extras)
    }).buildAsync()
    controllerFuture = future
    future.addListener({
      if (overlay == null) return@addListener
      runCatching { future.get() }.onSuccess { connected ->
        controller = connected
        playerView?.player = connected
        val listener = object : Player.Listener {
          override fun onIsPlayingChanged(isPlaying: Boolean) = render(connected, connected.sessionExtras)
          override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = render(connected, connected.sessionExtras)
          override fun onPlaybackStateChanged(playbackState: Int) = render(connected, connected.sessionExtras)
        }
        playerListener = listener
        connected.addListener(listener)
        render(connected, connected.sessionExtras)
      }
    }, activity.mainExecutor)
  }

  private fun render(controller: MediaController, extras: android.os.Bundle?) {
    if (overlay == null) return
    state = state.selectVideo(controller.currentMediaItem?.mediaId.orEmpty())
    val mediaTitle = controller.mediaMetadata.title?.toString().orEmpty()
    titleView?.text = mediaTitle.ifBlank { "TubeLite" }
    playButton?.text = if (controller.isPlaying) "Ⅱ" else "▶"
    val status = extras?.getString("status").orEmpty()
    when (status) {
      "loading" -> showStatus("Đang tải video…", retry = false)
      "error" -> showStatus(extras?.getString("error") ?: "Không tải được video", retry = true)
      else -> {
        val channel = controller.mediaMetadata.artist?.toString().orEmpty()
        showStatus(channel, retry = false)
      }
    }
  }

  private fun showStatus(message: String, retry: Boolean) {
    statusView?.text = message
    statusView?.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
    playButton?.apply {
      contentDescription = if (retry) "Thử lại video" else "Phát hoặc tạm dừng"
      text = if (retry) "↻" else if (controller?.isPlaying == true) "Ⅱ" else "▶"
      setOnClickListener {
        val current = controller ?: return@setOnClickListener
        if (retry) current.sendCustomCommand(
          SessionCommand(PlaybackService.ACTION_SELECT_VIDEO, android.os.Bundle.EMPTY),
          android.os.Bundle().apply { putString("videoId", state.videoId) },
        ) else if (current.isPlaying) current.pause() else current.play()
      }
    }
  }

  private fun expand() {
    val videoId = controller?.currentMediaItem?.mediaId?.takeIf(String::isNotBlank) ?: state.videoId
    if (videoId.isBlank()) return
    hideForExpansion()
    activity.startActivity(Intent(activity, PlaybackActivity::class.java).putExtra(PlaybackActivity.EXTRA_PENDING_VIDEO_ID, videoId))
  }

  private fun hideOverlay(releaseController: Boolean = true) {
    overlay?.let { view -> (view.parent as? FrameLayout)?.removeView(view) }
    overlay = null
    playerView?.player = null
    playerView = null
    titleView = null
    statusView = null
    playButton = null
    if (releaseController) releaseController()
  }

  private fun releaseController() {
    controller?.let { current -> playerListener?.let(current::removeListener) }
    playerListener = null
    controller = null
    controllerFuture?.let(MediaController::releaseFuture)
    controllerFuture = null
  }

  private fun text(size: Float, color: Int) = TextView(activity).apply {
    this.textSize = size
    setTextColor(color)
  }

  private fun dp(value: Int) = (activity.resources.displayMetrics.density * value).toInt()
}
