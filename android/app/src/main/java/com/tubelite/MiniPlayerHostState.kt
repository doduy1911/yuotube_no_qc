package com.tubelite

internal enum class MiniPlayerMode { HIDDEN, COLLAPSED, EXPANDED, CLOSED }

internal data class MiniPlayerHostState(
  val mode: MiniPlayerMode = MiniPlayerMode.HIDDEN,
  val videoId: String = "",
  private val attachmentRevision: Int = 0,
  private val attachedRevision: Int = -1,
) {
  val shouldAttach: Boolean get() = mode == MiniPlayerMode.COLLAPSED && videoId.isNotBlank()
  val shouldAttachAgain: Boolean get() = shouldAttach && attachmentRevision != attachedRevision

  fun collapse(id: String): MiniPlayerHostState {
    if (id.isBlank()) return close()
    if (mode == MiniPlayerMode.COLLAPSED && videoId == id) return this
    return copy(mode = MiniPlayerMode.COLLAPSED, videoId = id, attachmentRevision = attachmentRevision + 1)
  }

  fun attached(): MiniPlayerHostState = copy(attachedRevision = attachmentRevision)
  fun selectVideo(id: String): MiniPlayerHostState = if (id.isBlank()) this else copy(videoId = id)
  fun expand(): MiniPlayerHostState = copy(mode = MiniPlayerMode.EXPANDED)
  fun close(): MiniPlayerHostState = copy(mode = MiniPlayerMode.CLOSED, videoId = "", attachmentRevision = attachmentRevision + 1)
}
