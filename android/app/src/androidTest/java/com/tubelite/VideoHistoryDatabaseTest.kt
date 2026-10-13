package com.tubelite

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoHistoryDatabaseTest {
  private lateinit var database: VideoHistoryDatabase
  private lateinit var dao: VideoHistoryDao

  @Before fun createDatabase() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.deleteDatabase("test-history")
    database = Room.databaseBuilder(
      context, VideoHistoryDatabase::class.java, "test-history",
    ).allowMainThreadQueries().build()
    dao = database.videoHistoryDao()
  }

  @After fun closeDatabase() = database.close()

  @Test fun upsertUsesVideoIdAndKeepsOneRow() {
    dao.upsert(video("abcdefghijk", title = "first"))
    dao.upsert(video("abcdefghijk", title = "updated"))
    assertEquals(1, dao.getHistory().size)
    assertEquals("updated", dao.getHistory().single().title)
  }

  @Test fun recordSurvivesDatabaseReopen() {
    dao.upsert(video("abcdefghijk", positionMs = 45_000))
    database.close()
    database = Room.databaseBuilder(
      ApplicationProvider.getApplicationContext<Context>(), VideoHistoryDatabase::class.java, "test-history",
    ).allowMainThreadQueries().build()
    assertEquals(45_000L, database.videoHistoryDao().getHistory().single().positionMs)
  }

  @Test fun clearingHistoryKeepsFavoriteButRemovesOrdinaryRows() {
    dao.upsert(video("abcdefghijk", favorite = true))
    dao.upsert(video("lmnopqrstuv", favorite = false))
    dao.clearHistory()
    dao.deleteUnreferenced()
    assertTrue(dao.getHistory().isEmpty())
    assertEquals(listOf("abcdefghijk"), dao.getFavorites().map { it.videoId })
    assertFalse(dao.getFavorites().single().inHistory)
  }

  private fun video(id: String, title: String = id, positionMs: Long = 0, favorite: Boolean = false) =
    VideoHistoryEntity(id, title, "Channel", null, 120_000, positionMs, 100L, favorite, true)
}
