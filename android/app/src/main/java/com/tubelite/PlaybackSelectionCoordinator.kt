package com.tubelite

import java.util.concurrent.Executor

internal class PlaybackSelectionCoordinator<T>(
  private val executor: Executor,
) {
  private var generation = 0L
  private var videoId = ""

  @Synchronized
  fun select(
    selectedVideoId: String,
    qualityId: String,
    resolver: (String, String) -> T,
    onState: (PlaybackScreenState) -> Unit,
    onSuccess: (T) -> Unit,
    onFailure: (Throwable) -> Unit = {},
  ): Long {
    videoId = selectedVideoId
    val request = ++generation
    onState(PlaybackScreenState(videoId = selectedVideoId).selecting(selectedVideoId))
    executor.execute {
      val result = runCatching { resolver(selectedVideoId, qualityId) }
      synchronized(this) {
        if (request != generation || selectedVideoId != videoId) return@execute
      }
      result.onSuccess(onSuccess).onFailure(onFailure)
    }
    return request
  }

  @Synchronized
  fun isCurrent(request: Long, selectedVideoId: String): Boolean =
    request == generation && selectedVideoId == videoId
}
