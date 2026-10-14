package com.tubelite

import android.content.Context
import android.net.Uri
import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class TrendingVideo(
  val videoId: String,
  val title: String,
  val channelTitle: String,
  val thumbnailUrl: String?,
  val publishedAt: String?,
  val durationMs: Long?,
)

internal data class TrendingSection(
  val section: String,
  val items: List<TrendingVideo>,
  val regions: List<String>,
  val updatedAt: Long,
  val stale: Boolean,
) {
  fun toJson() = JSONObject()
    .put("section", section)
    .put("items", JSONArray().also { array -> items.forEach { video -> array.put(video.toJson()) } })
    .put("regions", JSONArray(regions))
    .put("updatedAt", updatedAt)
    .put("stale", stale)

  companion object {
    fun fromJson(json: JSONObject) = TrendingSection(
      section = json.optString("section"),
      items = json.optJSONArray("items")?.let { array ->
        (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::trendingVideoFromJson) }
      }.orEmpty(),
      regions = json.optJSONArray("regions")?.let { array -> (0 until array.length()).map { array.optString(it) } }.orEmpty(),
      updatedAt = json.optLong("updatedAt"),
      stale = json.optBoolean("stale"),
    )
  }
}

private fun TrendingVideo.toJson() = JSONObject()
  .put("videoId", videoId).put("title", title).put("channelTitle", channelTitle)
  .put("thumbnailUrl", thumbnailUrl ?: JSONObject.NULL).put("publishedAt", publishedAt ?: JSONObject.NULL)
  .put("durationMs", durationMs ?: JSONObject.NULL)

internal interface TrendingCache {
  fun read(section: String): TrendingSection?
  fun write(section: String, value: TrendingSection)
}

internal interface TrendingSource {
  fun fetch(region: String): List<TrendingVideo>
}

internal class TrendingMusicRepository(
  private val source: TrendingSource,
  private val cache: TrendingCache,
  private val now: () -> Long = System::currentTimeMillis,
) {
  fun get(section: String, forceRefresh: Boolean): TrendingSection {
    require(section == "vn" || section == "international") { "Unknown trending section" }
    val cached = cache.read(section)
    val timestamp = now()
    if (!forceRefresh && cached != null && timestamp - cached.updatedAt < CACHE_TTL_MS) return cached

    val regions = if (section == "vn") listOf("VN") else listOf("US", "GB")
    val available = regions.mapNotNull { region -> runCatching { region to source.fetch(region) }.getOrNull() }
    if (available.isEmpty()) {
      if (cached != null) return cached.copy(stale = true)
      throw YouTubeSearchException("API_UNAVAILABLE", "Không tải được nhạc thịnh hành.")
    }
    val items = if (section == "international") mergeInternational(
      available.firstOrNull { it.first == "US" }?.second.orEmpty(),
      available.firstOrNull { it.first == "GB" }?.second.orEmpty(),
    ) else available.first().second.take(MAX_ITEMS)
    val result = TrendingSection(section, items, available.map { it.first }, timestamp, false)
    cache.write(section, result)
    return result
  }

  companion object {
    private const val CACHE_TTL_MS = 60 * 60 * 1000L
    private const val MAX_ITEMS = 20

    fun mergeInternational(us: List<TrendingVideo>, gb: List<TrendingVideo>): List<TrendingVideo> {
      val result = mutableListOf<TrendingVideo>()
      val seen = mutableSetOf<String>()
      var usIndex = 0
      var gbIndex = 0
      var takeUs = true
      while (result.size < MAX_ITEMS && (usIndex < us.size || gbIndex < gb.size)) {
        val fromUs = takeUs && usIndex < us.size || gbIndex >= gb.size
        val item = if (fromUs) us[usIndex++] else gb[gbIndex++]
        if (seen.add(item.videoId)) {
          result += item
          takeUs = !fromUs
        }
      }
      return result
    }

    fun mapVideo(videoId: String, title: String, channel: String, thumbnail: String?, publishedAt: String?, duration: String?): TrendingVideo =
      TrendingVideo(videoId, title, channel, thumbnail, publishedAt, parseDuration(duration))

    private fun parseDuration(value: String?): Long? {
      if (value == null) return null
      val match = Regex("^P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$").matchEntire(value) ?: return null
      if (match.groupValues.drop(1).all(String::isBlank)) return null
      val (days, hours, minutes, seconds) = match.groupValues.drop(1).map { it.toLongOrNull() ?: 0L }
      return (((days * 24 + hours) * 60 + minutes) * 60 + seconds) * 1000
    }
  }
}

internal class SharedPreferencesTrendingCache(context: Context) : TrendingCache {
  private val preferences = context.getSharedPreferences("trending_music_v2", Context.MODE_PRIVATE)
  override fun read(section: String): TrendingSection? = preferences.getString(section, null)
    ?.let { runCatching { TrendingSection.fromJson(JSONObject(it)) }.getOrNull() }

  override fun write(section: String, value: TrendingSection) {
    preferences.edit().putString(section, value.toJson().toString()).apply()
  }
}

internal class YouTubeTrendingSource : TrendingSource {
  override fun fetch(region: String): List<TrendingVideo> {
    if (BuildConfig.YOUTUBE_API_KEY.isBlank()) throw YouTubeSearchException("API_KEY_MISSING", "Thiếu YouTube API key trong cấu hình.")
    val uri = Uri.parse("https://www.googleapis.com/youtube/v3/videos").buildUpon().apply {
      appendQueryParameter("part", "snippet,contentDetails")
      appendQueryParameter("chart", "mostPopular")
      appendQueryParameter("regionCode", region)
      appendQueryParameter("videoCategoryId", "10")
      appendQueryParameter("maxResults", "20")
      appendQueryParameter("key", BuildConfig.YOUTUBE_API_KEY)
    }.build()
    val connection = URL(uri.toString()).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 20_000
    try {
      val status = connection.responseCode
      val stream = if (status in 200..299) connection.inputStream else connection.errorStream
      val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
      val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
      if (status !in 200..299) throw YouTubeSearchRepository.mapApiError(status, json)
      val items = json.optJSONArray("items") ?: JSONArray()
      return (0 until items.length()).mapNotNull { index ->
        val item = items.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id")
        if (!id.matches(Regex("[A-Za-z0-9_-]{11}"))) return@mapNotNull null
        val snippet = item.optJSONObject("snippet") ?: JSONObject()
        val thumbnail = snippet.optJSONObject("thumbnails") ?: JSONObject()
        val image = listOf("high", "medium", "default").firstNotNullOfOrNull { thumbnail.optJSONObject(it)?.optString("url")?.takeIf(String::isNotBlank) }
        val duration = item.optJSONObject("contentDetails")?.optString("duration")
        val title = Html.fromHtml(snippet.optString("title"), Html.FROM_HTML_MODE_LEGACY).toString()
        TrendingMusicRepository.mapVideo(id, title, snippet.optString("channelTitle"), image,
          snippet.optString("publishedAt").takeIf(String::isNotBlank), duration)
      }
    } finally {
      connection.disconnect()
    }
  }
}

private fun trendingVideoFromJson(value: JSONObject): TrendingVideo = TrendingVideo(
  value.optString("videoId"), value.optString("title"), value.optString("channelTitle"),
  value.optString("thumbnailUrl").takeUnless { it == "null" || it.isBlank() },
  value.optString("publishedAt").takeUnless { it == "null" || it.isBlank() },
  value.optLong("durationMs").takeIf { !value.isNull("durationMs") },
)
