package com.tubelite

internal data class PlaybackOpenRequest(val videoId: String, val qualityId: String) {
  companion object {
    fun parse(videoId: String, qualityId: String): PlaybackOpenRequest? =
      if (videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) PlaybackOpenRequest(videoId, qualityId) else null
  }
}
