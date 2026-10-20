package com.tubelite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackOpenRequestTest {
  @Test fun acceptsValidVideoAndPreservesQuality() {
    val request = PlaybackOpenRequest.parse("dQw4w9WgXcQ", "1080p")
    assertEquals("dQw4w9WgXcQ", request?.videoId)
    assertEquals("1080p", request?.qualityId)
  }

  @Test fun rejectsMalformedVideoIds() {
    assertNull(PlaybackOpenRequest.parse("invalid", ""))
  }
}
