# Playback Open Responsiveness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Open the native playback screen immediately on video selection while the playback service resolves the stream in the background.

**Architecture:** Keep ExoPlayer and stream URLs inside `PlaybackService`. The native module launches `PlaybackActivity` and submits a video ID to the service without resolving the stream; the service publishes a loading snapshot, resolves on its executor, and publishes success or retryable error. A request generation guards every result so only the latest selection can update playback.

**Tech Stack:** React Native 0.87, Kotlin, Android Media3, NewPipe Extractor, JUnit, Android instrumentation tests, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-15-playback-open-responsiveness-design.md`

## Global Constraints

- Android minimum API is 26; use the existing foreground service and MediaSession architecture.
- Keep stream URLs native; do not pass them through JavaScript.
- Keep one ExoPlayer owned by `PlaybackService`.
- Do not perform stream extraction or Room queries on the main thread.
- Preserve resume-position, history, favorites, background playback, orientation and PiP behavior.
- Retry only after a user action; do not add automatic unbounded retries or artificial loading delays.
- Test, build, inspect the emulator experience and commit each completed task separately.

## Review Focus

- Service startup and Activity connection can race; test that the initial snapshot still contains the pending video ID and loading state.
- A second selection can finish before the first; test that a stale success and stale failure cannot replace the latest selection.
- Extraction failure can leave stale title/recommendations visible; test clearing old state and exposing a retryable error.
- Saved position and nearly-finished video policy can regress when lookup moves into the service; test both branches.
- Android foreground-service startup can fail before the screen is usable; test bridge rejection and ensure the UI does not hang in loading.

---

### Task 1: Model pending playback state and latest-request outcomes

**Files:**
- Modify: `android/app/src/main/java/com/tubelite/PlaybackScreenState.kt`
- Create: `android/app/src/main/java/com/tubelite/PlaybackSelectionCoordinator.kt`
- Modify: `android/app/src/test/java/com/tubelite/PlaybackScreenStateTest.kt`
- Create: `android/app/src/test/java/com/tubelite/PlaybackSelectionCoordinatorTest.kt`
- Modify: `android/app/src/main/java/com/tubelite/PlaybackService.kt`

**Interfaces:**
- Consumes: existing `PlaybackRequestGeneration.next(id)` and `isCurrent(request, id)`.
- Produces: `PlaybackScreenState.selecting(id)` always reports `status = "loading"`; a failure transition stores `status = "error"` and a user-displayable error while retaining the selected video ID; retry returns to loading and clears the old error.

- [ ] **Step 1: Add failing state tests** for selecting A then B clearing metadata/recommendations and setting loading; failing the current selection sets retryable error; retry clears error and returns to loading.
- [ ] **Step 2: Run the focused test and confirm failure.**

Run: `cd android && ./gradlew testDebugUnitTest --tests com.tubelite.PlaybackScreenStateTest`

Expected: new assertions fail because selection does not yet set loading and failure/retry transitions are absent.

- [ ] **Step 3: Implement the state transitions** in `PlaybackScreenState.kt` without adding Android dependencies; keep retryability represented by `status == "error"` and non-empty `error`.
- [ ] **Step 4: Run the focused test and confirm it passes.**

Run: `cd android && ./gradlew testDebugUnitTest --tests com.tubelite.PlaybackScreenStateTest`

Expected: `BUILD SUCCESSFUL` and all `PlaybackScreenStateTest` cases pass.

- [ ] **Step 5: Commit.**

```bash
git add android/app/src/main/java/com/tubelite/PlaybackScreenState.kt android/app/src/test/java/com/tubelite/PlaybackScreenStateTest.kt
git commit -m "test: model pending playback state"
```

### Task 2: Launch the screen before stream extraction

**Files:**
- Modify: `android/app/src/main/java/com/tubelite/NativeVideoExtractorModule.kt`
- Modify: `android/app/src/main/java/com/tubelite/PlaybackService.kt`
- Modify: `android/app/src/main/java/com/tubelite/PlaybackScreenState.kt`
- Modify: `android/app/src/test/java/com/tubelite/PlaybackScreenStateTest.kt`
- Test: `android/app/src/androidTest/java/com/tubelite/PlaybackOpenResponsivenessTest.kt` (create)

**Interfaces:**
- Consumes: Task 1 loading/error state transitions.
- Produces: `openVideo(videoId, qualityId): Promise<Boolean>` resolves once Activity and service request have been launched, not when extraction completes. The service accepts a video ID, publishes loading immediately, reads saved progress and resolves NewPipe stream on its existing background resolver.

- [ ] **Step 1: Add a failing instrumentation test** that launches the playback flow with a valid video ID and asserts the Activity renders that ID and loading state before any stream is ready.
- [ ] **Step 2: Run the focused instrumentation test and confirm failure.**

Run: `cd android && ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tubelite.PlaybackOpenResponsivenessTest`

Expected: the test currently observes no Activity/pending ID until stream extraction is complete.

- [ ] **Step 3: Add deterministic resolver tests** by creating `PlaybackSelectionCoordinator.kt` and `PlaybackSelectionCoordinatorTest.kt`. The coordinator accepts a resolver function, tracks the selected ID/generation, emits loading before calling the resolver, and ignores both stale success and stale failure; tests use latches/fake results without network or global mutable hooks.
- [ ] **Step 4: Run the coordinator and state tests; confirm the new tests fail before implementation.**

Run: `cd android && ./gradlew testDebugUnitTest --tests com.tubelite.PlaybackSelectionCoordinatorTest --tests com.tubelite.PlaybackScreenStateTest`

Expected: new coordinator tests fail because the coordinator does not yet exist.

- [ ] **Step 5: Implement the coordinator** and wire it to `PlaybackService`'s existing background resolver. Read Room resume position on the service executor and preserve the V1 near-end restart rule.
- [ ] **Step 6: Refactor `openVideo()`** to validate the 11-character ID, launch `PlaybackActivity` with the selected ID, and start the foreground service with an explicit select-video action. Do not call `resolveVideo()` in this bridge method. Resolve/reject its Promise based on request acceptance/launch exceptions only. Initialize the Activity's pending ID from its launch intent so its first frame can display loading before MediaController connects.
- [ ] **Step 7: Apply only the current result**; on success record resolved metadata/history and install the stream at its saved position. On failure publish retryable error for that pending ID. Ensure stale successes and failures do not mutate current state.
- [ ] **Step 8: Run the focused instrumentation and unit tests.**

Run: `cd android && ./gradlew testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tubelite.PlaybackOpenResponsivenessTest`

Expected: `BUILD SUCCESSFUL`; pending Activity state is observable before resolver completion, followed by normal playback state.

- [ ] **Step 9: Commit.**

```bash
git add android/app/src/main/java/com/tubelite/NativeVideoExtractorModule.kt android/app/src/main/java/com/tubelite/PlaybackService.kt android/app/src/main/java/com/tubelite/PlaybackScreenState.kt android/app/src/main/java/com/tubelite/PlaybackSelectionCoordinator.kt android/app/src/test/java/com/tubelite/PlaybackScreenStateTest.kt android/app/src/test/java/com/tubelite/PlaybackSelectionCoordinatorTest.kt android/app/src/androidTest/java/com/tubelite/PlaybackOpenResponsivenessTest.kt
git commit -m "feat: open playback while resolving stream"
```

### Task 3: Render resolving and retry states in the playback screen

**Files:**
- Modify: `android/app/src/main/java/com/tubelite/PlaybackActivity.kt`
- Modify: `android/app/src/main/java/com/tubelite/PlaybackService.kt`
- Modify: `android/app/src/test/java/com/tubelite/PlaybackScreenStateTest.kt`
- Modify: `android/app/src/androidTest/java/com/tubelite/PlaybackOpenResponsivenessTest.kt`

**Interfaces:**
- Consumes: service snapshots with `videoId`, `status`, `title`, `channelTitle` and `error`; service select-video command.
- Produces: Activity displays **Đang tải video…** for loading and an inline retry action for error. Retry sends the selected video ID back to service, creating a new request generation.

- [ ] **Step 1: Add failing Activity state assertions** for loading text, error text and retry action in the existing playback screen instrumentation test.
- [ ] **Step 2: Run the focused instrumentation test and confirm failure.**

Run: `cd android && ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tubelite.PlaybackOpenResponsivenessTest`

Expected: the screen currently renders loading as paused/blank and has no extraction retry action.

- [ ] **Step 3: Render service loading/error snapshots** in `PlaybackActivity`; keep the video frame black while resolving, avoid stale metadata/recommendations, and expose an accessible retry control for error.
- [ ] **Step 4: Route retry through the existing MediaController custom-command path** to the service, using the current video ID and a new generation. Ensure Activity recreation reconstructs the same UI from a snapshot.
- [ ] **Step 5: Run focused unit and instrumentation tests.**

Run: `cd android && ./gradlew testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tubelite.PlaybackOpenResponsivenessTest`

Expected: `BUILD SUCCESSFUL`; loading, error, retry, and restored-snapshot assertions pass.

- [ ] **Step 6: Commit.**

```bash
git add android/app/src/main/java/com/tubelite/PlaybackActivity.kt android/app/src/main/java/com/tubelite/PlaybackService.kt android/app/src/test/java/com/tubelite/PlaybackScreenStateTest.kt android/app/src/androidTest/java/com/tubelite/PlaybackOpenResponsivenessTest.kt
git commit -m "feat: show playback loading and retry states"
```

### Task 4: Verify behavior on emulator and build APK

**Files:**
- Modify if needed: `android/app/src/androidTest/java/com/tubelite/PlaybackOpenResponsivenessTest.kt`
- Build artifact: `android/app/build/outputs/apk/release/app-release.apk` (ignored; do not commit)

**Interfaces:**
- Consumes: completed loading-first bridge and Activity/service states.
- Produces: verified debug/release builds, emulator evidence for separate screen-open and first-frame timings, and a release APK for user review.

- [ ] **Step 1: Run all unit tests and Android instrumentation tests.**

Run: `cd android && ./gradlew testDebugUnitTest connectedDebugAndroidTest`

Expected: `BUILD SUCCESSFUL` with no failed tests.

- [ ] **Step 2: Build debug and release APKs.**

Run: `cd android && ./gradlew assembleDebug assembleRelease`

Expected: `BUILD SUCCESSFUL`; release APK exists at `android/app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 3: Install release APK on `emulator-5554` and manually verify** opening from home, search, library and a YouTube link; test cold network, extraction failure/retry, choosing B while A is loading, resume position, Back, rotation, PiP and background playback.
- [ ] **Step 4: Record measurements** from tap to Activity visible and tap to first video frame separately. Confirm Activity appears before extraction completes; do not claim a fixed first-frame latency.
- [ ] **Step 5: Commit any final test adjustments** only after verification; keep APK and `.env` out of Git.

## Self-Review

- Spec coverage: immediate Activity launch and Promise semantics (Task 2); loading/error/retry and snapshot restoration (Tasks 1–3); latest-selection guard and recommendation clearing (Tasks 1–3); resume/history/favorites compatibility (Tasks 2 and 4); Android launch failures and user-visible evidence (Tasks 2–4).
- All plan interfaces use the existing `openVideo(videoId, qualityId)` bridge and MediaController service commands; stream URL remains native.
- The instrumentation test needs a controllable resolver seam. Add a package-private resolver interface or factory only for tests, with production default `NewPipeVideoRepository.resolveVideo`; avoid a global mutable test hook.
- The plan has four independently reviewable commits, with tests preceding implementation in each code task.
