package com.tubelite

import android.text.Html
import org.schabi.newpipe.extractor.NewPipe

internal object SearchSuggestionRepository {
  fun suggest(query: String): List<String> {
    val normalized = query.trim()
    if (normalized.length < 2) return emptyList()
    NewPipeVideoRepository.initialize()
    val values = NewPipe.getService("YouTube").suggestionExtractor.suggestionList(normalized)
    return SearchSuggestionPolicy.normalize(values.map { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() })
  }
}
