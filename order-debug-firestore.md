# Order: flintrTV Debug-Build — Firestore tot seit v2.48, Infinite Spinner

## WICHTIGE Rahmenbedingungen

- Arbeitsverzeichnis: `~/Nextcloud/flintrtv` (absolute: `/Users/openclaw/Nextcloud/flintrtv`)
- **KEIN `.git` in diesem Ordner** (Nextcloud-Sync) — keine git-Commands, kein commit/push. Nur Dateien ändern.
- Build immer mit: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`, dann `sh gradlew <task> --no-daemon` (gradlew ist NICHT ausführbar, macOS hat kein `timeout`-Binary).
- Volle Test-Suite läuft zentral (Hermes-Gate) — du machst NUR `:app:compileDebugKotlin` + gezielte `--tests`-Filter-Läufe.
- `local.properties` enthält keystore.* und github.token — NIEMALS Werte ausgeben oder committen.
- Guardrails dürfen nicht brechen: `SecurityGuardrailsTest` (kein Body-Logging, Cleartext bleibt AN), `UserRepositoryGuardrailsTest` (nur `set()` mit `SetOptions.merge()`).
- versionName/versionCode NICHT ändern (nur Debug-Build, kein Release).
- Die local.properties wird von dir nicht angefasst.

## Problem (live auf Xiaomi MiBox 192.168.2.34, Android 14, per ADB diagnostiziert)

App-ID `com.flintr.tv`, Debug-Build hat Suffix `.debug` (applicationId `com.flintr.tv.debug`, App-Name „flintrTV Debug").

**Symptom:** flintrTV Debug v2.48 (versionCode 106, installiert heute 13:30) hängt beim Start im Endlos-Spinner. Kategorie "Favoriten" bleibt leer (Count 0). Kein Fehlerdialog.

**Reproduzierte Log-Evidenz (2 Läufe, identisch):**
1. Beim Start: `FavoritesRepository: Credentials cleared, resetting favorites` (+ WatchedHistory/WatchProgress/CategoryVisibility gleich) — das ist der initiale credentialsFlow-Impuls mit null.
2. `MainViewModel: Loading initial data...` → 25 s später `Server info loaded successfully` (Xtream-API via synchronem `credentialsRepository.getCredentials()` funktioniert!).
3. Danach: `Loading data from cache` (52 MB Katalog-Cache) → `Initial data loading finished.`
4. **FEHLT komplett:** `Username saved to Firestore on app start` (MainViewModel.saveUsernameIfNeeded) und `Loaded favorites from Firestore: N` (FavoritesRepository.loadFromFirestore). Kein Firestore-Log überhaupt.
5. Kein einziger Xtream-Katalog-Request (okhttp zeigt nur den GitHub-Update-Check).
6. Debug-only im Logcat: `GoogleApiManager: DEVELOPER_ERROR` (7×), `ProviderInstaller: Failed to load providerinstaller module ... Local version is 0 and remote version is 0`, `FlagRegistrar: Phenotype.API is not available`.
7. **Gegenprobe:** Prod-App `com.flintr.tv` v2.45 auf derselben Box, gleicher Account: Läuft einwandfrei. `Loaded favorites from Firestore: 30 total` + `Username saved` + 244 Live-Kategorien in ~8 s. DEVELOPER_ERROR: 0×.
8. Timing: Debug-App schrieb um 12:51 noch normal Cache/Credentials (lief also als v2.47), Update auf v2.48 um 13:30 → seitdem kaputt.

## Betroffener Code (Startpunkte, selbst verifizieren wo Dinge liegen)

- `app/src/main/java/com/flintr/tv/data/local/CredentialsRepository.kt` — v2.48 hat hier Umbau: primärer Konstruktor mit `warmupScope: CoroutineScope`, sekundärer `@Inject`-Konstruktor, lazy `EncryptedSharedPreferences`, init füllt `_credentialsFlow` per Warmup-Coroutine. `credentialsFlow` ist die Trigger-Quelle für FavoritesRepository/WatchProgressService/WatchedHistoryService/CategoryVisibilityService (alle collect'en den Flow; bei null → Reset, bei non-null → Firestore-Load).
- `app/src/main/java/com/flintr/tv/data/local/FavoritesRepository.kt` — init collect't credentialsFlow, loadFromFirestore() via Mutex, `userRepository.loadFavorites(username)`.
- `app/src/main/java/com/flintr/tv/data/repository/UserRepository.kt` — `loadFavorites` (Zeile ~309) und `saveUsername` (Zeile ~72): Firestore `.get()/.set().await()` OHNE Timeout → hängen ewig, wenn Firestore-Backend nicht antwortet.
- `app/src/main/java/com/flintr/tv/viewmodel/MainViewModel.kt` — loadInitialData/refreshData, saveUsernameIfNeeded, DataLoadingScreen-Interaktion.
- Spinner-UI: Reihen-Spinner im MainContentScreen/ContentCard-Bereich für Kategorie "Favoriten" — selbst finden.

## Aufgaben

### 1. Root-Cause-Analyse: Warum bleibt der Firestore-Pfad in Debug stecken?
Analysiere v2.48-Änderungen am Credentials-/Firestore-Pfad (du hast kein git — prüfe Code-Zustand und ggf. `/tmp`-Vergleichskopien falls vorhanden; ansonsten Code-Lektüre):
- Kann `credentialsFlow` dauerhaft null bleiben, obwohl `getCredentials()` synchron Credentials liefert (Warmup-Coroutine hängt in EncryptedSharedPreferences.create auf IO-Thread, verlorene Emission, Race lazy-init vs. Listener-Registrierung)?
- Warum hängt `saveUsername` (direkt aus MainViewModel, NICHT flow-abhängig) — das deutet auf Firestore-Transport-Ebene, nicht nur Flow. Prüfe Firestore-Init: FirebaseApp-Init im Debug-Variante (google-services.json hat zweiten Client-Eintrag für `com.flintr.tv.debug` — darf NICHT entfernt werden, sonst bricht der Build mit „No matching client").
- `DEVELOPER_ERROR` + ProviderInstaller-Fail nur im Debug deutet auf Firebase/Cloud-API-Key-Restriktion oder fehlende SHA-1-Registrierung des Debug-Keystores in der Firebase-Console (package `com.flintr.tv.debug`). Das Console-Setup kann Code nicht fixen — aber dein Report muss es eindeutig benennen.

### 2. Robustheits-Fix (Code) — Endlos-Spinner darf nie wieder passieren
- Alle Firestore-Aufrufe, die beim Start laufen (`saveUsername`, `loadFavorites`, und weitere Firestore-Calls, die im Startup-/Sync-Pfad hängen können), mit `kotlinx.coroutines.withTimeout(...)` umschließen (15 s) und als `Result.failure` surfaced.
- FavoritesRepository: Bei Timeout/Failure → leerer Favoriten-Stand + Fehlermeldung statt ewigem Warten. UI (Favoriten-Kategorie im MainContentScreen) muss bei Load-Fehlern einen sichtbaren Fehlerzustand + Retry-Ansatz zeigen (bestehendes Error-Pattern der App nutzen, kein neues Design erfinden).
- Falls du in der Analyse einen echten Bug im Flow/Warmup findest (verlorene Emission etc.): fixen, minimal-invasiv, mitSecondary-Constructor-Kompatibilität halten (Dagger).
- KEINE Over-Engineering: keine neuen Libraries, kein Room, keine Architektur-Umbauten.

### 3. Debug-Firebase-Diagnose (Report, kein Code)
- `app/google-services.json`: Debug-Client-Eintrag für `com.flintr.tv.debug` prüfen (api_key vorhanden? mobilesdk_app_id?) — Werte im Report nennen, KEINE Secrets (API-Keys der Firebase-Config sind public by design, darfst du nennen).
- Debug-Keystore-SHA-1 ermitteln: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -password android` (SHA-1 + SHA-256 in Report).
- Konkrete Console-Schritte im Report: was Chris in der Firebase-Console (Projekt flintrtv) registrieren/restricten muss, damit der Debug-Client Firestore nutzen darf.

