### 1: DONE

**Datei:** `app/src/androidTest/java/com/flintr/tv/ui/components/player/VideoSettingsDialogScreenshotTest.kt`

**Signatur/Parameter genutzt:**
`VideoSettingsDialog` ist `public` (`ui/components/player/VideoSettingsDialog.kt:332`) — keine Sichtbarkeitsänderung nötig. Aufruf im Test:

```kotlin
VideoSettingsDialog(onDismiss = {})
```

Alle anderen Parameter (`exoPlayer`, `playerView`, `playbackSettingsRepository`, `onAudioTrackOverridden`) haben Defaults (`null` / No-op-Lambda) und werden nicht überschrieben — die Composable fällt dadurch auf ihre eingebauten Default-Settings-Werte zurück (`VideoQuality.Auto`, `VideoResizeMode.FIT`, `autoPlay=false`, `rememberPosition=true`, `subtitlesEnabled=false`, `subtitleLanguage=Deutsch`, `audioLanguage=Deutsch`), da `playbackSettingsRepository == null`. Das ist ein realistischer "Dialog gerade geöffnet, kein Player aktiv"-Zustand (Audio/Untertitel-Track-Sektionen zeigen "Kein Player aktiv").

**Rendering:**
- `createComposeRule()` (gleiches Pattern wie `HomeSidebarFocusTest.kt`, kein `ComponentActivity`-Generic nötig)
- Content in `FlintRTVTheme { ... }` gewrappt (Dark-Theme, wie im Rest der App)
- Dialog ist ein echter `androidx.compose.ui.window.Dialog` — in Compose-UI-Tests wird dessen Root automatisch als zusätzlicher "Root" registriert und ist über `isDialog()`-Matcher ansprechbar, kein Extraktions-Workaround nötig
- Capture: `composeTestRule.onNode(isDialog()).captureToImage()` → `.asAndroidBitmap()` → PNG

**PNG-Pfad (abweichend von Vorgabe — siehe NOTES):**
`context.getExternalFilesDir(null)/video_settings_dialog.png`
→ auf dem Emulator konkret: `/sdcard/Android/data/com.flintr.tv.debug/files/video_settings_dialog.png`
(Abruf: `adb pull /sdcard/Android/data/com.flintr.tv.debug/files/video_settings_dialog.png`)

**Assertion:** `onNodeWithText("Video-Einstellungen").assertExists()` vor dem Capture — Test schlägt fehl, wenn der Dialog nicht rendert.

---

**DONE:**
- Testdatei erstellt, keine bestehenden Dateien verändert.
- Keine Gradle-Dependency-Änderung nötig: `androidTestImplementation(libs.compose.ui.test.junit4)` + `platform(libs.compose.bom)` sind in `app/build.gradle.kts:261-264` bereits vorhanden. `createComposeRule()`/`captureToImage()`/`isDialog()` sind alle Teil von `ui-test-junit4`.
- Nicht committet (wie gefordert).
- Nicht gebaut/ausgeführt (Tirith blockiert Gradle in dieser Session) — Operator muss ausführen:
  `./gradlew connectedDebugAndroidTest --tests "com.flintr.tv.ui.components.player.VideoSettingsDialogScreenshotTest"`

**NOTES:**
- **Pfad-Abweichung von der Vorgabe:** `/sdcard/Download/...` bzw. `/sdcard/...` direkt zu beschreiben scheitert ab `targetSdk 29` (hier `targetSdk = 36`, `app/build.gradle.kts:29`) ohne `WRITE_EXTERNAL_STORAGE`/`MANAGE_EXTERNAL_STORAGE`-Permission oder MediaStore-API — keine davon ist im Manifest vorhanden, und ich habe wie angewiesen keine Permissions ergänzt. Stattdessen `context.getExternalFilesDir(null)` genutzt (App-eigener External-Storage-Bereich, kein Permission-Bedarf, per `adb pull` abrufbar). Falls zwingend `/sdcard/Download/` gewünscht ist, müsste entweder die `WRITE_EXTERNAL_STORAGE`-Permission (+ `requestLegacyExternalStorage` oder `MediaStore.insert`) ergänzt werden — bitte vorher absegnen.
- Keine fehlenden Dependencies, keine private-Composable-Probleme.
- `isDialog()`/`captureToImage()` sind etablierte, stabile `androidx.compose.ui.test`-APIs seit Compose 1.1 — mit der hier gepinnten BOM `2023.10.01` (→ Compose UI ~1.5.4) sicher verfügbar; lokal nicht gegen die tatsächliche JAR verifizierbar, da der Gradle-Cache außerhalb des erlaubten Arbeitsverzeichnisses liegt (Sandbox-Grenze).
- Ungetestet, da kein Gradle-Zugriff in dieser Session — bitte vom Operator auf dem Emulator verifizieren und PNG-Optik gegenprüfen (insbesondere die neue 75%-Breite).
