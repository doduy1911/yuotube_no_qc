package com.tubelite

internal sealed interface AutoplayDecision {
  data class Resolve(val videoId: String, val token: Long) : AutoplayDecision
  data class NeedFallback(val token: Long) : AutoplayDecision
  data object Stop : AutoplayDecision
}

internal class AutoplayCoordinator(enabled: Boolean = true) {
  private data class Pending(val generation: Long, val originVideoId: String, val candidates: List<String>, val attempts: Int, val token: Long, val resolvedVideoId: String? = null)
  private var enabled = enabled
  private var generation = 0L
  private var token = 0L
  private var pending: Pending? = null
  private var handledEndedVideoId: String? = null
  private val recent = ArrayDeque<String>()

  fun setEnabled(value: Boolean) {
    if (enabled == value) return
    enabled = value; generation++; pending = null
  }

  fun cancelPending() { generation++; pending = null }

  fun isEnabled() = enabled
  fun isCurrent(token: Long) = enabled && pending?.let { it.token == token && it.generation == generation } == true
  fun isCurrentGeneration() = generation >= 0L

  fun freshCandidateCount(videoId: String, recommendations: List<String>): Int = recommendations.asSequence()
    .filter { it.matches(VIDEO_ID) && it != videoId && it !in recent }
    .distinct().take(MAX_CANDIDATES).count()

  fun manualSelection(videoId: String) {
    generation++; pending = null; handledEndedVideoId = null
    recent.clear(); rememberPlayed(videoId)
  }

  fun rememberPlayed(videoId: String) {
    if (videoId.isBlank()) return
    recent.remove(videoId); recent.addLast(videoId)
    while (recent.size > RECENT_LIMIT) recent.removeFirst()
  }

  fun onEnded(videoId: String, recommendations: List<String>, networkAvailable: Boolean, fallbackRecommendations: List<String> = emptyList()): AutoplayDecision {
    if (!enabled || videoId.isBlank() || handledEndedVideoId == videoId) return AutoplayDecision.Stop
    handledEndedVideoId = videoId
    if (!networkAvailable) return AutoplayDecision.Stop
    val candidates = (recommendations + fallbackRecommendations).asSequence()
      .filter { it.matches(VIDEO_ID) && it != videoId && it !in recent }
      .distinct().take(MAX_CANDIDATES).toList()
    if (candidates.isEmpty()) return AutoplayDecision.Stop
    return issue(videoId, candidates, 1)
  }

  fun failed(requestToken: Long, networkAvailable: Boolean): AutoplayDecision {
    val current = pending ?: return AutoplayDecision.Stop
    if (current.token != requestToken) return AutoplayDecision.Stop
    if (current.generation != generation || !enabled || !networkAvailable || current.attempts >= MAX_CANDIDATES) {
      pending = null
      return AutoplayDecision.Stop
    }
    if (current.resolvedVideoId == null) current.candidates.firstOrNull()?.let(::rememberPlayed)
    val remaining = if (current.resolvedVideoId != null) current.candidates else current.candidates.drop(1)
    if (remaining.isEmpty()) {
      pending = current.copy(candidates = emptyList(), resolvedVideoId = null)
      return AutoplayDecision.NeedFallback(current.token)
    }
    return issue(current.originVideoId, remaining, current.attempts + 1)
  }

  fun supplyFallback(requestToken: Long, candidates: List<String>, networkAvailable: Boolean): AutoplayDecision {
    val current = pending ?: return AutoplayDecision.Stop
    if (current.token != requestToken) return AutoplayDecision.Stop
    if (current.generation != generation || !enabled || !networkAvailable || current.attempts >= MAX_CANDIDATES) {
      pending = null
      return AutoplayDecision.Stop
    }
    val fresh = candidates.asSequence().filter { it.matches(VIDEO_ID) && it != current.originVideoId && it !in recent }.distinct().toList()
    if (fresh.isEmpty()) { pending = null; return AutoplayDecision.Stop }
    return issue(current.originVideoId, fresh, current.attempts + 1)
  }

  fun completed(requestToken: Long, videoId: String): Boolean {
    val current = pending ?: return false
    if (current.token != requestToken || current.generation != generation || current.candidates.firstOrNull() != videoId) return false
    pending = current.copy(candidates = current.candidates.drop(1), resolvedVideoId = videoId); rememberPlayed(videoId); return true
  }

  private fun issue(originVideoId: String, candidates: List<String>, attempts: Int): AutoplayDecision {
    val id = candidates.firstOrNull() ?: return AutoplayDecision.Stop
    token++
    pending = Pending(generation, originVideoId, candidates, attempts, token)
    return AutoplayDecision.Resolve(id, token)
  }

  companion object {
    private const val RECENT_LIMIT = 50
    private const val MAX_CANDIDATES = 3
    private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
  }
}
