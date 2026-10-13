package com.tubelite

import android.net.Uri
import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal class YouTubeSearchException(val code: String, message: String) : Exception(message)

internal object YouTubeSearchRepository {
  private const val BASE_URL = "https://www.googleapis.com/youtube/v3/"

  fun search(query: String, pageToken: String): JSONObject {
    if (BuildConfig.YOUTUBE_API_KEY.isBlank()) {
      throw YouTubeSearchException("API_KEY_MISSING", "Thiếu YouTube API key trong cấu hình.")
    }
    if (query.isBlank()) {
      return JSONObject().put("items", JSONArray()).put("nextPageToken", JSONObject.NULL)
    }

    val searchParams = linkedMapOf(
      "part" to "snippet",
      "type" to "video",
      "q" to query,
      "maxResults" to "20",
      "regionCode" to "VN",
      "key" to BuildConfig.YOUTUBE_API_KEY,
    )
    if (pageToken.isNotBlank()) searchParams["pageToken"] = pageToken

    val searchResponse = get("search", searchParams)
    val searchItems = searchResponse.optJSONArray("items") ?: JSONArray()
    val videos = mutableListOf<JSONObject>()
    val ids = mutableListOf<String>()

    for (index in 0 until searchItems.length()) {
      val item = searchItems.optJSONObject(index) ?: continue
      val videoId = item.optJSONObject("id")?.optString("videoId").orEmpty()
      if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) continue

      val snippet = item.optJSONObject("snippet") ?: JSONObject()
      val thumbnails = snippet.optJSONObject("thumbnails") ?: JSONObject()
      val thumbnail = listOf("high", "medium", "default")
        .firstNotNullOfOrNull { thumbnails.optJSONObject(it)?.optString("url")?.takeIf(String::isNotBlank) }

      videos += JSONObject()
        .put("videoId", videoId)
        .put("title", Html.fromHtml(snippet.optString("title"), Html.FROM_HTML_MODE_LEGACY).toString())
        .put("channelTitle", snippet.optString("channelTitle"))
        .put("thumbnailUrl", thumbnail ?: JSONObject.NULL)
        .put("publishedAt", snippet.optString("publishedAt").takeIf(String::isNotBlank) ?: JSONObject.NULL)
      ids += videoId
    }

    val durations = if (ids.isEmpty()) emptyMap() else loadDurations(ids)
    videos.forEach { video ->
      video.put("duration", durations[video.optString("videoId")] ?: JSONObject.NULL)
    }

    return JSONObject()
      .put("items", JSONArray(videos))
      .put("nextPageToken", searchResponse.optString("nextPageToken").takeIf(String::isNotBlank) ?: JSONObject.NULL)
  }

  private fun loadDurations(ids: List<String>): Map<String, String> {
    val response = get(
      "videos",
      mapOf(
        "part" to "contentDetails",
        "id" to ids.joinToString(","),
        "key" to BuildConfig.YOUTUBE_API_KEY,
      ),
    )
    val result = mutableMapOf<String, String>()
    val items = response.optJSONArray("items") ?: JSONArray()
    for (index in 0 until items.length()) {
      val item = items.optJSONObject(index) ?: continue
      val duration = item.optJSONObject("contentDetails")?.optString("duration")
      if (!duration.isNullOrBlank()) result[item.optString("id")] = duration
    }
    return result
  }

  private fun get(endpoint: String, params: Map<String, String>): JSONObject {
    val uri = Uri.parse(BASE_URL + endpoint).buildUpon().apply {
      params.forEach { (key, value) -> appendQueryParameter(key, value) }
    }.build()
    val connection = URL(uri.toString()).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 20_000
    connection.requestMethod = "GET"

    try {
      val status = connection.responseCode
      val stream = if (status in 200..299) connection.inputStream else connection.errorStream
      val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
      val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
      if (status !in 200..299) throw mapApiError(status, json)
      return json
    } finally {
      connection.disconnect()
    }
  }

  private fun mapApiError(status: Int, body: JSONObject): YouTubeSearchException {
    val error = body.optJSONObject("error") ?: JSONObject()
    val message = error.optString("message").takeIf(String::isNotBlank)
      ?: "YouTube API request failed ($status)."
    val reason = error.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
    val code = when {
      reason.contains("quota", ignoreCase = true) -> "API_QUOTA_EXCEEDED"
      reason.contains("keyInvalid", ignoreCase = true) || status == 401 -> "API_KEY_INVALID"
      reason.contains("accessNotConfigured", ignoreCase = true) -> "API_NOT_ENABLED"
      status == 403 -> "API_FORBIDDEN"
      status >= 500 -> "API_UNAVAILABLE"
      else -> "API_REQUEST_FAILED"
    }
    return YouTubeSearchException(code, message)
  }
}
