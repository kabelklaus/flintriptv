# Phase G1 — ON_STOP/ON_START Surface-Handling

## Recon

**Setup:** `PlayerScreen.kt` renders video via `AndroidView { PlayerView(ctx).apply { player = exoPlayer; useController = false } }` (media3 `PlayerView`, not a raw `SurfaceView`). The `ExoPlayer` instance itself is created/owned in `rememberPlayerSetup()` (a `remember(credentials, audioDecoderMode)` block) and released in a `DisposableEffect(exoPlayer)` (`exoPlayer.release()` on dispose).

**Existing lifecycle handling (before this change):** exactly one `LifecycleEventObserver`, registered via `DisposableEffect(lifecycleOwner, exoPlayer)` at `PlayerScreen.kt:1256-1279` (line numbers pre-change). It reacts **only** to `ON_PAUSE`:
- sets `exoPlayer.playWhenReady = false`, calls `exoPlayer.pause()`
- saves watch progress (`saveWatchProgress(...)`) for non-live content

There is **no `ON_STOP` or `ON_START` handling anywhere** in `PlayerScreen.kt` or `PlayerViewModel.kt` — `PlayerViewModel` has no lifecycle awareness at all (it's a plain `ViewModel` with no `ExoPlayer`/`PlayerView` reference; the player lives entirely in Compose state inside `PlayerScreen`). `PlayerView.onPause()`/`onResume()` (the media3-recommended surface-teardown hooks for API < 24) are never called either.

**Root-cause assessment (Standby/Wake "Decoder-Zombie" auf MiBox/Amlogic):**
On standby, Android delivers `ON_PAUSE` → `ON_STOP` in sequence. Today's code pauses playback and saves progress on `ON_PAUSE`, which is correct, but does nothing further on `ON_STOP`. The window's `Surface` backing `PlayerView`'s internal `SurfaceView` is torn down by the OS during standby; ExoPlayer normally learns about this via the `SurfaceHolder.Callback.surfaceDestroyed` callback that `PlayerView`/`SurfaceView` wires up internally, which calls `clearVideoSurface()` on the player. On some Amlogic/MiBox firmware this callback either fires too late or not at all during a deep-standby suspend, so the decoder stays configured against a surface that no longer exists. On wake (`ON_START`), a *new* `Surface` is created for the re-shown window, but since nothing in the app proactively clears/reattaches the video surface, the player is left rendering into the stale surface reference (or not re-attached to the new one) — audio output is unaffected (it doesn't depend on the surface) so sound continues while video is frozen on the last decoded frame. This matches Chris's observation exactly.

**Conclusion:** the fix must not rely on the OS/ExoPlayer surface-destroyed notification. Explicitly detach the video surface on `ON_STOP` and explicitly reattach it on `ON_START`, independent of whatever the OS surface-callback timing does.

**Resume-position bestandsverhalten:** watch progress is restored today via `WatchProgressService` + `restoreWatchProgress()`, driven off `STATE_READY` (`PlayerProgressRestoreEffect`) — this only runs once per `DisposableEffect(exoPlayer, streamUrl)` (i.e. on a genuinely new stream/media item), not on every `ON_START`. Since our fix only detaches the *surface* (never calls `exoPlayer.stop()`/`clearMediaItems()`/releases the player), `exoPlayer.currentPosition` is preserved natively by ExoPlayer across the standby cycle — no explicit resume-position code was needed or added for the ON_STOP/ON_START path itself.

## Implementation

New file `app/src/main/java/com/flintr/tv/ui/screens/PlayerSurfaceLifecycleObserver.kt`:
- Small standalone `LifecycleEventObserver` (`PlayerSurfaceLifecycleObserver`), independent of the existing ON_PAUSE observer, taking `() -> Player?` and `() -> PlayerView?` suppliers (not the concrete `ExoPlayer`/PlayerScreen state directly) so it's unit-testable without Compose/Hilt/Robolectric UI harness.
- `ON_STOP`: records `wasPlayingBeforeStop = player.isPlaying`, calls `player.pause()`, then `playerView?.player = null` (detaches/releases the video surface from the player — media3's `PlayerView.setPlayer` clears the old player's video surface internally).
- `ON_START`: reattaches via `playerView?.player = player`, then calls `player.play()` **only if** `wasPlayingBeforeStop` was true. No new autoplay behavior is invented — if playback was already paused before standby (e.g. user paused, or backgrounded via `ON_PAUSE` without having been playing), it stays paused after wake.

