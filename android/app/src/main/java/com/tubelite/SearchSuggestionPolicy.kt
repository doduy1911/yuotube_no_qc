package com.tubelite

internal object SearchSuggestionPolicy {
  private val unresolvedEntity = Regex("&(?:#\\d+|#x[0-9a-f]+|[a-z]+);", RegexOption.IGNORE_CASE)

  fun normalize(suggestions: List<String>): List<String> {
    val seen = mutableSetOf<String>()
    return suggestions.asSequence().map(String::trim)
      .filter { it.isNotEmpty() && '\uFFFD' !in it && !unresolvedEntity.containsMatchIn(it) }
      .filter { seen.add(it.lowercase()) }
      .take(8).toList()
  }
}
