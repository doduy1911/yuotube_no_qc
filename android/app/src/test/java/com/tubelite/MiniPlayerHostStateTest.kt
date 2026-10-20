package com.tubelite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerHostStateTest {
  @Test fun collapseKeepsVideoIdentityAndEnablesSingleHostAttachment() {
    val collapsed = MiniPlayerHostState().collapse("dQw4w9WgXcQ")
    assertEquals("dQw4w9WgXcQ", collapsed.videoId)
    assertEquals(MiniPlayerMode.COLLAPSED, collapsed.mode)
    assertTrue(collapsed.shouldAttach)
    assertFalse(collapsed.attached().collapse("dQw4w9WgXcQ").shouldAttachAgain)
  }

  @Test fun expandAndCloseHideOverlayWhileRetainingVideoUntilClose() {
    val collapsed = MiniPlayerHostState().collapse("dQw4w9WgXcQ")
    val expanded = collapsed.expand()
    assertEquals("dQw4w9WgXcQ", expanded.videoId)
    assertEquals(MiniPlayerMode.EXPANDED, expanded.mode)
    assertFalse(expanded.shouldAttach)
    val closed = collapsed.close()
    assertEquals("", closed.videoId)
    assertEquals(MiniPlayerMode.CLOSED, closed.mode)
    assertFalse(closed.shouldAttach)
  }

  @Test fun emptyVideoCannotShowMiniPlayer() {
    assertFalse(MiniPlayerHostState().collapse("").shouldAttach)
  }
}