Changes in `app/src/main/java/com/flintr/tv/ui/screens/PlayerScreen.kt`:
- Added `var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }` and set it in the `AndroidView` factory (`playerViewRef = this` alongside `player = exoPlayer`).
- Added a second `DisposableEffect(lifecycleOwner, exoPlayer)` next to the existing ON_PAUSE one, constructing a `remember`ed `PlayerSurfaceLifecycleObserver { exoPlayer }, { playerViewRef }` and registering/unregistering it on the same `lifecycleOwner`.
- No changes to the existing ON_PAUSE observer, no restructuring of the screen/composable layout.

## Tests

New file `app/src/test/java/com/flintr/tv/ui/screens/PlayerSurfaceLifecycleObserverTest.kt` (plain JUnit + mockk, `Player`/`PlayerView` mocked — no Robolectric/Compose needed since the observer only depends on the `Player` interface and `PlayerView`):
- `ON_STOP` while playing → `pause()` called, surface released (`playerView.player = null`).
- `ON_STOP` while already paused → surface still released, no spurious `play()` later.
- `ON_START` after an `ON_STOP` that occurred while playing → surface reattached (`playerView.player = <player>`) **and** `play()` called.
- `ON_START` after an `ON_STOP` that occurred while paused → surface reattached, `play()` **not** called (no invented autoplay).
- `ON_START` with no prior `ON_STOP` → no crash, `play()` not called (safe default `wasPlayingBeforeStop = false`).
- Two consecutive Standby/Wake cycles with different playing-state each time → each cycle's resume decision is independent (state doesn't leak from the previous cycle).
- Other lifecycle events (`ON_PAUSE`, `ON_RESUME`, `ON_CREATE`, `ON_DESTROY`) are no-ops for this observer.
- `player() == null` guard: observer doesn't throw if the player supplier returns null (e.g. torn down concurrently).

## Test results

Commands run:
```
sh gradlew :app:compileDebugKotlin --no-daemon
sh gradlew :app:testDebugUnitTest --no-daemon --tests "com.flintr.tv.ui.screens.PlayerSurfaceLifecycleObserverTest" --tests "com.flintr.tv.viewmodel.PlayerViewModelTest"
```
Results:
- `:app:compileDebugKotlin` → BUILD SUCCESSFUL
- `:app:testDebugUnitTest` (filtered) → BUILD SUCCESSFUL
  - `PlayerSurfaceLifecycleObserverTest`: 8 tests, 0 failures, 0 errors, 0 skipped
  - `PlayerViewModelTest`: 29 tests, 0 failures, 0 errors, 0 skipped (unaffected regression check)

## Was am Gerät (MiBox .34) zu verifizieren ist

Box war während dieser Session aus — folgendes muss der Orchestrator am Gerät nachprüfen:
1. Standby während aktiver Wiedergabe auslösen, warten (echter Deep-Standby, nicht nur Screen-off), Wake → Video muss innerhalb weniger Sekunden weiterlaufen (kein Standbild, kein Ton-ohne-Bild).
2. Standby während pausierter Wiedergabe (z. B. User hat vorher pausiert) → nach Wake bleibt pausiert (kein Auto-Play).
3. Mehrfache Standby/Wake-Zyklen hintereinander (Regressionscheck auf Zustands-Leck zwischen Zyklen).
4. Live-TV-Stream: Standby/Wake — prüfen ob Re-Attach auch bei Live-Inhalten (kein `WatchProgressService`-Pfad) sauber funktioniert.
5. Prüfen ob `PlayerView`-Neuzuweisung (`playerView.player = player`) auf dem Amlogic-Decoder tatsächlich einen sauberen Decoder-Reset auslöst, oder ob zusätzlich ein `exoPlayer.seekTo(exoPlayer.currentPosition)` nötig ist, falls der Deocder trotzdem einfriert (in dem Fall: Folge-Ticket, nicht in dieser Phase).

## Abweichungen

- Kein Test mit echtem Robolectric-Lifecycle/Compose-Harness für `PlayerScreen` selbst (kein bestehendes Compose-UI-Testsetup für diesen Screen im Repo, `PlayerScreen` ist eine sehr große private-Composable-Funktion ohne Test-Seams für Hilt/WatchProgressService). Stattdessen wurde die ON_STOP/ON_START-Logik in einen eigenständigen, von Compose/Hilt unabhängigen `LifecycleEventObserver` extrahiert (`PlayerSurfaceLifecycleObserver`), der direkt unit-testbar ist — das erfüllt die "eigener LifecycleObserver"-Vorgabe und macht das Verhalten testbar, ohne die Screen-Struktur umzubauen.
- `PlayerViewModel.kt` wurde nicht verändert — die Recon ergab, dass der Player/das Surface-Handling vollständig in `PlayerScreen.kt` lebt, nicht im ViewModel; eine Verlagerung dorthin hätte einen größeren Umbau bedeutet, der explizit nicht gefordert war.
