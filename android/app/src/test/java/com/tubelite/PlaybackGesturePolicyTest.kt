package com.tubelite

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackGesturePolicyTest {
  @Test fun downwardSwipeFromVideoCollapses() {
    assertEquals(
      PlaybackGestureDecision.COLLAPSE,
      PlaybackGesturePolicy.evaluate(120f, 100f, 124f, 230f, PlaybackTouchRegion.VIDEO, 80f),
    )
  }

  @Test fun horizontalMovementDoesNotCollapse() {
    assertEquals(
      PlaybackGestureDecision.NONE,
      PlaybackGesturePolicy.evaluate(50f, 100f, 190f, 112f, PlaybackTouchRegion.VIDEO, 80f),
    )
  }

  @Test fun tapAndControlsOrSeekAreaDoNotCollapse() {
    assertEquals(
      PlaybackGestureDecision.NONE,
      PlaybackGesturePolicy.evaluate(50f, 100f, 53f, 106f, PlaybackTouchRegion.VIDEO, 80f),
    )
    assertEquals(
      PlaybackGestureDecision.NONE,
      PlaybackGesturePolicy.evaluate(50f, 100f, 50f, 230f, PlaybackTouchRegion.CONTROLS, 80f),
    )
    assertEquals(
      PlaybackGestureDecision.NONE,
      PlaybackGesturePolicy.evaluate(50f, 100f, 50f, 230f, PlaybackTouchRegion.SEEK_BAR, 80f),
    )
  }

  @Test fun gestureBelowDistanceThresholdDoesNotCollapse() {
    assertEquals(
      PlaybackGestureDecision.NONE,
      PlaybackGesturePolicy.evaluate(50f, 100f, 52f, 160f, PlaybackTouchRegion.VIDEO, 80f),
    )
  }
}
