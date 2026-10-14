package com.tubelite

import android.net.Uri
import android.text.Html
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.ContentAvailability
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

internal class NewPipeRelatedVideoSource : RelatedVideoSource {
  override fun getRelated(videoId: String): List<RecommendationCandidate> {
    val info = NewPipeVideoRepository.getStreamInfo(videoId)
    return info.relatedItems.mapNotNull { item ->
      val relatedId = videoIdFromUrl(item.url) ?: return@mapNotNull null
      val stream = item as? StreamInfoItem
      val mediaType = if (item.infoType == InfoItem.InfoType.STREAM && stream?.streamType == StreamType.VIDEO_STREAM) {
        RecommendationMediaType.VIDEO
      } else RecommendationMediaType.NON_VIDEO
      val availability = when {
        stream == null -> RecommendationAvailability.UNAVAILABLE
        stream.contentAvailability == ContentAvailability.UPCOMING -> RecommendationAvailability.UPCOMING
        stream.streamType == StreamType.LIVE_STREAM || stream.streamType == StreamType.AUDIO_LIVE_STREAM -> RecommendationAvailability.LIVE
        stream.contentAvailability == ContentAvailability.AVAILABLE || stream.contentAvailability == ContentAvailability.UNKNOWN -> RecommendationAvailability.AVAILABLE
        else -> RecommendationAvailability.UNAVAILABLE
      }
      RecommendationCandidate(
        VideoSummary(
          videoId = relatedId,
          title = Html.fromHtml(item.name, Html.FROM_HTML_MODE_LEGACY).toString(),
          channelTitle = stream?.uploaderName.orEmpty(),
          thumbnailUrl = item.thumbnails.firstOrNull()?.url,
          publishedAt = null,
          durationMs = stream?.duration?.takeIf { it > 0 }?.times(1000L),
        ),
        mediaType,
        availability,
      )
    }
  }

  private fun videoIdFromUrl(value: String): String? {
    val uri = Uri.parse(value)
    val queryId = uri.getQueryParameter("v")
    val pathId = uri.pathSegments.lastOrNull()
    val id = queryId ?: pathId
    return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
  }
}
