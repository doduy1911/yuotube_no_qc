# Instant Playback and Mini-player Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Open playback immediately with an explicit loading state, then let users swipe it down into a persistent in-app mini-player while the home tab is shown.

**Architecture:** Keep one ExoPlayer and MediaSession in `PlaybackService`. Launch `PlaybackActivity` before stream resolution, publish loading/error state from the service, and render a compact native player surface in `MainActivity` when playback is collapsed. A native-to-JS event switches the React Native app to Home; reopening the mini-player brings the full playback Activity back over the same session.

**Tech Stack:** React Native 0.87, TypeScript, Kotlin, AndroidX Media3, Jest, JUnit, Android instrumentation tests, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-20-instant-playback-and-mini-player-design.md`

## Global Constraints

- Keep exactly one ExoPlayer and one MediaSession, both owned by `PlaybackService`.
- Do not pass stream URLs from native code into JavaScript.
- Show `Đang tải video…` while resolving; show a retryable error if resolution fails.
- Apply only the latest video selection or retry generation.
- Place the mini-player above the bottom tab bar and allow touches outside it to reach the React Native screen.
- Closing the mini-player stops the current playback session; collapsing it keeps playback running.
- Preserve PiP, background playback, autoplay, history, favorites, and saved-position behavior.
- Do not add dependencies unless the existing Android/React Native APIs cannot meet the spec.

## Review Focus

- Selecting video B while A is resolving must ignore both stale success and stale failure from A; cover in `PlaybackSelectionCoordinatorTest`.
- An invalid video ID or failed Activity/service launch must reject promptly instead of leaving an indefinite loading UI; cover in bridge/instrumentation tests.
- A loading or failed stream must remain visible and retryable after collapsing and expanding; cover in `MiniPlayerHostTest` and instrumentation tests.
- Surface reattachment during Activity transitions must not release the service player or leave two active player surfaces; cover with playback host instrumentation and session identity assertions.
- Gesture detection must not turn seek, horizontal scroll, control tap, or rotation into collapse; cover in `PlaybackGesturePolicyTest` and activity instrumentation.

---

### Task 1: Model playback selection and immediate loading state

**Files:**
- Modify: `android/app/src/main/java/com/tubelite/PlaybackScreenState.kt`
- Create: `android/app/src/main/java/com/tubelite/PlaybackSelectionCoordinator.kt`
- Modify: `android/app/src/main/java/com/tubelite/PlaybackService.kt`
- Test: `android/app/src/test/java/com/tubelite/PlaybackScreenStateTest.kt`
- Create: `android/app/src/test/java/com/tubelite/PlaybackSelectionCoordinatorTest.kt`

**Interfaces:**
- Consumes: Existing `ResolvedVideoStream`, `PlaybackScreenState`, and `NewPipeVideoRepository.resolveVideo(videoId, qualityId)`.
- Produces: `PlaybackSelectionCoordinator.select(videoId, qualityId, resolver, onState, onSuccess)` publishes loading immediately, resolves off the main thread, and ignores callbacks from stale generations. Service snapshots include pending video ID, status, and user-displayable error.

- [x] **Step 1: Add failing state tests** for selecting a new video entering `loading`, clearing stale metadata/recommendations, and failure/retry transitions retaining the selected video ID.
- [x] **Step 2: Add failing coordinator tests** for immediate loading publication, latest-selection success, stale success suppression, and stale failure suppression using controllable fake resolvers.
- [x] **Step 3: Run the focused unit tests and confirm the new assertions fail.**

Run: `cd android && ./gradlew testDebugUnitTest --tests com.tubelite.PlaybackScreenStateTest --tests com.tubelite.PlaybackSelectionCoordinatorTest`

Expected: new loading/retry/coordinator assertions fail against current behavior.

- [x] **Step 4: Implement the state transitions and generation coordinator** without Android dependencies in the coordinator; production resolver runs on the service's existing background executor.
- [x] **Step 5: Re-run the focused unit tests and confirm they pass.**
- [x] **Step 6: Commit the independently testable state/coordinator change.**

### Task 2: Open playback before resolution and render loading/error/retry

**Files:**
- Modify: `android/app/src/main/java/com/tubelite/NativeVideoExtractorModule.kt`
- Modify: `android/app/src/main/java/com/tubelite/PlaybackService.kt`
- Modify: `android/app/src/main/java/com/tubelite/PlaybackActivity.kt`
- Modify: `android/app/src/main/java/com/tubelite/PlaybackScreenState.kt`
- Test: `android/app/src/androidTest/java/com/tubelite/PlaybackOpenResponsivenessTest.kt` (create)
- Test: `android/app/src/androidTest/java/com/tubelite/PlaybackActivityLayoutTest.kt`

**Interfaces:**
- Consumes: Task 1 coordinator and service snapshot contract.
- Produces: `openVideo(videoId, qualityId): Promise<Boolean>` resolves after the Activity and service request are accepted, not after extraction. `PlaybackActivity` renders loading/error/retry from the service snapshot and restores the same state after recreation.

- [x] **Step 1: Add an instrumentation test** with a controllable delayed resolver; assert the playback Activity and `Đang tải video…` are visible before resolution completes.
- [x] **Step 2: Add tests** for inline error/retry, invalid IDs, and ensuring a failed bridge launch returns an error without hanging.
- [x] **Step 3: Run the focused instrumentation tests and confirm they fail.**

Run: `cd android && ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tubelite.PlaybackOpenResponsivenessTest`

Expected: current code waits for extraction before showing the Activity and has no retry state.

- [x] **Step 4: Refactor `openVideo()`** to validate the video ID, launch the Activity with pending selection state, and send the request to the service without calling NewPipe or Room on the bridge executor path.
- [x] **Step 5: Wire service selection to the coordinator**, read saved position on the service executor, record history only after successful resolution, and publish retryable errors on failure.
- [x] **Step 6: Render loading/error/retry in `PlaybackActivity`** and route retry through a service command that creates a new request generation.
- [x] **Step 7: Run focused unit and instrumentation tests.**

Run: `cd android && ./gradlew testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tubelite.PlaybackOpenResponsivenessTest`

Expected: Activity shows loading before the fake resolver completes; success, retry, invalid-ID, stale-result, and restoration assertions pass.

- [x] **Step 8: Commit immediate-open and loading/retry behavior.**

### Task 3: Add collapse/expand state and downward gesture policy

**Files:**
- Create: `android/app/src/main/java/com/tubelite/PlaybackGesturePolicy.kt`
- Modify: `android/app/src/main/java/com/tubelite/PlaybackActivity.kt`
- Modify: `android/app/src/main/java/com/tubelite/PlaybackService.kt`
- Test: `android/app/src/test/java/com/tubelite/PlaybackGesturePolicyTest.kt` (create)
- Test: `android/app/src/androidTest/java/com/tubelite/PlaybackActivityLayoutTest.kt`

**Interfaces:**
- Consumes: Existing playback Activity and Task 2 service snapshots.
- Produces: `PlaybackGesturePolicy.onTouch(startX, startY, endX, endY, touchRegion, threshold): GestureDecision` distinguishes downward collapse from taps, horizontal swipes, and seek/control interactions. `PlaybackService.ACTION_CLOSE_PLAYBACK` stops and clears the active session; collapse does not stop playback.

- [x] **Step 1: Add failing policy tests** for downward collapse from video region, rejection of horizontal movement, taps, seek/control area gestures, and gestures below threshold.
- [x] **Step 2: Run the focused unit test and confirm it fails.**

Run: `cd android && ./gradlew testDebugUnitTest --tests com.tubelite.PlaybackGesturePolicyTest`

- [x] **Step 3: Implement the pure gesture policy** and connect it to the video container without intercepting PlayerView controls or fullscreen actions.
- [x] **Step 4: Add instrumentation assertions** that a qualifying swipe collapses once, while seek/control interaction does not collapse.
- [x] **Step 5: Run focused unit and instrumentation tests and confirm they pass.**
- [x] **Step 6: Commit gesture and playback close behavior.**

### Task 4: Host the mini-player in MainActivity and return to Home

**Files:**
- Modify: `android/app/src/main/java/com/tubelite/MainActivity.kt`
- Create: `android/app/src/main/java/com/tubelite/MiniPlayerHost.kt`
- Create: `android/app/src/main/java/com/tubelite/MiniPlayerHostState.kt`
- Modify: `android/app/src/main/java/com/tubelite/PlaybackActivity.kt`
- Modify: `android/app/src/main/java/com/tubelite/PlaybackService.kt`
- Create: `android/app/src/main/java/com/tubelite/PlaybackUiEvents.kt`
- Modify: `App.tsx`
- Create: `android/app/src/test/java/com/tubelite/MiniPlayerHostStateTest.kt`
- Test: `android/app/src/androidTest/java/com/tubelite/MiniPlayerHostTest.kt` (create)
- Test: `App.test.tsx` (create)

**Interfaces:**
- Consumes: Task 3 collapse/close actions and service snapshot.
- Produces: `MiniPlayerHost.show()` attaches a compact `PlayerView`/controls to `MainActivity` above the app bottom bar; `hide()` removes it; `expand()` launches `PlaybackActivity` for the current service session. `PlaybackUiEvents` emits `miniPlayerCollapsed` and `miniPlayerClosed` through React Native's device event emitter; `App.tsx` selects Home after collapse and refreshes library state after close.

- [x] **Step 1: Add failing `MiniPlayerHostStateTest` cases** for collapsed/expanded/closed state, snapshot restoration, and rejecting duplicate host attachment.
- [x] **Step 2: Add an instrumentation test** that collapses playback, asserts MainActivity/Home is visible with the mini-player, and verifies the same `MediaSession` remains connected.
- [x] **Step 3: Implement MainActivity host lifecycle** using an in-app overlay attached to the activity content root; keep outside touches pass-through and position it above the React Native bottom tabs.
- [x] **Step 4: Bind mini-player video surface and controls** to the existing MediaController; implement play/pause, close, and tap-to-expand without creating another player.
- [x] **Step 5: Wire collapse and expand** so Activity transitions detach/attach surfaces in lifecycle-safe order and loading/error state remains available.
- [x] **Step 6: Emit `miniPlayerCollapsed` and `miniPlayerClosed` with `DeviceEventManagerModule.RCTDeviceEventEmitter`; subscribe via `DeviceEventEmitter` in `App.tsx` to switch to Home after collapse and refresh library state after close.**
- [x] **Step 7: Add `App.test.tsx`** with the existing `react-test-renderer` setup; emit each device event and assert the active tab/content update.
- [x] **Step 8: Run focused host, bridge, and instrumentation tests.**

Run: `cd android && ./gradlew testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tubelite.MiniPlayerHostTest`

Run: `npm test -- --runInBand`

Expected: Home responds outside the overlay; mini-player controls work; expand returns to the same video/position; close stops playback and removes the overlay.

- [x] **Step 9: Commit mini-player host and navigation integration.**

### Task 5: Verify full app behavior and build APKs

**Files:**
- Modify if needed: tests introduced in Tasks 1–4.
- Build artifacts: `android/app/build/outputs/apk/debug/app-debug.apk` and `android/app/build/outputs/apk/release/app-release.apk` (ignored; do not commit).

**Interfaces:**
- Consumes: Completed immediate-open, service state, gesture policy, and MainActivity mini-player host.
- Produces: Full automated verification, debug/release APKs, and emulator evidence for loading-first playback, collapse/expand, controls, and preserved playback features.

- [x] **Step 1: Run all JavaScript checks.**

Run: `npm test -- --runInBand`

Run: `npm run lint`

Run: `npx tsc --noEmit`

Expected: all Jest tests pass, ESLint exits successfully, and TypeScript reports no errors.

- [x] **Step 2: Run all Android tests.**

Run: `cd android && ./gradlew testDebugUnitTest connectedDebugAndroidTest`

Expected: `BUILD SUCCESSFUL` with all unit and instrumentation tests passing on the running emulator.

- [x] **Step 3: Build debug and release APKs.**

Run: `cd android && ./gradlew assembleDebug assembleRelease`

Expected: `BUILD SUCCESSFUL` and both APKs exist at the paths above.

- [x] **Step 4: Install and smoke-check on emulator.** Installed and launched the release APK on Pixel 7; instrumentation tests cover pending loading/error/retry, gesture, collapse, mini-player controls and host navigation. Real NewPipe playback and network-dependent scenarios were not manually exercised.
- [x] **Step 5: Record limitations and commit the final implementation.** Release/debug builds and automated checks passed. ESLint reports 8 existing `no-void` warnings; the emulator smoke check did not validate a live YouTube stream.

## Self-Review

- Spec coverage: immediate loading/open and latest-result safety (Tasks 1–2); error/retry/restoration (Tasks 1–2 and 4); swipe policy and touch conflicts (Task 3); mini-player, Home navigation, one-session surface handoff, controls and close (Task 4); preservation of PiP/background/autoplay/history/favorites/resume and debug/release verification (Task 5).
- Cross-task contracts use service snapshots and `MediaController`; stream URLs remain native.
- Host surface handoff is tested explicitly because it is the highest-risk lifecycle boundary.
- Native-to-JS events are `miniPlayerCollapsed` and `miniPlayerClosed`; Task 4 uses React Native's device event emitter, so no additional native module registration is needed.
- The plan keeps tests deterministic with fake resolvers for loading races, and reserves real NewPipe playback for emulator smoke verification.
