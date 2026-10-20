package com.tubelite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class PlaybackSelectionCoordinatorTest {
  @Test fun publishesLoadingBeforeResolverRuns() {
    val tasks = mutableListOf<() -> Unit>()
    val coordinator = PlaybackSelectionCoordinator<String>(Executor { task -> tasks.add { task.run() } })
    val states = mutableListOf<PlaybackScreenState>()
    var resolved = false
    coordinator.select("video000001", "", { _, _ -> resolved = true; "stream" }, { states.add(it) }, { })
    assertEquals("loading", states.single().status)
    assertFalse(resolved)
    tasks.single().invoke()
    assertTrue(resolved)
  }

  @Test fun ignoresStaleSuccessAndFailureAfterNewSelection() {
    val tasks = mutableListOf<() -> Unit>()
    val coordinator = PlaybackSelectionCoordinator<String>(Executor { task -> tasks.add { task.run() } })
    val states = mutableListOf<PlaybackScreenState>()
    val success = mutableListOf<String>()
    coordinator.select("video000001", "", { _, _ -> "A" }, { states.add(it) }, { success.add(it) })
    coordinator.select("video000002", "", { _, _ -> "B" }, { states.add(it) }, { success.add(it) })
    tasks[0].invoke()
    tasks[1].invoke()
    assertEquals(listOf("B"), success)
    assertTrue(states.all { it.videoId != "video000001" || it.status == "loading" })
    assertEquals("video000002", states.last().videoId)
  }

  @Test fun publishesErrorOnlyForCurrentFailure() {
    val tasks = mutableListOf<() -> Unit>()
    val coordinator = PlaybackSelectionCoordinator<String>(Executor { task -> tasks.add { task.run() } })
    val states = mutableListOf<PlaybackScreenState>()
    coordinator.select("video000001", "", { _, _ -> error("A failed") }, { states.add(it) }, { })
    coordinator.select("video000002", "", { _, _ -> "B" }, { states.add(it) }, { })
    tasks[0].invoke()
    tasks[1].invoke()
    assertFalse(states.any { it.status == "error" })
    assertEquals("video000002", states.last().videoId)
  }
}