### 4. Verifikation
- `sh gradlew :app:compileDebugKotlin --no-daemon` grün
- Gezielte Tests: `sh gradlew :app:testDebugUnitTest --no-daemon --tests "*CredentialsRepository*" --tests "*FavoritesRepository*" --tests "*UserRepository*" --tests "*SecurityGuardrails*" --tests "*UserRepositoryGuardrails*"`
- `sh gradlew :app:assembleDebug --no-daemon` → APK unter `app/build/outputs/apk/debug/app-debug.apk`
- Vor Gradle: Disk-Headroom prüfen (`df -h ~`), weniger als 2 GB frei → abbrechen und im Report vermerken.

### 5. Report
Schreibe `fix-report-debug-firestore.md` im Repo-Root:
- Root Cause (eindeutig, mit Code-/Log-Belegen)
- Geänderte Dateien (Liste mit Kurzbegründung je Datei)
- Test-/Compile-Ergebnisse (echte Outputs, keine Erfindungen)
- Debug-Keystore SHA-1/SHA-256 + exakte Firebase-Console-Schritte für Chris
- Offene Punkte/Risiken

## Arbeitsweise
- Selbst verifizieren, wo Dateien/Klassen liegen (Pfade oben können leicht danebenliegen).
- Kein interactive nötig: alle Commands nicht-interaktiv ausführen.
- Wenn du nach der Analyse feststellst, dass der Firestore-Ausfall NICHT code-fixbar ist (reines Console-Problem), Task 2 trotzdem umsetzen (Defensive) und das klar im Report trennen.
