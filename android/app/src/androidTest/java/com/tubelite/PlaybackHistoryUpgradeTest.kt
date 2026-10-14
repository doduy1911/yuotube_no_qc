package com.tubelite

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackHistoryUpgradeTest {
  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
  private val videoId = "upgrade00001"

  @Test fun seedV1HistoryAndFavorite() {
    VideoHistoryStore.recordVideo(context, VideoMetadata(videoId, "Upgrade test video", "TubeLite test", null, 180_000L), 55_000L)
    VideoHistoryStore.setFavorite(context, videoId, true)
    val row = VideoHistoryDatabase.get(context).videoHistoryDao().getById(videoId)!!
    assertEquals(55_000L, row.positionMs)
    assertTrue(row.isFavorite)
  }

  @Test fun v2InstallPreservesHistoryPositionAndFavorite() {
    val row = VideoHistoryDatabase.get(context).videoHistoryDao().getById(videoId)!!
    assertEquals("Upgrade test video", row.title)
    assertEquals(55_000L, row.positionMs)
    assertTrue(row.isFavorite)
    assertTrue(row.inHistory)
    assertTrue(PlaybackSettings(context).isAutoplayEnabled())
  }
}
