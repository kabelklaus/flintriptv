# Findings — flintrTV Empfehlungen Stufe 1

## Codebase-Fakten (verifiziert 2026-10-05, ~/Nextcloud/flintrtv)

- `Favorite` hat KEIN Genre-Feld → Genre-Anreicherung ausschließlich via Katalog-Lookup (VodStream/Series by id)
- `WatchProgressService`: COMPLETION_THRESHOLD=0.9, MIN_POSITION_MS=30s; Keys `movie:{streamId}` und `series:{seriesId}:{episodeId}` (Prefix-Konstanten im Companion)
- `WatchedHistoryService`: Firestore-Map key→WatchedItem; WatchedItem.genre oft null (Debug-Logs 2026-09 zeigten teils alle null) → nicht als Genre-Quelle verwenden
- `RatingEntry`: contentType/contentId/title/rating(1|0)/ratedAt; Firestore `users/{username}.ratings` Map<contentKey, RatingEntry>; UserRepository: saveRating (Transaction, merge) + loadRating (single) vorhanden, Bulk-Load FEHLT (Task 4a)
- Katalog: `DataCacheService` → `CachedData(channels, movies, series, liveCategories, vodCategories, seriesCategories)`, memoized Parse (multi-MB)
- `Series.kt`: seasons: List<Season>?, Season.episodes: List<Episode>?; genre: String?; rating/rating5Based: String?/Double?
- `VodStream`: genre: String?, rating: String?, rating5Based: Double?
- UI: `HomeDestination` = sealed class in `ui/sidebars/MainNavbar.kt` (Search/TV/Movies/Series/Settings-NavItems); Special-Categories via `createSpecialCategories()` in AppNavigation.kt (~1310): FAVORITES → IN_PROGRESS (nur wenn supportsInProgress) → NEW, prepend vor sortierte Kategorien; IDs in `ui/common/Constants.kt` (`__favorites__`, etc.)
- `VODContentSection`/`SeriesContentSection` (AppNavigation ~1128/1157) switchen bei `IN_PROGRESS_CATEGORY_ID` auf `InProgressContent` — Muster-Vorlage für den Recommendation-Zweig
- Click-Handler: `createInProgressClickHandlers` / `createSeriesClickHandler` inkl. saveNavigationStateBeforePlayer + logAndNavigate
- `AppNavigationDependencies` (~594) hält alle Services; RecommendationService dort einhängen (oder via hiltViewModel — CC entscheidet nach Recon)
- Tests: 65 Dateien, Robolectric+Mockk; Vorbilder: FavoritesRepositoryTest, WatchProgressServiceTest
- `ContentParser.kt` hat bereits Rating-Extraktion: Priorität rating (0-10) → rating5Based×2 → imdb — WIEDERVERWENDEN, nicht neu schreiben

## Entscheidungen

- Thumbs als Modifikator (+1/−2), nicht Kernsignal (Chris-Zustimmung im Chat 2026-10-05)
- "Aktiv" = ≥5 Episoden ≥20% ODER ≥3 Episoden ≥90% (Chris' 5-6-Folgen-Idee, serienlastig interpretiert)
- Cold Start → leere Row statt Pseudo-Content
- Live-TV außen vor

## CC-Sandbox

- Plan liegt zusätzlich im Repo: `.hermes-plan/` (task_plan.md, findings.md, progress.md, order.md) — `-p`-Sandbox kann `~/.hermes/plans/…` nicht lesen
- Bestätigt (2026-10-05): `sh gradlew :app:compileDebugKotlin` wird von der Bash-Tool-Permission-Schicht blockiert ("requires approval"), auch mit `JAVA_HOME` gesetzt und ohne `&&`-Verkettung — Gate konnte nicht selbst gefahren werden, exakt wie in order.md erwartet.

## Recon (Task 0, ergänzend zu den o.g. Codebase-Fakten)

- `VODContent`/`SeriesContent` (VODScreen.kt/SeriesScreen.kt) haben ihr eigenes internes `when`-Pattern für Favorites/New, komplett getrennt vom `IN_PROGRESS_CATEGORY_ID`-Zweig, der stattdessen in `AppNavigation.kt`s `VODContentSection`/`SeriesContentSection` auf eine andere Top-Level-Composable (`InProgressContent`) umschaltet. Der Empfehlungs-Zweig folgt letzterem Muster (eigene Composables `RecommendedVodContentSection`/`RecommendedSeriesContentSection` in AppNavigation.kt, nicht in VODContent/SeriesContent selbst).
- `ContentGridLayout` zeigt bei leerer Liste automatisch den reguellen Empty-State (`ContentGridEmptyState`, Fokus-sicher weil kein Crash/weißer Screen) — kein zusätzlicher Code für Cold-Start-UI nötig, Wiederverwendung reicht.
- `WatchedHistoryService`-Keys sind identisch zu `WatchProgressService`-Keys (`movie:{streamId}` / `series:{seriesId}:{episodeId}`) — Membership-Check per Key direkt möglich, kein separates Lookup nötig.
- `ContentParser.getRating(item): String?` existiert bereits (rating→rating5Based×2 Priorität) und wird fürs Tie-Break in `RecommendationEngine` wiederverwendet statt neu geschrieben.
- `AppNavigationDependencies` wird nur an einer Stelle konstruiert (`MainActivity.kt`) — dort `recommendationService` ergänzt, kein weiterer Call-Site-Fund.
