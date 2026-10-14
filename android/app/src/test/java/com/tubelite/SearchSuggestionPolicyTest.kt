package com.tubelite

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchSuggestionPolicyTest {
  @Test
  fun trimsDeduplicatesDropsMalformedEntriesAndLimitsToEight() {
    val values = listOf("  Song  ", "song", "broken � text", "", "two", "three", "four", "five", "six", "seven", "eight", "nine")
    assertEquals(listOf("Song", "two", "three", "four", "five", "six", "seven", "eight"), SearchSuggestionPolicy.normalize(values))
  }

  @Test
  fun dropsUnresolvedHtmlEntities() {
    assertEquals(listOf("clean"), SearchSuggestionPolicy.normalize(listOf("clean", "bad &#7911; text")))
  }
}
