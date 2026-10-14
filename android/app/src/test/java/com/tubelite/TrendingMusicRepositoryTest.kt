package com.tubelite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendingMusicRepositoryTest {
  @Test
  fun combinesInternationalRegionsAlternatelyDeduplicatesAndLimitsToTwenty() {
    val us = (0..11).map { video("video${it.toString().padStart(6, '0')}") }
    val gb = (0..11).map { video(if (it == 0) us[0].videoId else "brit${it.toString().padStart(7, '0')}") }
    val merged = TrendingMusicRepository.mergeInternational(us, gb)

    assertEquals(20, merged.size)
    assertEquals(us[0].videoId, merged[0].videoId)
    assertEquals(gb[1].videoId, merged[1].videoId)
    assertEquals(1, merged.count { it.videoId == us[0].videoId })
  }

  @Test
  fun mapsIsoDurationAndSnippetMetadata() {
    val mapped = TrendingMusicRepository.mapVideo(
      "abcdefghijk", "Song", "Artist", "https://img.test/a.jpg", "2026-01-01T00:00:00Z", "PT3M12S",
    )
    assertEquals(192_000L, mapped.durationMs)
    assertEquals("Artist", mapped.channelTitle)
    assertEquals("https://img.test/a.jpg", mapped.thumbnailUrl)
  }

  @Test
  fun usesFreshCacheWithoutCallingNetworkAndRefreshesExpiredCache() {
    val source = FakeTrendingSource()
    val cache = MemoryTrendingCache()
    val repository = TrendingMusicRepository(source, cache, { 3_600_000L })
    cache.write("vn", TrendingSection("vn", listOf(video("cached00001")), listOf("VN"), 1L, false))
    assertEquals("cached00001", repository.get("vn", false).items.single().videoId)
    assertEquals(0, source.calls)

    repository.get("vn", true)
    assertEquals(1, source.calls)
  }

  @Test
  fun returnsAvailableInternationalRegionWhenTheOtherFails() {
    val source = FakeTrendingSource().apply { failRegion = "US" }
    val result = TrendingMusicRepository(source, MemoryTrendingCache(), { 10L }).get("international", true)
    assertEquals(listOf("GB"), result.regions)
    assertEquals("gbvideo0001", result.items.first().videoId)
    assertFalse(result.stale)
  }

  @Test
  fun returnsStaleCacheOnFailureAndThrowsWhenNoCacheExists() {
    val source = FakeTrendingSource().apply { failRegion = "VN" }
    val cache = MemoryTrendingCache()
    cache.write("vn", TrendingSection("vn", listOf(video("cached00001")), listOf("VN"), 1L, false))
    val stale = TrendingMusicRepository(source, cache, { 3_600_002L }).get("vn", true)
    assertTrue(stale.stale)
    assertEquals("cached00001", stale.items.single().videoId)
    assertTrue(runCatching { TrendingMusicRepository(source, MemoryTrendingCache(), { 9L }).get("vn", true) }.isFailure)
  }

  private fun video(id: String) = TrendingVideo(id, id, "channel", null, null, null)

  private class MemoryTrendingCache : TrendingCache {
    private val values = mutableMapOf<String, TrendingSection>()
    override fun read(section: String) = values[section]
    override fun write(section: String, value: TrendingSection) { values[section] = value }
  }

  private class FakeTrendingSource : TrendingSource {
    var calls = 0
    var failRegion: String? = null
    override fun fetch(region: String): List<TrendingVideo> {
      calls++
      if (region == failRegion) error("offline")
      val id = if (region == "GB") "gbvideo0001" else "vnvideo00001"
      return listOf(TrendingVideo(id, id, "channel", null, null, null))
    }
  }
}
