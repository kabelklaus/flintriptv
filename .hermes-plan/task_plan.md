# flintrTV Empfehlungen Stufe 1 ("Für dich") Implementation Plan

> **For Hermes:** Umsetzung via Claude Code Builder (cc-route, `--permission-mode acceptEdits`, order.md aus `.hermes-plan/order.md`). Hermes = Orchestrator: Gates (compile + volle Test-Suite) zentral fahren, committen, installieren. CC darf nicht selbst pushen/committen.
>
> **Für CC:** Vollständige Selbstständigkeit nötig — kein Rückfragen möglich. Wo diese Plan-Datei etwas nicht hergibt: selbst im Tree verifizieren (Pfade können sich verschoben haben).

**Goal:** Eine "Für dich"-Kategorie (Chip bei Movies + Series), die on-device aus Favoriten, Guckverhalten (WatchProgress/WatchedHistory) und Thumbs ein Genre-Profil baut und ungeschauten Katalog-Content danach scoret.

**Architecture:** Pure-Kotlin-Score-Engine (kein Backend, kein TMDB) — `GenreNormalizer` + `UserContentProfile` + `RecommendationEngine`, verpackt in einen Hilt-`@Singleton` `RecommendationService`, der die vorhandenen StateFlows (Favorites, WatchedHistory, WatchProgress, Ratings, Katalog-Cache) kombiniert. UI hängt als Special-Category-Chip (`__recommended__`) in den bestehenden `createSpecialCategories`-Pfad von AppNavigation ein und rendert das Katalog-Grid gefiltert auf die Top-N-Empfehlungen.

**Tech Stack:** Kotlin 2.0.21, Compose (Mobile-Material3), Hilt, StateFlow/Moshi (vorhanden), JUnit+Mockk (Tests). Keine neuen Dependencies.

**Datenbasis (verifiziert 2026-10-05 im Tree):**
- `Favorite` (`data/model/Favorite.kt`): id/type(LIVE|MOVIE|SERIES)/streamId/name/metadata/addedAt — KEIN Genre → Genre-Lookup über Katalog nötig
- `WatchProgress` (`data/model/WatchProgress.kt`): position/duration (ms)/lastWatched/title; Keys `movie:{streamId}` bzw. `series:{seriesId}:{episodeId}`; COMPLETION_THRESHOLD=0.9 existiert in WatchProgressService
- `WatchedItem` (`data/model/WatchedItem.kt`): contentType/streamId/seriesId/episodeId/name/genre/categoryName/watchedAt; Genre kann null sein
- `RatingEntry` (`data/model/RatingEntry.kt`): contentType/contentId/title/rating(1|0)/ratedAt; Firestore: `users/{username}.ratings` Map<contentKey, RatingEntry>; UserRepository hat saveRating/loadRating (einzel), KEINEN Bulk-Load
- Katalog: `DataCacheService.getCachedData()` → `CachedData(channels, movies: List<VodStream>, series: List<Series>, …)`; VodStream.genre/rating, Series.genre/rating als nullable Strings
- UI-Pfad: `AppNavigation.kt` → `createSpecialCategories()` (Favorites/New/InProgress-Chips, `Constants.kt` FAVORITES_CATEGORY_ID=`__favorites__`), `MainContentBody` → `VODContentSection`/`SeriesContentSection` switchen auf `IN_PROGRESS_CATEGORY_ID` → `InProgressContent`. Neuer Chip folgt exakt diesem Muster.
- Sidebars/Navbar (`MainNavbar.kt`) bleiben NICHT angefasst — Chip erscheint nur in der Kategorie-Leiste von Movies + Series.

---

## scoring-Spec (verbindlich)

**Genre-Normalisierung:** Xtream-Genres sind Rohstrings ("Action, Adventure", "Drama|Horror"). Split auf `,` `|` `;` `/`, trim, lowercase, dedupe, leere/„null"-Strings verwerfen.

**Signal-Gewichte (Genre-Profil, pro Genre des jeweiligen Titels):**

| Signal | Erkennung | Gewicht |
|---|---|---|
| Favorit | in Favorites (MOVIE/SERIES), Genre via Katalog-Lookup | +3.0 |
| Serie aktiv | ≥5 distinct Episoden mit progress ≥0.2 ODER ≥3 distinct Episoden ≥0.9 (Keys `series:{id}:{ep}`) | +2.5 |
| Serie angebrochen | 1–4 distinct Episoden begonnen | +1.0 |
| Film durchgesucht | Movie-Key progress ≥0.9 | +1.5 |
| Film abgebrochen | Movie-Key 0<progress<0.2 UND nicht in WatchedHistory | −1.0 |
| Thumb up | RatingEntry rating=1 | +1.0 |
| Thumb down | RatingEntry rating=0 | −2.0 |
| Alters-Dämpfung | Faktor `max(0.3, exp(-ageDays/90.0))` auf jedes Signal, age = now − watchedAt/lastWatched/ratedAt/addedAt | multiplikativ |

