package com.tubelite

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewPipeRelatedVideoSmokeTest {
  @Test
  fun extractsRelatedVideosFromPublicYouTubeVideo() {
    val related = NewPipeRelatedVideoSource().getRelated("dQw4w9WgXcQ")
    assertTrue("Expected related items from the public YouTube video", related.isNotEmpty())
    assertTrue(related.all { it.video.videoId.matches(Regex("[A-Za-z0-9_-]{11}")) })
  }
}
