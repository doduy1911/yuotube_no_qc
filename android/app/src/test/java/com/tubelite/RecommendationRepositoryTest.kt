package com.tubelite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationRepositoryTest {
  @Test
  fun filtersCurrentDuplicatesPlaylistsLiveAndUpcomingItems() {
    val related = FakeRelatedSource(listOf(
      candidate("current0001"), candidate("video000000"), candidate("video000000"),
      candidate("playlist001", RecommendationMediaType.NON_VIDEO),
      candidate("livevid00001", availability = RecommendationAvailability.LIVE),
      candidate("future00001", availability = RecommendationAvailability.UPCOMING),
      candidate("video000001"),
    ))
    val result = repository(related, FakeTrendingSource()).get("current0001")
    assertEquals("related", result.source)
    assertEquals(listOf("video000000", "video000001"), result.items.map { it.videoId })
  }

  @Test
  fun usesRelatedItemsInSourceOrderWhenTheyAreAvailable() {
    val result = repository(FakeRelatedSource(listOf(candidate("video000000"), candidate("video000001"))), FakeTrendingSource()).get("current0001")
    assertEquals("related", result.source)
    assertEquals(listOf("video000000", "video000001"), result.items.map { it.videoId })
  }

  @Test
  fun fallsBackToInterleavedDeduplicatedTrendingWhenRelatedIsEmptyOrFails() {
    val vn = listOf(video("video000000"), video("video000001"))
    val international = listOf(video("video000002"), video("video000000"), video("video000003"))
    val result = repository(FakeRelatedSource(emptyList()), FakeTrendingSource(vn, international)).get("current0001")
    assertEquals("trending", result.source)
    assertEquals(listOf("video000000", "video000002", "video000001", "video000003"), result.items.map { it.videoId })

    val failed = repository(FakeRelatedSource(emptyList(), fail = true), FakeTrendingSource(vn, international)).get("current0001")
    assertEquals(result.items, failed.items)
  }

  @Test
  fun limitsFallbackToTwentyAndReturnsEmptyWhenBothSourcesFail() {
    val vn = (0..14).map { video("vn${it.toString().padStart(9, '0')}") }
    val international = (0..14).map { video("gb${it.toString().padStart(9, '0')}") }
    val limited = repository(FakeRelatedSource(emptyList()), FakeTrendingSource(vn, international)).get("current0001")
    assertEquals(20, limited.items.size)

    val empty = repository(FakeRelatedSource(emptyList(), fail = true), FakeTrendingSource(fail = true)).get("current0001")
    assertTrue(empty.items.isEmpty())
    assertEquals("trending", empty.source)
  }

  private fun repository(related: RelatedVideoSource, trending: TrendingRecommendationSource) = RecommendationRepository(related, trending)
  private fun video(id: String) = VideoSummary(id, id, "channel", null, null, null)
  private fun candidate(id: String, type: RecommendationMediaType = RecommendationMediaType.VIDEO, availability: RecommendationAvailability = RecommendationAvailability.AVAILABLE) =
    RecommendationCandidate(video(id), type, availability)

  private class FakeRelatedSource(private val items: List<RecommendationCandidate>, private val fail: Boolean = false) : RelatedVideoSource {
    override fun getRelated(videoId: String): List<RecommendationCandidate> { if (fail) error("related unavailable"); return items }
  }

  private class FakeTrendingSource(private val vn: List<VideoSummary> = emptyList(), private val international: List<VideoSummary> = emptyList(), private val fail: Boolean = false) : TrendingRecommendationSource {
    override fun getTrending(section: String): List<VideoSummary> {
      if (fail) error("trending unavailable")
      return if (section == "vn") vn else international
    }
  }
}