Nicht erkennbare Ages (parse-fehler) → Faktor 1.0. Werte als `const val` oben in der Engine-Datei (konfigurierbar, eine Quelle).

**Scoring Katalog-Item:** `score = Σ genreWeight[g] für g in normalizedGenres(item)`. Filter: nur Items mit score > 0; excl. Live, excl. bereits Favorit, excl. Movie mit progress ≥0.9 oder in WatchedHistory, excl. Serie mit beliebiger Episoden-Aktivität (hat sie schon im Profil verbraucht). Tie-Break: `rating`/`rating5Based*2` desc (null = 0.0), dann Name asc. Output: Top 20, deterministisch sortiert.

**Cold Start:** < 5 erkennbare Signal-Titel (Favoriten+aktive Serien+gesehene Movies kombiniert) → `emptyList()` (Chip zeigt leeres Grid, kein Crash, kein Pseudo-Content).

**Rating-Fallback (ergänzt 2026-10-30):** Xtream `get_vod_streams` liefert KEINE Genres (Katalog-Cache: 57.177/57.177 Movies ohne `genre`), Genre-Scoring liefert für Movies daher immer 0. Wenn die genre-basierte Liste eines Typs nach `take(limit)` LEER ist: Fallback = Katalog-Items minus Ausschlüsse, sortiert `rating` desc → `name` asc, `take(limit)`. Kein Mischen mit Genre-Treffern; Cold-Start-Gate bleibt davor unverändert. **Chris 2026-10-30: bewusst so lassen**, kein Genre-Enrichment.

**Diversitäts-Bremse (ergänzt 2026-10-30):** genre-basierte Listen: max. 5 Einträge pro Anker-Genre (Anker = normalisiertes Genre mit höchstem Profil-Gewicht, Gleichstand alphabetisch), zweiphasige Auswahl (Cap-Phase, dann Auffüllen bis `limit` ohne Cap), deterministisch. Verhindert Genre-Monokultur (Fall: 29 Anime-Favoriten → Top 20 komplett Anime). Rating-Fallback bleibt ohne Cap.

---

### Task 0: Recon (read-only)

**Objective:** Fehlende Details im Tree verifizieren, bevor T1 startet.

**Files:** keine Änderungen.

**Step 1:** Lies `ui/tabs/VODScreen.kt` + `ui/tabs/SeriesScreen.kt`: Wie filtert `VODContent`/`SeriesContent` nach `selectedCategoryId`? Reicht `params.viewModel`/ein bestehender Parameter, oder braucht der neue Pfad eigene Items? Notiere in `.hermes-plan/findings.md`.
**Step 2:** Lies `AppNavigation.kt` `VODContentSection`/`SeriesContentSection` (~Zeile 1128/1157): Wie genau ist der `IN_PROGRESS_CATEGORY_ID`-Zweig verdrahtet (inkl. Click-Handler + Fokus)? Der Recommendation-Zweig kopiert dieses Muster.
**Step 3:** Prüfe `UserRepository.kt` (Ratings-Storage ~Zeile 465-535 + `upsertRating` ~1150): existierendes Pattern fürs Lesen des User-Docs, an dem sich `loadAllRatings` orientiert.
**Step 4:** Prüfe `DataCacheService` API-Namen für den Katalogzugriff (`getCachedData` o.ä., memoized).

### Task 1: GenreNormalizer (TDD)

**Objective:** Roh-Genre-Strings → saubere Genre-Liste.

**Files:**
- Create: `app/src/main/java/com/flintr/tv/domain/recommendation/GenreNormalizer.kt`
- Test: `app/src/test/java/com/flintr/tv/domain/recommendation/GenreNormalizerTest.kt`

**Step 1:** Failing test: `"Action, Adventure"` → `[action, adventure]`; `"Drama|Horror"`; `"Sci-Fi & Fantasy; Thriller"` → split auch auf `&`; `null`/`""`/`"  , , "` → `emptyList()`; case-folding + dedupe (`"Action, ACTION"` → `[action]`).
**Step 2:** `sh gradlew :app:testDebugUnitTest --tests "*GenreNormalizerTest*"` → FAIL (class not found).
**Step 3:** Implementiere `object GenreNormalizer { fun normalize(raw: String?): List<String> }`.
**Step 4:** Test → PASS. Compile: `sh gradlew :app:compileDebugKotlin`.

### Task 2: UserContentProfile (TDD)

**Objective:** Signale → Genre-Gewichte nach scoring-Spec.

