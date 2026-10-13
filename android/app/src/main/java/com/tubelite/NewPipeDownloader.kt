package com.tubelite

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URL

internal class NewPipeDownloader : Downloader() {
  override fun execute(request: Request): Response {
    val connection = URL(request.url()).openConnection() as HttpURLConnection
    connection.connectTimeout = 20_000
    connection.readTimeout = 25_000
    connection.instanceFollowRedirects = true
    connection.requestMethod = request.httpMethod()
    request.headers()?.forEach { (name, values) ->
      values.forEach { value -> connection.addRequestProperty(name, value) }
    }

    val requestBody = request.dataToSend()
    if (requestBody != null) {
      connection.doOutput = true
      connection.outputStream.use { it.write(requestBody) }
    }

    val status = connection.responseCode
    val stream = if (status >= 400) connection.errorStream else connection.inputStream
    val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
    val responseHeaders = connection.headerFields
      .filterKeys { it != null }
      .mapKeys { it.key ?: "" }
      .filterKeys { it.isNotEmpty() }

    return Response(
      status,
      connection.responseMessage,
      responseHeaders,
      responseBody,
      connection.url.toString(),
    ).also { connection.disconnect() }
  }
}
