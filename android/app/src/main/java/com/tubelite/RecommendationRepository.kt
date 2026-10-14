package com.tubelite

import org.json.JSONArray
import org.json.JSONObject

internal data class VideoSummary(
  val videoId: String,
  val title: String,
  val channelTitle: String,
  val thumbnailUrl: String?,
  val publishedAt: String?,
  val durationMs: Long?,
) {
  fun toJson() = JSONObject()
    .put("videoId", videoId).put("title", title).put("channelTitle", channelTitle)
    .put("thumbnailUrl", thumbnailUrl ?: JSONObject.NULL).put("publishedAt", publishedAt ?: JSONObject.NULL)
    .put("durationMs", durationMs ?: JSONObject.NULL)
}
internal enum class RecommendationMediaType { VIDEO, NON_VIDEO }
internal enum class RecommendationAvailability { AVAILABLE, LIVE, UPCOMING, UNAVAILABLE }
internal data class RecommendationCandidate(
  val video: VideoSummary,
  val mediaType: RecommendationMediaType,
  val availability: RecommendationAvailability,
)
internal data class RecommendationSet(
  val videoId: String,
  val items: List<VideoSummary>,
  val source: String,
  val error: String? = null,
) {
  fun toJson() = JSONObject()
    .put("videoId", videoId).put("source", source)
    .put("items", JSONArray().also { array -> items.forEach { array.put(it.toJson()) } })
    .put("error", error ?: JSONObject.NULL)
}
internal interface RelatedVideoSource {
  fun getRelated(videoId: String): List<RecommendationCandidate>
}
internal interface TrendingRecommendationSource {
  fun getTrending(section: String): List<VideoSummary>
}

internal class RecommendationRepository(
  private val related: RelatedVideoSource,
  private val trending: TrendingRecommendationSource,
) {
  fun get(videoId: String): RecommendationSet {
    require(videoId.matches(VIDEO_ID)) { "Invalid YouTube video id" }
    val relatedResult = runCatching { related.getRelated(videoId) }
    val relatedItems = relatedResult.getOrDefault(emptyList())
    val filtered = filterRelated(videoId, relatedItems)
    if (filtered.isNotEmpty()) return RecommendationSet(videoId, filtered, SOURCE_RELATED)

    val vnResult = runCatching { trending.getTrending("vn") }
    val internationalResult = runCatching { trending.getTrending("international") }
    val fallback = mergeTrending(
      videoId,
      vnResult.getOrDefault(emptyList()),
      internationalResult.getOrDefault(emptyList()),
    )
    val error = if (fallback.isEmpty() && vnResult.isFailure && internationalResult.isFailure) {
      "Không tải được bài gợi ý. Hãy thử lại sau."
    } else if (fallback.isEmpty()) {
      "Chưa có bài gợi ý để hiển thị."
    } else null
    return RecommendationSet(videoId, fallback, SOURCE_TRENDING, error)
  }

  companion object {
    private const val MAX_ITEMS = 20
    private const val SOURCE_RELATED = "related"
    private const val SOURCE_TRENDING = "trending"
    private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

    fun filterRelated(videoId: String, candidates: List<RecommendationCandidate>): List<VideoSummary> {
      val seen = mutableSetOf<String>()
      return candidates.asSequence()
        .filter { candidate ->
          candidate.mediaType == RecommendationMediaType.VIDEO &&
            candidate.availability == RecommendationAvailability.AVAILABLE &&
            candidate.video.videoId != videoId && VIDEO_ID.matches(candidate.video.videoId) &&
            seen.add(candidate.video.videoId)
        }
        .map(RecommendationCandidate::video)
        .take(MAX_ITEMS)
        .toList()
    }

    fun mergeTrending(videoId: String, vn: List<VideoSummary>, international: List<VideoSummary>): List<VideoSummary> {
      val result = mutableListOf<VideoSummary>()
      val seen = mutableSetOf(videoId)
      var vnIndex = 0
      var internationalIndex = 0
      var takeVn = true
      while (result.size < MAX_ITEMS && (vnIndex < vn.size || internationalIndex < international.size)) {
        val fromVn = takeVn && vnIndex < vn.size || internationalIndex >= international.size
        val item = if (fromVn) vn[vnIndex++] else international[internationalIndex++]
        if (VIDEO_ID.matches(item.videoId) && seen.add(item.videoId)) {
          result += item
          takeVn = !fromVn
        }
      }
      return result
    }
  }
}

internal class CachedTrendingRecommendationSource(private val repository: TrendingMusicRepository) : TrendingRecommendationSource {
  override fun getTrending(section: String): List<VideoSummary> = repository.get(section, forceRefresh = false).items.map { video ->
    VideoSummary(video.videoId, video.title, video.channelTitle, video.thumbnailUrl, video.publishedAt, video.durationMs)
  }
}
