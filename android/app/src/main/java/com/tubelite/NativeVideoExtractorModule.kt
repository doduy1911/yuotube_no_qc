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
  private val trendingRepository by lazy {
    TrendingMusicRepository(YouTubeTrendingSource(), SharedPreferencesTrendingCache(reactApplicationContext))
  }

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
        val resolved = NewPipeVideoRepository.resolveVideo(videoId, qualityId)
        val context = reactApplicationContext
        val row = VideoHistoryDatabase.get(context).videoHistoryDao().getById(videoId)
        val resumePosition = row?.positionMs?.takeIf { row.durationMs == 0L || it < row.durationMs - 10_000L } ?: 0L
        VideoHistoryStore.recordVideo(context, resolved.metadata, resumePosition)
        PlaybackService.start(context, resolved, videoId, resumePosition)
        context.startActivity(
          Intent(context, PlaybackActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        promise.resolve(true)
      } catch (error: Throwable) {
        promise.reject("PLAYBACK_FAILED", error.message ?: "Không phát được video này.", error)
      }
    }
  }

  @ReactMethod
  fun searchVideos(query: String, pageToken: String, promise: Promise) {
    executor.execute {
      try {
        promise.resolve(YouTubeSearchRepository.search(query, pageToken).toString())
      } catch (error: YouTubeSearchException) {
        promise.reject(error.code, error.message, error)
      } catch (error: Throwable) {
        promise.reject("API_NETWORK_ERROR", error.message ?: "Không kết nối được YouTube API.", error)
      }
    }
  }

  @ReactMethod
  fun getTrendingMusic(section: String, forceRefresh: Boolean, promise: Promise) {
    executor.execute {
      runCatching { trendingRepository.get(section, forceRefresh).toJson().toString() }
        .onSuccess { promise.resolve(it) }
        .onFailure { error ->
          if (error is YouTubeSearchException) promise.reject(error.code, error.message, error)
          else promise.reject("API_NETWORK_ERROR", error.message ?: "Không tải được nhạc thịnh hành.", error)
        }
    }
  }

  @ReactMethod
  fun getHistory(promise: Promise) = getLibraryRows(favorites = false, promise)

  @ReactMethod
  fun getFavorites(promise: Promise) = getLibraryRows(favorites = true, promise)

  @ReactMethod
  fun setFavorite(videoId: String, enabled: Boolean, promise: Promise) {
    executor.execute {
      runCatching { VideoHistoryStore.setFavorite(reactApplicationContext, videoId, enabled) }
        .onSuccess { promise.resolve(true) }
        .onFailure { promise.reject("STORAGE_ERROR", "Không lưu được video yêu thích.", it) }
    }
  }

  @ReactMethod
  fun clearHistory(promise: Promise) {
    executor.execute {
      runCatching { VideoHistoryStore.clearHistory(reactApplicationContext) }
        .onSuccess { promise.resolve(true) }
        .onFailure { promise.reject("STORAGE_ERROR", "Không xóa được lịch sử xem.", it) }
    }
  }

  private fun getLibraryRows(favorites: Boolean, promise: Promise) {
    executor.execute {
      runCatching {
        val dao = VideoHistoryDatabase.get(reactApplicationContext).videoHistoryDao()
        val rows = if (favorites) dao.getFavorites() else dao.getHistory()
        org.json.JSONArray().also { array -> rows.forEach { row ->
          array.put(org.json.JSONObject()
            .put("videoId", row.videoId).put("title", row.title).put("channelTitle", row.channelTitle)
            .put("thumbnailUrl", row.thumbnailUrl ?: org.json.JSONObject.NULL)
            .put("durationMs", row.durationMs).put("positionMs", row.positionMs)
            .put("lastWatchedAt", row.lastWatchedAt).put("isFavorite", row.isFavorite))
        } }.toString()
      }.onSuccess { promise.resolve(it) }
        .onFailure { promise.reject("STORAGE_ERROR", "Không tải được thư viện.", it) }
    }
  }

  companion object {
    const val NAME = "NativeVideoExtractor"
  }
}