**Files:**
- Create: `app/src/main/java/com/flintr/tv/domain/recommendation/UserContentProfile.kt`
- Test: `app/src/test/java/com/flintr/tv/domain/recommendation/UserContentProfileTest.kt`

**Step 1:** Failing tests mit fixteren Katalog-Fixtures (2-3 VodStream, 2 Series mit Genre-Strings). Cases: Favorit-Serie → +3 auf ihre Genres; 5×`series:10:1..5` progress 0.5 → Serie aktiv (+2.5); 2 Episoden → angebrochen (+1.0); Movie progress 0.95 → +1.5; Movie progress 0.05 nicht gewatcht → −1.0; Thumb down −2; Alter 200 Tage → Faktor ~0.3 (capped); gemischte Signale summieren sich auf demselben Genre.
**Step 2:** Run → FAIL.
**Step 3:** Implementiere:
```kotlin
data class GenreProfile(val weights: Map<String, Double>, val signalCount: Int)
object UserContentProfile {
    fun build(
        favorites: List<Favorite>,
        watchedHistory: Map<String, WatchedItem>,
        watchProgress: Map<String, WatchProgress>,
        ratings: Map<String, RatingEntry>,
        movies: List<VodStream>,
        series: List<Series>,
        nowMs: Long = System.currentTimeMillis()
    ): GenreProfile
}
```
Key-Parsing: `"movie:{id}"` / `"series:{id}:{epId}"` — After outras Dateien verifizieren (Prefixe in WatchProgressService-Companion). Genre-Auflösung AUSSCHLIESSLICH über Katalog (WatchedItem.genre ignorieren — häufig null). Sowohl Movies- als auch Series-Episoden-Signale fließen in dasselbe Genre-Map.
**Step 4:** Run → PASS, compile grün.

### Task 3: RecommendationEngine (TDD)

**Objective:** Profil + Katalog → sortierte Top-20-Empfehlungen.

**Files:**
- Create: `app/src/main/java/com/flintr/tv/domain/recommendation/RecommendationEngine.kt`
- Test: `app/src/test/java/com/flintr/tv/domain/recommendation/RecommendationEngineTest.kt`

**Step 1:** Failing tests: Kandidat mit passenden Genres gewinnt; Favorit/Gesehen/Live erscheinen nie; score≤0 raus; Tie-Break über rating; Cold-Start (<5 Signale) → empty; Top-20-Cap bei 30 Kandidaten.
**Step 2:** Run → FAIL.
**Step 3:** Implementiere:
```kotlin
data class RecommendationResult(
    val movies: List<VodStream>,
    val series: List<Series>
)
object RecommendationEngine {
    fun recommend(profile: GenreProfile, cached: CachedData, limitPerType: Int = 20): RecommendationResult
}
```
Movies und Series getrennt gescoret/gelistet (Chip zeigt je nach Destination nur den relevanten Typ).
**Step 4:** Run → PASS, compile grün.

### Task 4: RecommendationService (Hilt)

**Objective:** Flows kombinieren, StateFlow<RecommendationResult> exposen.

**Files:**
- Create: `app/src/main/java/com/flintr/tv/data/local/RecommendationService.kt`
- Modify: nichts sonst (DI via @Inject constructor, AppModule braucht keinen Eintrag für @Singleton @Inject)
- Test: `app/src/test/java/com/flintr/tv/data/local/RecommendationServiceTest.kt` (Recompute-Test: Favorit-Änderung → neues Ergebnis; Robolectric+Mockk nach Vorbild `FavoritesRepositoryTest`)

**Step 1:** Test: fake Repos/Services (gleiche Fakes wie in Nachbar-Tests) → engineOutput aktualisiert sich wenn `_favorites.value` sich ändert.
**Step 2:** Run → FAIL.
**Step 3:** Implementiere `combine(favorites, watchedHistory, watchProgress, ratingsFlow, catalogFlow)` → debounce 300ms → profile → engine. Ratings: vorab `UserRepository.loadAllRatings(username)` (Task 4a) ins Flow einhängen; bei Load-Fehler mit `emptyMap()` weiterarbeiten (Row bleibt funktional ohne Thumbs).
  - **4a (davor):** `UserRepository.loadAllRatings(username): Result<Map<String, RatingEntry>>` nach dem existierenden load-Pattern (`loadRating` ~Zeile 504). Read-only, kein set().
**Step 4:** Run → PASS, compile grün.

### Task 5: UI — "Für dich"-Chip + Grid

**Objective:** Sichtbare Kategorie bei Movies + Series, Klick → Detail/Player wie gewohnt.

