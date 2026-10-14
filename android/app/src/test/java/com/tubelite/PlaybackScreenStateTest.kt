package com.tubelite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackScreenStateTest {
  @Test fun requestGenerationRejectsOldResultsAndKeepsLatestSelection() {
    val requests = PlaybackRequestGeneration()
    val first = requests.next("video000000")
    val latest = requests.next("video000001")
    assertFalse(requests.isCurrent(first, "video000000"))
    assertTrue(requests.isCurrent(latest, "video000001"))
  }

  @Test fun selectingNewVideoClearsOldRecommendationMetadataImmediately() {
    val state = PlaybackScreenState(videoId = "video000000", title = "Old", recommendations = listOf("video000002"))
    val changed = state.selecting("video000001")
    assertEquals("video000001", changed.videoId)
    assertNull(changed.title)
    assertTrue(changed.recommendations.isEmpty())
    assertTrue(changed.recommendationsLoading)
  }

  @Test fun snapshotIncludesPlaybackAndRecommendationState() {
    val state = PlaybackScreenState(videoId = "video000000", title = "Title", status = "paused", positionMs = 1234L)
    assertEquals("paused", state.status)
    assertEquals(1234L, state.positionMs)
  }
}
