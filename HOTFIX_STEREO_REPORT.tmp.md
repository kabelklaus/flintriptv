DONE:
- Wired `createStereoDownmixProcessor()` into `PlayerScreen.kt`'s `createExoPlayer`: `renderersFactory` is now `object : DefaultRenderersFactory(context)` overriding `buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams): AudioSink`, building a `DefaultAudioSink` with `setAudioProcessors(arrayOf(createStereoDownmixProcessor()))`. Added imports `androidx.media3.exoplayer.audio.AudioSink` and `androidx.media3.exoplayer.audio.DefaultAudioSink` (`DefaultRenderersFactory` was already imported). No separate import needed for `createStereoDownmixProcessor` — `PlayerScreen.kt` already has `import com.flintr.tv.ui.components.player.*`.
- The existing `.apply { setEnableDecoderFallback(true); when (audioDecoderMode) ... }` block is unchanged, now chained off the anonymous object instead of the plain `DefaultRenderersFactory(context)`.
- Version bump in `app/build.gradle.kts`: `versionCode = 109`, `versionName = "2.49.2"` (was 108 / 2.49.1).
- Committed and pushed to `DEV`: commit `bfc5202`.
- `VideoSettingsDialog.kt`'s pre-existing uncommitted change was left untouched (not staged, not committed).

NOTES:
- Only the 4 intended files were staged (`StereoDownmix.kt`, `StereoDownmixTest.kt`, `PlayerScreen.kt`, `app/build.gradle.kts`) via explicit `git add <paths>`, no `-A`/`.`.
- No PR created, no merge, no tags — per instructions, that's the operator's job.

TESTS:
- `sh gradlew :app:compileDebugKotlin --no-daemon` → BUILD SUCCESSFUL.
- `sh gradlew :app:testDebugUnitTest --no-daemon --tests '*StereoDownmix*' --rerun` → BUILD SUCCESSFUL, 6/6 tests passed, 0 failures (`TEST-com.flintr.tv.StereoDownmixTest.xml`: tests="6" failures="0" errors="0").
