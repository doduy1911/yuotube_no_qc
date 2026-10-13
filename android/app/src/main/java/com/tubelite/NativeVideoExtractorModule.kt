package com.tubelite

import android.content.Intent
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReactApplicationContext
import java.util.concurrent.Executors

class NativeVideoExtractorModule(
  reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {
  private val executor = Executors.newSingleThreadExecutor()

  override fun getName() = NAME

  @ReactMethod
  fun getVideoDetails(videoId: String, promise: Promise) {
    executor.execute {
      try {
        promise.resolve(NewPipeVideoRepository.getVideoDetails(videoId).toString())
      } catch (error: Throwable) {
        promise.reject("EXTRACTION_FAILED", error.message ?: "Không lấy được thông tin video.", error)
      }
    }
  }

  @ReactMethod
  fun openVideo(videoId: String, qualityId: String, promise: Promise) {
    executor.execute {
      try {
        val (streamUrl, title) = NewPipeVideoRepository.openVideo(videoId, qualityId)
        val context = reactApplicationContext
        PlaybackService.start(context, streamUrl, title)
        context.startActivity(
          Intent(context, PlaybackActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        promise.resolve(true)
      } catch (error: Throwable) {
        promise.reject("PLAYBACK_FAILED", error.message ?: "Không phát được video này.", error)
      }
    }
  }

  companion object {
    const val NAME = "NativeVideoExtractor"
  }
}