**Files:**
- Modify: `app/src/main/java/com/flintr/tv/ui/common/Constants.kt` — `const val RECOMMENDED_CATEGORY_ID = "__recommended__"`
- Modify: `app/src/main/java/com/flintr/tv/ui/navigation/AppNavigation.kt` — `createSpecialCategories()` (nur wenn destination VOD oder Series): recommendationsChip zwischen Favorites und InProgress; `VODContentSection`/`SeriesContentSection`: Zweig `RECOMMENDED_CATEGORY_ID` → filtert `cached.movies`/`cached.series` auf Engine-Ergebnis, rendert über dasselbe Grid/ContentCard-Pattern wie der normale Kategorie-Zweig; Click-Handler = bestehende `createVodClickHandler`/`createSeriesClickHandler` (inkl. saveNavigationStateBeforePlayer)
- Modify: `app/src/main/res/values/strings.xml` (+ values-en falls vorhanden): `recommended_for_you` = "Für dich" / "For you"
- Test: `app/src/test/java/com/flintr/tv/ui/…` — falls es für createSpecialCategories schon Tests gibt (suchen!), dort Case ergänzen; wenn UI-Test-Infra für diesen Pfad fehlt, Compile + bestehende Suite genügen

**Step 1:** createSpecialCategories erweitern + Strings. Compile grün.
**Step 2:** Content-Section-Zweig: leere Ergebnisliste → reguläres Empty-State des Grids (kein Crash, kein weißer Screen). Fokus: erster Chip/Item fokussierbar, kein Fokus-Dead-End (Skill-Regel: Screen nie ohne Fokus — Fallback auf erstes Item via bestehendem Muster).
**Step 3:** Compile + `:app:testDebugUnitTest --tests "*Category*"` o.ä. grün.

### Task 6: Zentrales Gate + Sichtbarkeit (Orchestrator, nicht CC)

**Objective:** Beweis, dass Chris es im Frontend sieht.

**Steps (Hermes):**
1. Volle Suite: `sh gradlew :app:testDebugUnitTest --no-daemon` (JAVA_HOME auf Android-Studio-JBR) — muss komplett grün sein inkl. Guardrails
2. `sh gradlew :app:assembleDebug --no-daemon` → APK
3. Install auf MiBox `192.168.2.34:5555` (Einschalten erlaubt; vorher `ping -t 3`), Fallback Emulator (`Pixel_7_Pro -no-window`-Muster aus Skill) — Smoke: `am start` + `pidof` + logcat ohne FATAL
4. Chris meldet, was er auf dem Gerät sieht; Version-Bump/Release-Push NUR nach seinem OK (Release-Checkliste Schritt 0)

## Verification (gesamt)

- [ ] `sh gradlew :app:compileDebugKotlin` grün
- [ ] Volle `:app:testDebugUnitTest` grün (SecurityGuardrailsTest + UserRepositoryGuardrailsTest inklusive)
- [ ] APK installiert, App startet ohne FATAL EXCEPTION
- [ ] Bei Movies + Series erscheint Chip "Für dich"; Klick auf Empfehlung öffnet Serie/Film normal
- [ ] Keine Änderung an Firestore-Write-Pfaden (nur reads + neues loadAllRatings)

## Constraints für CC (verbindlich)

- Kein `set()` ohne `SetOptions.merge()` auf `users/*` (Guardrail-Test bewacht es)
- Keine neuen Dependencies; keine Änderung an Cleartext-Config, GITHUB_TOKEN-BuildConfig, Update-Flow
- Tests: `context.packageName` statt hardcodiertem Package
- `sh gradlew` (NICHT `./gradlew`), JAVA_HOME exportieren
- Pfade selbst gegen den Tree verifizieren, wenn sich Zeilenzahlen verschoben haben
- Status-Zeilen in `.hermes-plan/task_plan.md` nach jedem Task updaten; Findings nach `.hermes-plan/findings.md`
- Gate-Commands (volle Suite) versucht CC einmal; bei Approval-Verweigerung sauber stoppen und melden — Orchestrator fährt das Gate zentral

**Status-Dashboard:**
- Task 0 (Recon): **Status:** ✅ done (Findings in findings.md ergänzt)
- Task 1 (GenreNormalizer): **Status:** ✅ done (Test+Impl geschrieben, Gate nicht selbst gelaufen - s. Report)
- Task 2 (UserContentProfile): **Status:** ✅ done (Test+Impl geschrieben, Gate nicht selbst gelaufen)
- Task 3 (RecommendationEngine): **Status:** ✅ done (Test+Impl geschrieben, Gate nicht selbst gelaufen)
- Task 4 (+4a Service/Bulk-Ratings): **Status:** ✅ done (UserRepository.loadAllRatings + RecommendationService + Tests, Gate nicht selbst gelaufen)
- Task 5 (UI Chip+Grid): **Status:** ✅ done (Constants/strings/AppNavigation-Wiring, Gate nicht selbst gelaufen)
- Task 6 (Gate+Install, Hermes): **Status:** ⏳ pending (Orchestrator)
