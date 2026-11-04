# Order: flintrTV Empfehlungen Stufe 1 — "Für dich" (Genre-Profil + UI-Chip)

**KONTEXT:** flintrTV (Android, Kotlin/Compose, IPTV-App) bekommt eine Empfehlungs-Funktion. On-device Score-Engine baut aus Favoriten/Guckverhalten/Thumbs ein Genre-Profil und scoret den Katalog-Cache. UI: neuer Kategorie-Chip "Für dich" bei Movies + Series, der die Top-Empfehlungen zeigt. **Der vollständige Plan mit Scoring-Spec liegt in `.hermes-plan/task_plan.md`, Zusatz-Fakten in `.hermes-plan/findings.md` — LIES BEIDE ZUERST VOLLSTÄNDIG. Diese Order referenziert den Plan; die Spec dort ist verbindlich.**

## Anforderungen

1. 100% Kotlin, Compose (Mobile-Material3 wie der Rest der App), Hilt. Keine Java-Dateien.
2. minSdk 24, target/compileSdk 36 (bestehende Config, nicht ändern).
3. Funktionsumfang:
   - `GenreNormalizer` (Task 1), `UserContentProfile` (Task 2), `RecommendationEngine` (Task 3), `RecommendationService` + `UserRepository.loadAllRatings` (Task 4/4a), UI-Chip `__recommended__` bei Movies+Series mit Grid-Rendering über das bestehende ContentCard/Grid-Pattern (Task 5) — jeweils exakt nach Spec im Plan, TDD (failing test → minimal impl → pass).
   - Cold Start (<5 Signale) = leeres Ergebnis, Chip zeigt regulären Empty-State. Kein Crash, kein White-Screen.
   - Fokus: Screen darf NIE ohne Fokus bleiben (Fokus-Fallback auf erstes Item, bestehende Muster in SeriesEpisodesScreen/AppNavigation verwenden). Touch-Taps, die Fokus bedeuten, rufen `safeRequestFocus`.
   - Firestore: nur READS + das neue `loadAllRatings` (Lese-Implementierung nach `loadRating`-Pattern). Keine neuen Write-Pfade.
4. Keine neuen Dependencies.
5. UI-Text: Deutsch ("Für dich") via strings.xml (`recommended_for_you`), values-en "For you" falls values-en existiert.

## Finger weg

- `gradlew`, `gradle/wrapper/*`, `local.properties`, `google-services.json`: existieren fertig — nicht anfassen, nicht neu generieren.
- `build.gradle.kts`: NICHT anfassen (KEIN Version-Bump in dieser Order — macht der Orchestrator nach Freigabe).
- Keine Änderungen an: Cleartext-Config, GITHUB_TOKEN-BuildConfig, Update-Flow, Firestore-Sync von Favorites/WatchProgress/WatchedHistory (Guardrail-Tests bewachen das: kein `set()` ohne `SetOptions.merge()` auf `users/*`).
- Bestehende Verhaltens-Entscheidungen nicht "fixen" (D-Pad-Seek 10s, Thumbs bei Live ausgeblendet, SoftRatingOverlay kein D-Pad-Ziel).

## Wichtige Baum-Fakten (verifiziert, kann sich verschoben haben — selbst prüfen)

- Katalog: `DataCacheService.getCachedData()` → `CachedData(movies: List<VodStream>, series: List<Series>, …)`, memoized
- Progress-Keys: `movie:{streamId}` / `series:{seriesId}:{episodeId}`; COMPLETION_THRESHOLD=0.9 in WatchProgressService
- Favorite hat KEIN Genre → Genre-Anreicherung nur über Katalog-Lookup. WatchedItem.genre ist oft null → nicht als Quelle.
- UI-Verdrahtung: `AppNavigation.kt` `createSpecialCategories()` (~1310) prependet Chips; `VODContentSection`/`SeriesContentSection` (~1128/1157) switchen bei `IN_PROGRESS_CATEGORY_ID` auf `InProgressContent` — genau dieses Muster für `RECOMMENDED_CATEGORY_ID` kopieren (inkl. `createVodClickHandler`/`createSeriesClickHandler` mit saveNavigationStateBeforePlayer).
- Rating-Extraktion (Tie-Break): `ContentParser.kt` hat Priorität rating→rating5Based×2 — wiederverwenden.
- Services hängen an `AppNavigationDependencies` (~594).
- Tests (Robolectric+Mockk, Vorbilder `FavoritesRepositoryTest`, `WatchProgressServiceTest`): `context.packageName` statt hartem Package; `assertDoesNotExist()` ist Member von SemanticsNodeInteraction (kein Import!).
- Build: `sh gradlew` (nicht `./gradlew`), `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

## Sandbox

Du kannst `sh gradlew` nicht selbst ausführen (Approval wird in `-p` nie granted). Versuche die Gates GENAU EINMAL; werden sie blockiert: sauber stoppen und im Report die vollständige Änderungsliste + die erwarteten Befehle (`sh gradlew :app:compileDebugKotlin` bzw. `sh gradlew :app:testDebugUnitTest`) nennen. Der Orchestrator baut, gated und committet. Committe/pushe NICHT selbst.

## Report (letzte Nachricht)

- Geänderte/neue Dateien (vollständig, inkl. Test-Dateien)
- Ob und welche Gates du selbst laufen lassen konntest; erwartete Ergebnisse für den Orchestrator
- Abweichungen von der Spec im Plan (mit Begründung)
- Bekannte Einschränkungen / TODOs
