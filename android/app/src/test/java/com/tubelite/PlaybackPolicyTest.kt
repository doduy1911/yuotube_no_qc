package com.tubelite

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPolicyTest {
  @Test
  fun choosesHighestPlayableQualityAtOrBelow720p() {
    val selected = PlaybackPolicy.chooseDefaultQuality(
      listOf(
        PlaybackQuality("1080", 1080),
        PlaybackQuality("480", 480),
        PlaybackQuality("720", 720),
        PlaybackQuality("360", 360, playable = false),
      ),
    )

    assertEquals("720", selected?.id)
  }

  @Test
  fun fallsBackToLowestAvailableWhenEveryQualityExceeds720p() {
    val selected = PlaybackPolicy.chooseDefaultQuality(
      listOf(PlaybackQuality("1440", 1440), PlaybackQuality("1080", 1080)),
    )

    assertEquals("1080", selected?.id)
  }

  @Test
  fun triesRefreshingAnExpiredHttpSourceOnlyOnce() {
    assertTrue(
      PlaybackPolicy.shouldRefreshSource(
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        alreadyRetried = false,
      ),
    )
    assertFalse(
      PlaybackPolicy.shouldRefreshSource(
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        alreadyRetried = true,
      ),
    )
    assertFalse(
      PlaybackPolicy.shouldRefreshSource(
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        alreadyRetried = false,
      ),
    )
  }
}
