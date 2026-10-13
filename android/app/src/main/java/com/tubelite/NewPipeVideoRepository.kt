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

  private fun initialize() {
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

  fun getVideoDetails(videoId: String): JSONObject {
    val info = fetchInfo(videoId)
    val streams = playableCombinedStreams(info)
    val qualities = JSONArray()

    streams.forEach { stream ->
      val height = Regex("(\\d+)p").find(stream.resolution)?.groupValues?.get(1)?.toIntOrNull()
      qualities.put(
        JSONObject()
          .put("id", stream.id)
          .put("label", stream.resolution)
          .put("height", height ?: JSONObject.NULL),
      )
    }

    return JSONObject()
      .put("videoId", videoId)
      .put("title", info.name)
      .put("channelTitle", info.uploaderName)
      .put("durationMs", info.duration * 1000)
      .put("thumbnailUrl", info.thumbnails.firstOrNull()?.url ?: JSONObject.NULL)
      .put("qualities", qualities)
  }

  fun openVideo(videoId: String, qualityId: String): Pair<String, String> {
    val info = fetchInfo(videoId)
    val streams = playableCombinedStreams(info)
    val selected = streams.firstOrNull { it.id == qualityId } ?: chooseDefaultStream(streams)
      ?: throw IllegalStateException("No playable combined stream is available")
    return selected.content to info.name
  }

  private fun playableCombinedStreams(info: StreamInfo): List<VideoStream> =
    info.videoStreams.filter { !it.isVideoOnly && it.isUrl && it.content.startsWith("https://") }

  private fun chooseDefaultStream(streams: List<VideoStream>): VideoStream? {
    val withHeights = streams.mapNotNull { stream ->
      val height = Regex("(\\d+)p").find(stream.resolution)?.groupValues?.get(1)?.toIntOrNull()
      height?.let { stream to it }
    }
    return withHeights.filter { it.second <= 720 }.maxByOrNull { it.second }?.first
      ?: withHeights.minByOrNull { it.second }?.first
      ?: streams.firstOrNull()
  }
}
