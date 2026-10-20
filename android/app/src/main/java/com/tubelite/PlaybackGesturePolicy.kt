package com.tubelite

internal enum class PlaybackTouchRegion { VIDEO, CONTROLS, SEEK_BAR, OTHER }
internal enum class PlaybackGestureDecision { NONE, COLLAPSE }

internal object PlaybackGesturePolicy {
  fun evaluate(startX: Float, startY: Float, endX: Float, endY: Float, region: PlaybackTouchRegion, threshold: Float): PlaybackGestureDecision {
    val deltaX = endX - startX
    val deltaY = endY - startY
    return if (region == PlaybackTouchRegion.VIDEO && deltaY >= threshold && deltaY > kotlin.math.abs(deltaX) * 1.25f) {
      PlaybackGestureDecision.COLLAPSE
    } else PlaybackGestureDecision.NONE
  }
}
