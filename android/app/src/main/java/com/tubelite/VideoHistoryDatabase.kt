package com.tubelite

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "videos")
data class VideoHistoryEntity(
  @PrimaryKey val videoId: String,
  val title: String,
  val channelTitle: String,
  val thumbnailUrl: String?,
  val durationMs: Long,
  val positionMs: Long,
  val lastWatchedAt: Long,
  val isFavorite: Boolean,
  val inHistory: Boolean,
)

@Dao
interface VideoHistoryDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun upsert(video: VideoHistoryEntity)

  @Query("SELECT * FROM videos WHERE inHistory = 1 ORDER BY lastWatchedAt DESC")
  fun getHistory(): List<VideoHistoryEntity>

  @Query("SELECT * FROM videos WHERE isFavorite = 1 ORDER BY lastWatchedAt DESC")
  fun getFavorites(): List<VideoHistoryEntity>

  @Query("SELECT * FROM videos WHERE videoId = :videoId LIMIT 1")
  fun getById(videoId: String): VideoHistoryEntity?

  @Query("UPDATE videos SET isFavorite = :enabled WHERE videoId = :videoId")
  fun updateFavorite(videoId: String, enabled: Boolean): Int

  @Query("UPDATE videos SET inHistory = 0, positionMs = 0 WHERE inHistory = 1")
  fun clearHistory()

  @Query("DELETE FROM videos WHERE inHistory = 0 AND isFavorite = 0")
  fun deleteUnreferenced()
}

@Database(entities = [VideoHistoryEntity::class], version = 1, exportSchema = false)
abstract class VideoHistoryDatabase : RoomDatabase() {
  abstract fun videoHistoryDao(): VideoHistoryDao

  companion object {
    @Volatile private var instance: VideoHistoryDatabase? = null
    fun get(context: Context): VideoHistoryDatabase = instance ?: synchronized(this) {
      instance ?: Room.databaseBuilder(
        context.applicationContext, VideoHistoryDatabase::class.java, "tubelite-history.db",
      ).build().also { instance = it }
    }
  }
}

internal object VideoHistoryStore {
  fun recordVideo(context: Context, video: VideoMetadata, positionMs: Long = 0L) {
    val dao = VideoHistoryDatabase.get(context).videoHistoryDao()
    val existing = dao.getById(video.videoId)
    dao.upsert(
      VideoHistoryEntity(
        video.videoId, video.title, video.channelTitle, video.thumbnailUrl, video.durationMs,
        positionMs.coerceAtLeast(0), System.currentTimeMillis(), existing?.isFavorite ?: false, true,
      ),
    )
  }

  fun savePosition(context: Context, videoId: String, positionMs: Long) {
    val dao = VideoHistoryDatabase.get(context).videoHistoryDao()
    val current = dao.getById(videoId) ?: return
    dao.upsert(current.copy(positionMs = positionMs.coerceAtLeast(0), lastWatchedAt = System.currentTimeMillis()))
  }

  fun setFavorite(context: Context, videoId: String, enabled: Boolean) {
    val dao = VideoHistoryDatabase.get(context).videoHistoryDao()
    if (dao.updateFavorite(videoId, enabled) == 0 && enabled) {
      dao.upsert(VideoHistoryEntity(videoId, "Video $videoId", "", null, 0, 0, System.currentTimeMillis(), true, false))
    }
    dao.deleteUnreferenced()
  }

  fun clearHistory(context: Context) {
    val dao = VideoHistoryDatabase.get(context).videoHistoryDao()
    dao.clearHistory()
    dao.deleteUnreferenced()
  }
}

internal data class VideoMetadata(
  val videoId: String,
  val title: String,
  val channelTitle: String,
  val thumbnailUrl: String?,
  val durationMs: Long,
)
