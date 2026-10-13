package com.tubelite

import android.app.Activity
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.roundToInt
import java.util.concurrent.Executors

class PlaybackActivity : Activity() {
  private lateinit var playerView: PlayerView
  private lateinit var qualityButton: TextView
  private val extractorExecutor = Executors.newSingleThreadExecutor()
  private var selectedQualityId = ""
  private var controllerFuture: ListenableFuture<MediaController>? = null
  private val videoSizeListener = object : Player.Listener {
    override fun onVideoSizeChanged(videoSize: VideoSize) {
      updatePipAspectRatio(videoSize)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    playerView = PlayerView(this).apply {
      useController = true
      keepScreenOn = true
      setBackgroundColor(Color.BLACK)
      setShutterBackgroundColor(Color.BLACK)
    }
    window.decorView.setBackgroundColor(Color.BLACK)
    qualityButton = TextView(this).apply {
      text = "⚙"
      textSize = 22f
      setTextColor(Color.WHITE)
      setPadding(18, 12, 18, 12)
      setBackgroundColor(Color.argb(150, 20, 20, 20))
      contentDescription = "Chọn chất lượng video"
      setOnClickListener { showQualityOptions() }
    }
    val root = FrameLayout(this).apply {
      setBackgroundColor(Color.BLACK)
      addView(playerView, FrameLayout.LayoutParams(-1, -1))
      addView(
        qualityButton,
        FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
          topMargin = 100
          marginEnd = 20
        },
      )
    }
    setContentView(root)

    val token = SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
    val future = MediaController.Builder(this, token).buildAsync()
    controllerFuture = future
    future.addListener({
      if (!isDestroyed) {
        val controller = future.get()
        playerView.player = controller
        controller.addListener(videoSizeListener)
      }
    }, mainExecutor)
  }

  override fun onDestroy() {
    (playerView.player as? MediaController)?.removeListener(videoSizeListener)
    playerView.player = null
    controllerFuture?.let { MediaController.releaseFuture(it) }
    controllerFuture = null
    extractorExecutor.shutdownNow()
    super.onDestroy()
  }

  private fun updatePipAspectRatio(videoSize: VideoSize) {
    if (videoSize.width <= 0 || videoSize.height <= 0) return
    val width = (videoSize.width * videoSize.pixelWidthHeightRatio).roundToInt()
    val height = videoSize.height
    val ratio = width.toFloat() / height
    if (ratio !in (1f / 2.39f)..2.39f) return
    setPictureInPictureParams(
      PictureInPictureParams.Builder()
        .setAspectRatio(Rational(width, height))
        .build(),
    )
  }

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode) {
      enterPictureInPictureMode(
        PictureInPictureParams.Builder()
          .setAspectRatio(Rational(16, 9))
          .build(),
      )
    }
  }

  override fun onPictureInPictureModeChanged(
    isInPictureInPictureMode: Boolean,
    newConfig: Configuration,
  ) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    qualityButton.visibility = if (isInPictureInPictureMode) android.view.View.GONE else android.view.View.VISIBLE
    playerView.useController = !isInPictureInPictureMode
  }

  private fun showQualityOptions() {
    val item = playerView.player?.currentMediaItem
    val videoId = item?.mediaId?.takeIf(String::isNotBlank)
    if (videoId == null) return
    qualityButton.isEnabled = false
    extractorExecutor.execute {
      runCatching { NewPipeVideoRepository.getVideoDetails(videoId) }
        .onSuccess { details ->
          runOnUiThread {
            qualityButton.isEnabled = true
            val qualities = details.optJSONArray("qualities") ?: return@runOnUiThread
            if (selectedQualityId.isBlank()) selectedQualityId = details.optString("defaultQualityId")
            val ids = mutableListOf<String>()
            val labels = mutableListOf<String>()
            for (index in 0 until qualities.length()) {
              val item = qualities.optJSONObject(index) ?: continue
              val id = item.optString("id")
              val label = item.optString("label")
              if (id.isNotBlank() && label.isNotBlank()) {
                ids += id
                labels += if (id == selectedQualityId) "$label  ✓" else label
              }
            }
            if (ids.isEmpty()) return@runOnUiThread
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
              .setTitle("Chất lượng video")
              .setItems(labels.toTypedArray()) { _, index ->
                val qualityId = ids[index]
                if (qualityId == selectedQualityId) return@setItems
                qualityButton.isEnabled = false
                extractorExecutor.execute {
                  runCatching { NewPipeVideoRepository.resolveVideo(videoId, qualityId) }
                    .onSuccess { resolved ->
                      runOnUiThread {
                        selectedQualityId = resolved.qualityId
                        qualityButton.isEnabled = true
                        PlaybackService.changeQuality(this, resolved, videoId)
                      }
                    }
                    .onFailure {
                      runOnUiThread {
                        qualityButton.isEnabled = true
                        android.widget.Toast.makeText(this, "Không đổi được chất lượng video", android.widget.Toast.LENGTH_SHORT).show()
                      }
                    }
                }
              }
              .setNegativeButton("Hủy", null)
              .show()
          }
        }
        .onFailure {
          runOnUiThread {
            qualityButton.isEnabled = true
            android.widget.Toast.makeText(this, "Không tải được chất lượng video", android.widget.Toast.LENGTH_SHORT).show()
          }
        }
    }
  }
}
