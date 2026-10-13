package com.tubelite

import androidx.media3.common.PlaybackException

internal data class PlaybackQuality(
  val id: String,
  val height: Int?,
  val playable: Boolean = true,
)

internal object PlaybackPolicy {
  fun chooseDefaultQuality(qualities: List<PlaybackQuality>): PlaybackQuality? {
    val playable = qualities.filter { it.playable }
    val withHeight = playable.filter { it.height != null }
    return withHeight.filter { it.height!! <= 720 }.maxByOrNull { it.height!! }
      ?: withHeight.minByOrNull { it.height!! }
      ?: playable.firstOrNull()
  }

  fun shouldRefreshSource(errorCode: Int, alreadyRetried: Boolean): Boolean =
    errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && !alreadyRetried
}
