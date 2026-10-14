package com.tubelite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoplayCoordinatorTest {
  @Test fun disabledDoesNotStartAndEnabledChoosesFirstValidCandidateOnlyOnce() {
    val coordinator = AutoplayCoordinator(enabled = false)
    assertEquals(AutoplayDecision.Stop, coordinator.onEnded("current0001", listOf("next0000001"), networkAvailable = true))
    coordinator.setEnabled(true)
    val decision = coordinator.onEnded("current0001", listOf("current0001", "next0000001", "other000001"), true)
    assertTrue(decision is AutoplayDecision.Resolve)
    assertEquals("next0000001", (decision as AutoplayDecision.Resolve).videoId)
    assertEquals(AutoplayDecision.Stop, coordinator.onEnded("current0001", listOf("next0000001"), true))
  }

  @Test fun invalidatesPendingResolveWhenDisabledOrManuallySelected() {
    val coordinator = AutoplayCoordinator()
    val pending = coordinator.onEnded("current0001", listOf("next0000001"), true) as AutoplayDecision.Resolve
    coordinator.setEnabled(false)
    assertFalse(coordinator.isCurrent(pending.token))
    coordinator.setEnabled(true)
    coordinator.manualSelection("other000001")
    val next = coordinator.onEnded("other000001", listOf("next0000001"), true) as AutoplayDecision.Resolve
    coordinator.manualSelection("next0000001")
    assertFalse(coordinator.isCurrent(next.token))
  }

  @Test fun triesAtMostThreeCandidatesAndStopsWithoutNetwork() {
    val coordinator = AutoplayCoordinator()
    assertEquals(AutoplayDecision.Stop, coordinator.onEnded("current0001", listOf("next0000001"), false))
    assertEquals(AutoplayDecision.Stop, coordinator.onEnded("current0001", listOf("next0000001"), true))
    coordinator.manualSelection("current0001")
    var decision = coordinator.onEnded("current0001", listOf("next0000001", "other000001", "third000001", "fourth00001"), true) as AutoplayDecision.Resolve
    decision = coordinator.failed(decision.token, true) as AutoplayDecision.Resolve
    assertEquals("other000001", decision.videoId)
    decision = coordinator.failed(decision.token, true) as AutoplayDecision.Resolve
    assertEquals("third000001", decision.videoId)
    assertEquals(AutoplayDecision.Stop, coordinator.failed(decision.token, true))
  }

  @Test fun staleFailureDoesNotCancelNewerPendingCandidate() {
    val coordinator = AutoplayCoordinator()
    val first = coordinator.onEnded("current0001", listOf("next0000001", "other000001"), true) as AutoplayDecision.Resolve
    val second = coordinator.failed(first.token, true) as AutoplayDecision.Resolve
    assertEquals(AutoplayDecision.Stop, coordinator.failed(first.token, true))
    assertTrue(coordinator.isCurrent(second.token))
    assertEquals("other000001", second.videoId)
  }

  @Test fun usesTrendingFallbackWhenRelatedCandidatesAreAllRecent() {
    val coordinator = AutoplayCoordinator()
    coordinator.manualSelection("current0001")
    val related = listOf("recent00001", "older000001")
    related.forEach(coordinator::rememberPlayed)
    val decision = coordinator.onEnded("current0001", related, true, listOf("trend000001"))
    assertTrue(decision is AutoplayDecision.Resolve)
    assertEquals("trend000001", (decision as AutoplayDecision.Resolve).videoId)
  }

  @Test fun countsOnlyFreshAutoplayCandidatesUpToAttemptLimit() {
    val coordinator = AutoplayCoordinator()
    coordinator.manualSelection("current0001")
    coordinator.rememberPlayed("recent00001")
    assertEquals(2, coordinator.freshCandidateCount("current0001", listOf("recent00001", "next0000001", "other000001")))
  }

  @Test fun stopCancelsPendingAutoplayResolution() {
    val coordinator = AutoplayCoordinator()
    val pending = coordinator.onEnded("current0001", listOf("next0000001"), true) as AutoplayDecision.Resolve
    coordinator.cancelPending()
    assertFalse(coordinator.isCurrent(pending.token))
    assertFalse(coordinator.completed(pending.token, pending.videoId))
    assertEquals(AutoplayDecision.Stop, coordinator.failed(pending.token, true))
  }

  @Test fun exhaustedRelatedCandidateCanContinueWithFallbackWithinThreeAttempts() {
    val coordinator = AutoplayCoordinator()
    val first = coordinator.onEnded("current0001", listOf("next0000001"), true) as AutoplayDecision.Resolve
    val fallback = coordinator.failed(first.token, true) as AutoplayDecision.NeedFallback
    val second = coordinator.supplyFallback(fallback.token, listOf("next0000001", "other000001"), true) as AutoplayDecision.Resolve
    assertEquals("other000001", second.videoId)
    val finalFallback = coordinator.failed(second.token, true) as AutoplayDecision.NeedFallback
    val third = coordinator.supplyFallback(finalFallback.token, listOf("last0000001"), true) as AutoplayDecision.Resolve
    assertEquals("last0000001", third.videoId)
    assertEquals(AutoplayDecision.Stop, coordinator.failed(third.token, true))
  }

  @Test fun avoidsFiftyRecentIdsButManualSelectionStartsNewSessionAndAllowsRepeat() {
    val coordinator = AutoplayCoordinator()
    coordinator.manualSelection("current0001")
    repeat(55) { index ->
      coordinator.rememberPlayed("v${index.toString().padStart(10, '0')}")
    }
    val decision = coordinator.onEnded("current0001", listOf("v0000000005", "v0000000000", "fresh000001"), true)
    assertTrue(decision is AutoplayDecision.Resolve)
    assertEquals("v0000000000", (decision as AutoplayDecision.Resolve).videoId)
    val retry = coordinator.failed(decision.token, true) as AutoplayDecision.Resolve
    assertEquals("fresh000001", retry.videoId)
    assertTrue(coordinator.completed(retry.token, "fresh000001"))
    coordinator.manualSelection("fresh000001")
    val replay = coordinator.onEnded("fresh000001", listOf("v0000000000"), true)
    assertTrue(replay is AutoplayDecision.Resolve)
  }
}
