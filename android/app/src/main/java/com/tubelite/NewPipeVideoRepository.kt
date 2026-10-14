package com.tubelite

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.json.JSONArray
import org.json.JSONObject

internal object NewPipeVideoRepository {
  private val lock = Any()
  @Volatile private var initialized = false

  internal fun initialize() {
    if (initialized) return
    synchronized(lock) {
      if (!initialized) {
        NewPipe.init(NewPipeDownloader(), Localization("vi", "VN"), ContentCountry("VN"))
        initialized = true
      }
    }
  }

  private fun fetchInfo(videoId: String): StreamInfo {
    require(videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) { "Invalid YouTube video id" }
    initialize()
    val service: StreamingService = NewPipe.getService("YouTube")
    return StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=$videoId")
  }

  internal fun getStreamInfo(videoId: String): StreamInfo = fetchInfo(videoId)

  fun getVideoDetails(videoId: String): JSONObject {
    val info = fetchInfo(videoId)
    val streams = playableStreams(info)
    val qualities = JSONArray()

    streams.forEach { stream ->
      val height = Regex("(\\d+)p").find(stream.resolution)?.groupValues?.get(1)?.toIntOrNull()
      qualities.put(
        JSONObject()
          .put("id", stream.id)
          .put("label", if (stream.audioUrl == null) stream.resolution else "${stream.resolution} · âm thanh riêng")
          .put("height", height ?: JSONObject.NULL),
      )
    }

    return JSONObject()
      .put("videoId", videoId)
      .put("title", info.name)
      .put("channelTitle", info.uploaderName)
      .put("durationMs", info.duration * 1000)
      .put("thumbnailUrl", info.thumbnails.firstOrNull()?.url ?: JSONObject.NULL)
      .put("defaultQualityId", chooseDefaultStream(streams)?.id ?: JSONObject.NULL)
      .put("qualities", qualities)
  }

  fun resolveVideo(videoId: String, qualityId: String = ""): ResolvedVideoStream {
    val info = fetchInfo(videoId)
    val streams = playableStreams(info)
    val selected = streams.firstOrNull { it.id == qualityId } ?: chooseDefaultStream(streams)
      ?: throw IllegalStateException("No playable combined stream is available")
    return ResolvedVideoStream(
      selected.videoUrl, info.name, selected.id, selected.audioUrl,
      VideoMetadata(
        videoId, info.name, info.uploaderName, info.thumbnails.firstOrNull()?.url,
        info.duration * 1000L,
      ),
    )
  }

  private fun playableStreams(info: StreamInfo): List<PlayableVideoStream> {
    val audioUrl = info.audioStreams
      .filter { it.isUrl && it.content.startsWith("https://") }
      .maxByOrNull { it.averageBitrate }
      ?.content

    val combined = info.videoStreams
      .filter { !it.isVideoOnly() && it.isUrl && it.content.startsWith("https://") }
      .sortedByDescending { it.getBitrate() }
      .distinctBy { it.getResolution() }
      .map { PlayableVideoStream(it.id, it.getResolution(), it.content, null) }

    val adaptive = if (audioUrl == null) emptyList() else info.videoOnlyStreams
      .filter { it.isUrl && it.content.startsWith("https://") }
      .sortedByDescending { it.getBitrate() }
      .distinctBy { it.getResolution() }
      .map { PlayableVideoStream(it.id, it.getResolution(), it.content, audioUrl) }

    return combined + adaptive
  }

  private fun chooseDefaultStream(streams: List<PlayableVideoStream>): PlayableVideoStream? {
    val qualities = streams.map { stream ->
      val height = Regex("(\\d+)p").find(stream.resolution)?.groupValues?.get(1)?.toIntOrNull()
      PlaybackQuality(stream.id, height)
    }
    val selectedId = PlaybackPolicy.chooseDefaultQuality(qualities)?.id ?: return null
    return streams.firstOrNull { it.id == selectedId }
  }
}

private data class PlayableVideoStream(
  val id: String,
  val resolution: String,
  val videoUrl: String,
  val audioUrl: String?,
)

internal data class ResolvedVideoStream(
  val videoUrl: String,
  val title: String,
  val qualityId: String,
  val audioUrl: String?,
  val metadata: VideoMetadata,
)
