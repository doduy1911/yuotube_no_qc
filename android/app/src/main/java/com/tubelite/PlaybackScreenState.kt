package com.tubelite

internal data class PlaybackScreenState(
  val videoId: String = "",
  val title: String? = null,
  val channelTitle: String? = null,
  val status: String = "idle",
  val positionMs: Long = 0L,
  val autoplayEnabled: Boolean = true,
  val recommendations: List<String> = emptyList(),
  val recommendationsLoading: Boolean = false,
  val error: String? = null,
) {
  fun selecting(newVideoId: String) = copy(
    videoId = newVideoId,
    title = null,
    channelTitle = null,
    status = "loading",
    recommendations = emptyList(),
    recommendationsLoading = true,
    error = null,
  )

  fun failed(message: String) = copy(status = "error", recommendationsLoading = false, error = message)
}

internal class PlaybackRequestGeneration {
  private var generation = 0L
  private var videoId = ""
  fun next(id: String): Long { videoId = id; generation += 1; return generation }
  fun isCurrent(request: Long, id: String) = request == generation && id == videoId
}
