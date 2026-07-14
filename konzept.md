# Verbesserungskonzept Flintr TV

Stand: 2026-07-14 · Basis: Code-Audit des `DEV`-Branches (Security, Performance, Usability, Architektur) inkl. Detail-Review der Player-Interaktion (Kapitel 5).
Bewusste Projektentscheidungen aus den Guardrail-Tests (`SecurityGuardrailsTest`, `PerformanceGuardrailsTest`) und `todos/storys.mkd` wurden berücksichtigt und nicht erneut in Frage gestellt, außer wo unten explizit begründet.

---

## 1. Sicherheit

### 1.1 🔴 KRITISCH: GitHub-Token wird ins APK eingebacken

**Fund:** `app/build.gradle.kts` schreibt `GITHUB_TOKEN` als `buildConfigField` in die App. Der Token landet als Klartext-String in `classes.dex` und ist mit `apktool`/`strings` in Sekunden extrahierbar. Er authentifiziert gegen das private Repo `kabelklaus/flintriptv` (`AppModule.kt:101` und `:170` setzen ihn als `Authorization`-Header).

**Risiko:** Jeder, der die APK besitzt (und die App verteilt sich per Self-Update selbst), kann mit dem Token auf das private Repo zugreifen – je nach Token-Scope lesen oder sogar schreiben (Supply-Chain-Risiko: manipulierte Release-APKs).

**Konkrete Anpassung:**
1. **Sofort:** Token rotieren und durch einen *fine-grained PAT* ersetzen: nur Repo `flintriptv`, nur Permission „Contents: Read". Damit ist der Schaden auf „Releases lesen" begrenzt.
2. **Richtig lösen (Token ganz aus der App entfernen), eine der zwei Optionen:**
   - **Option A – öffentliches Release-Repo:** Release-APKs in ein separates, öffentliches Repo (oder GitHub Pages) publizieren. `GitHubApiService` braucht dann keinen Auth-Header mehr; der komplette Token-Code in `build.gradle.kts` und `AppModule.kt` entfällt.
   - **Option B – Update-Proxy:** Kleiner Endpoint (Cloudflare Worker / Firebase Function), der `latest.json` (Version, URL, SHA-256) und die APK ausliefert. Der Token lebt nur serverseitig.
3. CI-Workflow (`build-release.yml`) entsprechend anpassen.

### 1.2 🔴 HOCH: Firestore ohne Authentifizierung, Dokumente nur über Username adressiert

**Fund:** `UserRepository.kt:25` nutzt `FirebaseFirestore.getInstance()` ohne jegliche Firebase-Auth. Dokument-ID in der Collection `users` ist der **IPTV-Username** (`UserRepository.saveUsername()`). Darunter liegen Favoriten, Watch-Progress, Watched-History, versteckte Kategorien und die Friends-Liste. Es gibt keine Security-Rules im Repo.

**Risiko:**
- Da die App keine Auth macht, müssen die Firestore-Rules Lese-/Schreibzugriff ohne `request.auth` erlauben – sonst würde nichts funktionieren. Damit kann **jeder** mit der (aus dem APK extrahierbaren) Firebase-Konfiguration beliebige Nutzerdaten lesen, ändern oder löschen – es genügt, einen Username zu kennen oder zu enumerieren.
- Datenschutz: Der IPTV-Username ist ein halbes Credential und wird als Klartext-Schlüssel in eine zentrale Datenbank geschrieben, zusammen mit dem kompletten Sehverhalten.

**Konkrete Anpassung (in dieser Reihenfolge):**
1. **Firebase Anonymous Auth** aktivieren; beim App-Start `signInAnonymously()`. Kein Login-Zwang für den Nutzer, keine UX-Änderung.
2. **Dokument-ID von Username auf `auth.uid` umstellen.** Migration: beim ersten Start nach Update das alte Username-Dokument lesen, unter der UID neu speichern, altes Dokument löschen. Der Username kann als Feld (idealerweise als `SHA-256(username)`-Hash) erhalten bleiben, falls die Friends-Funktion ihn zum Nachschlagen braucht.
3. **Security Rules deployen** (und als `firestore.rules` ins Repo aufnehmen):
   ```
   match /users/{uid} {
     allow read, write: if request.auth != null && request.auth.uid == uid;
   }
   ```
   Für Friends ggf. eine separate, bewusst lesbare Sub-Struktur definieren statt das ganze User-Dokument zu öffnen.
4. Bis dahin prüfen, was die aktuellen Rules in der Firebase Console tatsächlich erlauben – das ist der einzige Punkt dieser Analyse, der sich nicht aus dem Repo verifizieren lässt.

### 1.3 🟠 MITTEL: Credentials wandern durch Navigation-Routes und SavedStateHandle

**Fund:** Xtream-Stream-URLs enthalten bauartbedingt `username/password` im Pfad. Die App bettet die **komplette URL** in die Navigation-Route ein (`Screen.Player`-Routen in `AppNavigation.kt:104 ff.`, `handleDeepLink()` baut `.../movie/${username}/${password}/...`). Nav-Argumente werden von Jetpack Navigation in SavedState-Bundles persistiert (überleben Prozess-Tod, tauchen in `adb bugreport`-Dumps auf) und werden teils geloggt.

**Konkrete Anpassung:** Nur `contentType` + `streamId`/`seriesId`/`episodeId` als Nav-Argumente übergeben. Die Stream-URL erst im `PlayerViewModel` aus `CredentialsRepository` + IDs zusammenbauen. Nebeneffekte: kürzere Routen, kein URL-Encoding-Gefrickel mit `__EMPTY_TITLE__`-Platzhaltern, und `saveNavigationStateBeforePlayer()`-Logs enthalten keine Secrets mehr.

### 1.4 🟠 MITTEL: 477 `Log.*`-Aufrufe landen im Release-Build

**Fund:** Kein Log-Stripping in `proguard-rules.pro`, kein Timber. Einzelne Aufrufe sind zwar per `BuildConfig.DEBUG` geschützt, die Mehrheit nicht (z. B. Sync-/Cache-/Navigation-Logs mit Content-Titeln).

**Konkrete Anpassung:** In `proguard-rules.pro` ergänzen:
```
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
```
`w`/`e` bewusst behalten (Crashlytics-Kontext). Die bestehenden `if (BuildConfig.DEBUG)`-Wrapper können danach schrittweise entfallen.

### 1.5 🟡 NIEDRIG: Cleartext-Traffic global erlaubt

Laut `SecurityGuardrailsTest` eine **bewusste Entscheidung** (Xtream-Server der Nutzer sprechen oft nur HTTP) – wird hier nicht gekippt. Verbesserung ohne Bruch: Beim Login zuerst `https://` gegen den Server probieren und nur bei Fehlschlag auf `http://` zurückfallen; bei HTTP-Verbindung einen dezenten Hinweis „Verbindung unverschlüsselt" in den Verbindungs-Settings anzeigen. Der Guardrail-Test bleibt gültig.

### 1.6 🟡 NIEDRIG: Update-Installation prüft nur ZIP-Magic-Bytes

`UpdateDownloadService.isValidApk()` prüft nur die ersten 4 Bytes. Android selbst verweigert zwar Updates mit fremder Signatur (Grundschutz vorhanden), aber: SHA-256-Checksumme in die Release-Metadaten aufnehmen und vor der Installation verifizieren; optional `PackageManager.getPackageArchiveInfo()` + Signaturvergleich mit der installierten App vor dem Install-Intent.

### 1.7 🟡 NIEDRIG: EncryptedSharedPreferences ist deprecated + synchrone Init

Die Jetpack-Security-Bibliothek (`security-crypto 1.1.0-alpha06`) wird nicht weiterentwickelt. Zusätzlich wird der MasterKey in `CredentialsRepository` synchron im Konstruktor eines Singletons erzeugt – Keystore-Zugriffe können beim Kaltstart spürbar blocken. Mittelfristig: Migration auf DataStore mit Keystore-basierter Verschlüsselung (Tink); kurzfristig reicht Lazy-Init auf dem IO-Dispatcher.

### 1.8 🟡 NIEDRIG: Repo-Hygiene

- `test.txt` (Prompt-Notiz) löschen.
- `references/` (33 MB fremder Quellcode) und `todos/` sind **nicht** in `.gitignore` – vor dem nächsten Commit eintragen oder bewusst committen.
- Positiv: `google-services.json`, `local.properties`, Keystores sind korrekt ignoriert; keine hartcodierten Secrets im Quellcode gefunden.

---

## 2. Performance

### 2.1 🔴 HOCH: Kompletter Katalog als JSON-Strings in SharedPreferences

**Fund:** `DataCacheService` serialisiert alle Channels, Filme und Serien als drei riesige JSON-Strings in `iptv_cache_prefs`. Konsequenzen:
- **Jeder Kategorie-Wechsel** parst den kompletten Katalog neu: `XtreamRepository.getCachedData()` → `loadCachedDataAsync()` → Moshi-Parse *aller* Einträge, danach `filter{}` (`XtreamRepository.kt:117–150`). Bei typischen IPTV-Katalogen (10.000–100.000 Einträge) sind das Sekunden pro Klick auf TV-Hardware.
- SharedPreferences hält die komplette XML-Datei nach dem ersten Zugriff **dauerhaft im RAM** – die Mega-Strings belegen den Heap doppelt (String + geparste Objekte).
- Schreiben (`saveCachedData`) serialisiert und schreibt die gesamte Datei atomar neu.

**Konkrete Anpassung: Room-Datenbank** mit Tabellen `live_streams`, `vod_streams`, `series`, `categories` und Index auf `category_id`:
- Kategorie-Browsing wird ein `SELECT ... WHERE category_id = ?` (Millisekunden statt Voll-Parse).
- Die Suche wird ein `LIKE`-Query statt In-Memory-Filter über alles.
- `loadItemsByIds()` (Favoriten/Fortsetzen) wird ein `WHERE id IN (...)`.
- DAO-Methoden können `Flow<List<T>>` liefern → ViewModels werden reaktiv statt imperativ.
- Migration ist unkritisch: Cache ist ohnehin nach 24 h invalide; beim ersten Start mit Room einfach neu laden.
Das deckt sich mit **Story #2** („Category-Navigation Performance") in `todos/storys.mkd` – Room ist die nachhaltige Lösung dafür.

### 2.2 🔴 HOCH (konkreter Bug): Suche parst den Cache auf dem Main Thread

**Fund:** `MainViewModel.performSearch()` (Zeile 398) ruft das **synchrone** `cacheService.loadCachedData()` auf – innerhalb von `viewModelScope.launch { }`, also auf `Dispatchers.Main`. Der komplette Katalog-Parse + drei `filter`-Durchläufe blockieren die UI bei jeder Suche → Jank bis ANR.

**Konkrete Anpassung (Einzeiler, sofort):** `loadCachedData()` → `loadCachedDataAsync()`. Langfristig ersetzt Room-Query (2.1) die Stelle komplett.

### 2.3 🟠 MITTEL: Veralteter Dependency-Stack

`gradle/libs.versions.toml`: Compose BOM `2023.10.01` (fast 3 Jahre alt), Media3 `1.3.1`, Coil `2.5.0`, Lifecycle `2.7.0`, core-ktx `1.10.1`, Retrofit `2.9.0`. Neuere Compose-Runtimes bringen messbare Recomposition-Verbesserungen; Media3-Updates enthalten viele HLS-/Playback-Fixes, die genau die Retry-Workarounds in `XtreamRepository`/Player betreffen können.

**Konkrete Anpassung:** Gestufte Updates mit jeweils vollem Regressionstest (Playback, Fokus-Navigation): erst Compose BOM + Lifecycle + Activity, dann Media3, dann Coil (3.x hat API-Änderungen – ggf. bei 2.7 bleiben, bis 2.4 erledigt ist). Version-Catalog macht das mechanisch einfach.

### 2.4 🟠 MITTEL: Coil ohne jede Konfiguration

Es gibt keinen konfigurierten `ImageLoader`; `ContentCard.kt:688` nutzt `rememberAsyncImagePainter(model = url)` ohne Size/Crossfade. Auf Poster-Grids mit hunderten Cover-Bildern heißt das: volle Decode-Größe, unbegrenzt geteilter Default-Cache, Fallback-Erkennung nur über den Painter-State.

**Konkrete Anpassung:** `FlintRTVApplication` implementiert `ImageLoaderFactory`:
```kotlin
override fun newImageLoader() = ImageLoader.Builder(this)
    .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
    .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(250L * 1024 * 1024).build() }
    .crossfade(true)
    .build()
```
In `ContentCard` auf `AsyncImage` mit `ImageRequest.Builder(...).size(width, height)` (Kartenmaße aus `Dimensions`) umstellen – Coil decodiert dann nur in Anzeigegröße.

### 2.5 🟡 NIEDRIG: `items()` ohne `key`

`EPGScreen.kt:63`, `MainNavbar.kt:325`, `SubSettingsHiddenCategories.kt:212` – bei Listenänderungen recomposen alle Items statt nur der geänderten, und Fokus-/Scroll-Zustand pro Item geht verloren. Fix: `items(streams, key = { it.streamId })` etc. (`ContentGridLayout` macht es bereits richtig.)

### 2.6 🟡 NIEDRIG: `KotlinJsonAdapterFactory` (Reflection) trotz Moshi-Codegen

`AppModule.provideMoshi()` registriert die Reflection-Factory als Fallback – das zieht `kotlin-reflect` (~2,5 MB) ins APK und maskiert fehlende `@JsonClass`-Annotationen. `FriendEntry` und `UpdateInfo` sind die einzigen Modelle ohne Codegen. Fix: dort `@JsonClass(generateAdapter = true)` ergänzen (sofern sie überhaupt durch Moshi laufen – `FriendEntry` geht primär durch Firestore), Factory entfernen, Unit-Tests laufen lassen.

### 2.7 🟡 NIEDRIG: Statisches `SimpleDateFormat` ist nicht threadsafe

`DataCacheService.ISO8601_FORMAT` wird als companion-val von mehreren Threads genutzt (`saveCachedData` auf beliebigem Thread, `isCacheValid()` vom Aufrufer). `SimpleDateFormat` ist mutable → sporadisch korrupte Timestamps = stiller Cache-Verfall. Fix: Desugaring ist aktiv, also `java.time.Instant` / `DateTimeFormatter.ISO_INSTANT` (immutable, threadsafe) – oder schlicht `putLong(epochMillis)`.

*Nicht anfassen:* Der sequentielle Startup in `MainViewModel` ist per `PerformanceGuardrailsTest` als bewusste Entscheidung dokumentiert (Story #36) – keine erneute Parallelisierung empfehlen.

---

## 3. Usability

### 3.1 🔴 EPG ist ein Platzhalter

`EPGScreen.kt` rendert pro Kanal statisch „EPG Daten werden geladen..." – es werden nie EPG-Daten geladen. Für eine IPTV-App ist der Programmführer Kernfunktionalität.

**Konkretes Konzept:**
1. Xtream liefert XMLTV über `xmltv.php?username=&password=` – einmal täglich (nach dem Katalog-Refresh) laden, in Room-Tabelle `epg_programs (channel_epg_id, start, stop, title, desc)` mit Index auf `(channel_epg_id, start)` ablegen. `LiveStream.epgChannelId` ist das Join-Feld.
2. UI in zwei Stufen: **(a)** Now/Next-Zeile auf den Live-TV-Karten (billig, sofortiger Mehrwert); **(b)** klassisches Timeline-Grid (Kanäle vertikal, Zeit horizontal, „Jetzt"-Linie) als eigener Screen. Das bestehende `getShortEpg` bleibt für die Player-Info-Overlay-Anzeige.

### 3.2 🟠 Sprach-Mix: Hardcodierte deutsche Strings trotz strings.xml und Sprachauswahl

`SubSettingsPlayback.kt`, `PlayerErrorDialog.kt`, `AppNavigation.kt:866`, `SubSettingsGeneral.kt:325` u. a. enthalten deutsche Literale, obwohl `strings.xml` existiert und es sogar ein „Sprache auswählen"-Setting gibt – das so nur einen Teil der UI übersetzen kann.

**Konkrete Anpassung:** Alle UI-Literale nach `strings.xml` (`stringResource(R.string....)`), `values-en/` anlegen. Lint-Regel `HardcodedText` als Fehler konfigurieren, damit es nicht wieder einreißt (der `lint {}`-Block in `build.gradle.kts` existiert schon).

### 3.3 🟠 Mobile-Material3 statt Compose for TV; Leanback ist toter Ballast

Die App baut TV-UI mit Mobile-Material3 nach (eigene Focus-Maps, `FocusRequester`-Geflechte in `ContentGridLayout`/`HomeContent`). `androidx.tv:tv-material` + `tv-foundation` liefern D-Pad-Fokus, `ImmersiveList`, Carousel und TV-typische Klickverhalten out of the box. Zusätzlich: `androidx.leanback:leanback` ist deklariert, aber **nirgends importiert** → Dependency entfernen (APK-Größe, Buildzeit).

**Konkrete Anpassung:** Leanback sofort entfernen. TV-Compose schrittweise einführen – neuer EPG-Screen (3.1) als Pilot, dann Grid/Rows migrieren. Kein Big-Bang nötig.

### 3.4 🟡 Roh-Fehlermeldungen in der UI

`uiState.error = error.message` reicht Strings wie „Failed to get live streams: 404" oder Moshi-Parsing-Fehler direkt an den Nutzer durch. **Konkrete Anpassung:** Fehler-Typen (Netz / Auth / Server / Parsing) zentral mappen – `ConnectivityErrorUtils` existiert bereits als Ansatz – auf lokalisierte Meldungen mit Handlungsvorschlag („Server nicht erreichbar – erneut versuchen") + Retry-Button. Ein sealed `AppError` statt `String?` im UiState macht das typsicher.

### 3.5 🟡 Suche: Voice-Input fehlt

Enter-getriggerte Suche mit Min-Länge 2 ist auf TV eine vernünftige Entscheidung. Was fehlt: **Voice-Search** über das Mikrofon der Fernbedienung (`RecognizerIntent.ACTION_RECOGNIZE_SPEECH`) – auf TV der schnellste Eingabeweg. Nach Umstellung auf Room-Queries (2.1) ist auch eine debounced Live-Suche günstig genug.

### 3.6 🟡 Accessibility

52 `contentDescription`-Vorkommen sind eine gute Basis. Ergänzen: `semantics { stateDescription }` auf Content-Karten (Favorit / gesehen / Fortschritt), damit TalkBack den Kartenzustand ansagt; einmaliger TalkBack-Durchlauf auf echter TV-Hardware als Abnahmekriterium.

---

## 4. Architektur & Wartbarkeit

### 4.1 `AppNavigation.kt` (2.308 Zeilen) aufteilen

Routen-Definitionen, Deep-Link-Parsing, Kategorie-Filterlogik, Fokus-Restore und der NavGraph liegen in einer Datei. **Konkrete Anpassung:** Aufteilen in `Screen.kt` (Routen), `DeepLinkHandler.kt`, `CategoryFiltering.kt` (die pure Logik ist dank `AppNavigationCategorySelectionTest` schon testbar) und je ein NavGraph-Abschnitt pro Feature. Analog existiert für `PlayerScreen.kt` (1.887 Zeilen) bereits Story #34.

### 4.2 Hilt konsequent nutzen statt Durchreichen

- `MainActivity` injiziert 9 Abhängigkeiten und reicht sie als `AppNavigationDependencies` durch den Composable-Baum. Stattdessen: pro Screen `hiltViewModel()`, die ViewModels injizieren ihre Repos selbst.
- `AppModule` enthält viele `@Provides` für Klassen, die bereits `@Inject constructor` haben (z. B. `XtreamRepository`, `UpdateRepository`) – die Methoden ersatzlos streichen. Die `*ServiceDependencies`-Container (nur da, um Parameterzahl-Warnungen zu umgehen) entfallen damit ebenfalls.

### 4.3 Reaktive Datenpipeline statt imperativer StateFlow-Pflege

`MainViewModel` pflegt sechs `MutableStateFlow`s manuell und cached/cleared von Hand. Mit Room (2.1) wird daraus: DAO-`Flow` → `combine` mit Hidden-Categories → `stateIn(viewModelScope)`. Weniger Zustand, weniger Race-Conditions (z. B. kann `loadData()` heute ein veraltetes Ergebnis in den Flow schreiben, wenn der Nutzer schnell Kategorien wechselt – der Job-Cancel deckt nur den letzten Aufruf ab).

### 4.4 Tests gezielt ausbauen

Vorhandene Unit-/Guardrail-Tests sind gut. Lücken mit dem besten Kosten/Nutzen-Verhältnis:
- `XtreamRepository` gegen **MockWebServer** (Erfolg, 5xx-Retry, kaputtes JSON, leere Serien-Antwort – die Retry-Logik ist komplex und ungetestet).
- `handleDeepLink()` (pure suspend-Funktion, leicht testbar: unbekannter Typ, fehlende IDs, fehlende Credentials).
- Nach Room-Migration: DAO-Tests mit In-Memory-DB.

---

## 5. Player-Interaktion & Bedienbarkeit (Detail-Review)

Untersucht: `PlayerScreen.kt`, `CustomPlayerControls.kt`, `VideoProgressBar.kt`, `CentralPlayPauseButton.kt`, `InteractionUtils.kt`, `PlayerViewModel.kt`, `PlayerConstants.kt`.

**Das Interaktionsmodell heute:** Der Player kennt drei D-Pad-Zustände: (1) Overlay versteckt → Links/Rechts spult ±10 s, Hoch/Runter zappt Kanäle (Live), OK togglet Play/Pause; (2) Overlay sichtbar ohne Button-Fokus → Runter fokussiert Play/Pause; (3) Button fokussiert → Links/Rechts navigiert zwischen den Buttons. Das Overlay blendet nach 5 s Inaktivität aus.

**Was gut gelöst ist** (bewahren, nicht wegrefactoren): Das akkumulierende Spulen mit verzögertem Commit (`pendingSeekPosition` + 400 ms `SEEK_COMMIT_DELAY_MS`) ist genau richtig für IPTV — mehrere Tastendrücke erzeugen *einen* Seek-Request statt zehn HTTP-Range-Requests an den Xtream-Server, mit „+30s"-Feedback auf dem Bildschirm. Ebenfalls gut: „endet um HH:MM"-Anzeige, No-Audio-Warnung mit Sprung in die Audio-Settings, Watch-Credits-Modus mit Auto-Play-Countdown, Pause+Progress-Save bei `ON_PAUSE`, Fehlerdialog mit Retry.

### 5.1 🔴 Hardware-Medientasten und MediaSession fehlen komplett

**Fund:** Es gibt keinerlei Behandlung von `Key.MediaPlayPause`, `MediaFastForward`, `MediaRewind`, `MediaNext`, `ChannelUp`, `ChannelDown` (nur `getKeyName()` in `InteractionUtils.kt:49` kennt sie – fürs Logging). Es existiert auch **keine `MediaSession`**.

**Auswirkung:** Auf Fernbedienungen mit dedizierten Tasten (Fire TV, Shield, klassische TV-Remotes, Bluetooth-Tastaturen) passiert beim Druck auf Play/Pause oder FF/REW **nichts**. Ohne MediaSession funktioniert außerdem kein „Hey Google, Pause", keine Now-Playing-Integration des Systems, und Media-Button-Events werden von manchen Geräten gar nicht erst an die App geroutet.

**Konkrete Anpassung:** `androidx.media3:media3-session` einbinden und den ExoPlayer in eine `MediaSession` wrappen (in `rememberPlayerSetup` erzeugen, in `onDispose` releasen). Das erledigt Play/Pause/FF/REW/Next automatisch über den Player. Zusätzlich in `Modifier.onDpadDirection`-Nachbarschaft `ChannelUp`/`ChannelDown` auf `handleChannelUp/Down` mappen — der Handler existiert bereits.

### 5.2 🔴 Spulen ist im Pause-Zustand unmöglich

**Fund:** `handleDpadSeek` (`PlayerScreen.kt:1459`) beginnt mit `if (state.isPlaying)` und gibt sonst `false` zurück. Wer pausiert und dann Links/Rechts drückt, spult nicht — dabei ist „pausieren, Position suchen, weiterschauen" der häufigste Seek-Anwendungsfall überhaupt (und Standard bei Netflix/YouTube/Prime).

**Konkrete Anpassung:** Bedingung auf `state.duration > 0` ändern (spulbar = VOD/Serie mit bekannter Dauer, egal ob pausiert). Für Live bleibt Seek wie bisher deaktiviert, Hoch/Runter zappt weiter.

### 5.3 🟠 Fixe 10-Sekunden-Schritte ohne Beschleunigung

**Fund:** Jeder D-Pad-Druck spult exakt 10 s (`DPAD_SEEK_BACK_MS`/`FORWARD_MS`), gedrosselt auf max. 5 Schritte/s (`SEEK_DEBOUNCE_MS = 200`). Um in einem Zwei-Stunden-Film 30 Minuten zu springen, braucht der Nutzer **180 Tastendrücke bzw. mindestens 36 Sekunden Dauerfeuer**.

**Konkrete Anpassung:** Progressive Schrittweite innerhalb einer Seek-Sequenz — die Infrastruktur (`seekStartPosition`, Commit-Fenster) existiert schon, es fehlt nur ein Zähler: aufeinanderfolgende Drücke innerhalb des Commit-Fensters zählen und die Schrittweite staffeln, z. B. Druck 1–4 → 10 s, 5–9 → 30 s, 10–14 → 60 s, ab 15 → 5 min. Das Feedback-Label („+2m30s") zeigt weiterhin den Gesamt-Offset. Optional als Ausbaustufe: Halten der Taste = kontinuierliches Scrubbing mit Vorschaubild.

### 5.4 🟠 Tote OK-Taste bei sichtbarem Overlay ohne Button-Fokus

**Fund:** `Modifier.onDpadCenter` (`PlayerScreen.kt:1587`) reagiert nur `if (!state.showControls)`. Im Zustand „Overlay sichtbar, kein Button fokussiert" (z. B. direkt nach Hoch-Drücken aus der Button-Zeile) macht OK **gar nichts** — der Nutzer sieht die Controls, drückt OK und muss erst begreifen, dass er zuerst Runter drücken muss.

**Konkrete Anpassung:** Im Zweig `isOverlayWithoutControlFocus` bei OK direkt `onPlayPause()` ausführen und den Fokus auf `PLAY_PAUSE` setzen — identisch zum Verhalten bei verstecktem Overlay. Damit hat OK in jedem Zustand eine Funktion.

### 5.5 🟠 Back verlässt den Player sofort, statt erst das Overlay zu schließen

**Fund:** `Modifier.onBackButton` (`PlayerScreen.kt:1580`) ruft immer direkt `onBack()` auf — auch bei geöffnetem Overlay, geöffnetem Settings-Dialog ausgenommen. TV-Konvention (Leanback-Guidelines, alle großen Streaming-Apps): erster Back-Druck schließt das Overlay, erst der zweite verlässt die Wiedergabe. Aktuell fliegt man beim reflexhaften „Overlay wegdrücken" aus dem Film.

**Konkrete Anpassung:**
```kotlin
onBackButton {
    when {
        state.showControls -> { state.showControls = false; state.focusedButton = null }
        else -> { /* bisheriges Verhalten inkl. hideNextEpisodeButton() */ onBack() }
    }
}
```

### 5.6 🟠 Zwei konkurrierende Seek-Implementierungen mit unterschiedlichem Verhalten

**Fund:** Die On-Screen-Buttons „10 s zurück/vor" (`createPreviousCallback`/`createNextCallback`, `PlayerScreen.kt:723–751`) committen **jeden Druck sofort** per `exoPlayer.seekTo()`. Der D-Pad-Pfad (`handleDpadSeek`) akkumuliert dagegen und committet gebündelt. Dieselbe Nutzerabsicht („mehrmals 10 s vor") verhält sich also je nach Eingabeweg unterschiedlich: Buttons feuern pro Druck einen Request auf den IPTV-Server und ruckeln, D-Pad ist flüssig. Zusätzlich ist es doppelter Code für dasselbe Feature.

**Konkrete Anpassung:** `handleDpadSeek` zu einer gemeinsamen Funktion `accumulateSeek(offsetMs: Long)` verallgemeinern und die Button-Callbacks darauf umstellen. `createPreviousCallback`/`createNextCallback`/`createSeekCallback` entfallen.

### 5.7 🟠 Timeline ist per D-Pad nicht bedienbar

**Fund:** `VideoProgressBar` reagiert ausschließlich auf Touch (`pointerInput`; Drag + Tap-Seek), ist aber nicht fokussierbar. Auf dem TV gilt: Sobald ein Button fokussiert ist, navigieren Links/Rechts nur den Fokus — **präzises Spulen geht bei sichtbarem Overlay nur über die 10-s-Buttons**. Das dominante TV-Pattern (Netflix, YouTube TV) ist umgekehrt: Fokus auf der Timeline, Links/Rechts scrubbt direkt.

**Konkrete Anpassung:** Timeline als fokussierbares Element in die Fokus-Kette aufnehmen (Hoch von der Button-Zeile → Timeline). Bei Fokus auf der Timeline rufen Links/Rechts die gemeinsame `accumulateSeek()`-Logik aus 5.6 auf, der Scrubber-Dot dient als Fokus-Indikator. Damit verhalten sich D-Pad und Touch konsistent.

### 5.8 🟡 Manuell nachgebaute Fokus-Navigation der Controls

**Fund:** `createNavigationHandler` (`CustomPlayerControls.kt:275`) kodiert die Links/Rechts-Reihenfolge aller sechs Buttons von Hand (12 `when`-Fälle), der Fokus-Zustand wird doppelt geführt (Compose-Fokus **und** `state.focusedButton`, synchronisiert über `onFocusChanged` + `LaunchedEffect` + `focusRequesters[...]!!`). Jeder neue Button erfordert Änderungen an vier Stellen; die `!!`-Zugriffe auf die Map sind ein latentes Crash-Risiko; Doppelzustand ist die klassische Quelle für „Fokus verschwindet"-Bugs.

**Konkrete Anpassung:** Die Buttons in eine `Row` mit Standard-Compose-Fokusnavigation stellen (`focusGroup()`, Reihenfolge ergibt sich aus der Composition), `FocusRequester` nur noch für den initialen Fokus. `state.focusedButton` auf ein abgeleitetes „welcher Button ist fokussiert"-Signal reduzieren (für die Auto-Hide-Logik reicht `lastInteractionTime`). Bei einer späteren Migration auf `androidx.tv:tv-material` (siehe 3.3) kommt dieses Verhalten ohnehin aus der Bibliothek.

### 5.9 🟡 Eigenes Long-Press-Framework mit 800 ms Eingabesperre

**Fund:** `InteractionUtils.onLongPress` (ca. 300 Zeilen) implementiert Long-Press-Erkennung für Key- und Touch-Eingaben doppelt, mit eigener Timer-/Blocking-Maschinerie. Nach jedem Long-Press ist das Element **800 ms für alle Eingaben blockiert** (`LONG_PRESS_BLOCKING_PERIOD_MS`) — beim schnellen Navigieren durch Karten fühlt sich das wie eine hängende App an. Dazu loggt jeder einzelne KeyDown/KeyUp mehrzeilig (auch im Release, vgl. Finding 1.4).

**Konkrete Anpassung:** Touch-Seite durch `Modifier.combinedClickable(onLongClick = ...)` ersetzen (Framework-Standard, korrekt mit Cancel/Slop). Key-Seite auf eine kleine KeyUp/KeyDown-Zeitmessung ohne Blocking-Fenster reduzieren — die 800-ms-Sperre behandelt ein Symptom (doppelte Events), dessen Ursache mit `consumeNextRelease` bereits gelöst ist. Ziel: < 80 Zeilen, keine künstliche Eingabeverzögerung.

### 5.10 🟡 Zapping-Lücken bei Live-TV

- **Kein Zapping ohne `categoryId`:** Wird ein Live-Stream aus Favoriten oder per Deep-Link gestartet (`createRoute()` ohne Kategorie), sind Hoch/Runter tot. Fix: Fallback auf die Favoriten-Liste bzw. „alle Kanäle" als Zapping-Kontext.
- **Kein Wrap-around:** Vom letzten Kanal geht es nicht zum ersten (bewusst laut Kommentar, aber unüblich — jeder Fernseher wrappt). Vorschlag: Wrap-around aktivieren.
- **Keine Ziffern-Direktwahl:** Zifferntasten der Fernbedienung könnten direkt eine Kanalnummer anwählen (mit kurzem Overlay „123_"). Nice-to-have nach EPG (3.1).
- CH+/CH−-Tasten: siehe 5.1.

### 5.11 🟡 Kleinere Korrektheits-Findings im Player

| Fund | Problem | Fix |
|---|---|---|
| `restoreWatchProgress` (`PlayerScreen.kt:333`) | `delay(500)` „wait a bit" vor dem Seek — Magic-Sleep kaschiert ein Timing-Problem; bei langsamen Geräten ggf. zu kurz, sonst unnötige Wartezeit | Seek direkt bei `STATE_READY` ausführen (dort ist `seekTo` garantiert gültig); das `delay` entfernen |
| `PlayerProgressRestoreEffect` (`PlayerScreen.kt:806`) | `LaunchedEffect(params.exoPlayer.playbackState)` keyed auf eine **nicht-observable** Player-Property — funktioniert nur zufällig, weil parallel `state.playbackState` Recompositions triggert | Auf `state.playbackState` (Compose-State) keyen |
| `CentralPlayPauseButton` | nutzt `onClick` (inkl. `onDpadCenter`), ist aber nicht `focusable` — der Key-Pfad ist toter Code; Button ist faktisch touch-only | `onDpadCenter` entfernen oder Button fokussierbar machen (nach 5.8 entscheiden) |
| `PlayerPositionUpdateEffect` (`PlayerScreen.kt:855`) | Polling läuft nur `while (isPlaying)` — bei Pause/Buffering friert die Zeit-/Fortschrittsanzeige ein (nach manuellem Seek im Pause-Zustand wird nur teilweise von Hand nachgeführt) | Loop auch bei sichtbarem Overlay laufen lassen (`isPlaying \|\| state.showControls`) |
| `formatTime`-Anzeige bei `duration = C.TIME_UNSET` | Vor dem ersten `STATE_READY` ist `duration` negativ (`TIME_UNSET`) — Anzeige „…/ -0:01"-Artefakte möglich | `duration > 0` als Anzeige-Guard für den Zeitblock |

---

## 6. Priorisierte Roadmap

### Phase 1 – Quick Wins (½–1 Tag, keine Architekturänderung)
| # | Maßnahme | Referenz |
|---|----------|----------|
| 1 | `performSearch`: `loadCachedData()` → `loadCachedDataAsync()` | 2.2 |
| 2 | GitHub-Token rotieren → fine-grained PAT, nur Contents:Read | 1.1 |
| 3 | Log-Stripping (`-assumenosideeffects`) in ProGuard | 1.4 |
| 4 | `items()`-Keys in EPG/Navbar/HiddenCategories | 2.5 |
| 5 | `SimpleDateFormat` → `java.time` | 2.7 |
| 6 | Leanback-Dependency entfernen | 3.3 |
| 7 | `test.txt` löschen; `references/`, `todos/` in `.gitignore` | 1.8 |
| 8 | Player: Spulen auch bei Pause erlauben (`isPlaying` → `duration > 0`) | 5.2 |
| 9 | Player: OK-Taste bei Overlay ohne Fokus = Play/Pause | 5.4 |
| 10 | Player: Back schließt erst das Overlay, dann den Player | 5.5 |
| 11 | Player: `ChannelUp`/`ChannelDown`-Tasten auf Zapping mappen | 5.1 |

### Phase 2 – Sicherheit (ca. 1 Woche)
1. Firebase Anonymous Auth + Security Rules + Username→UID-Migration (1.2)
2. Nav-Routen ohne Credentials: IDs statt Stream-URLs (1.3)
3. Update-Mechanismus token-frei (öffentliches Release-Repo oder Proxy) + SHA-256-Verifikation (1.1, 1.6)

### Phase 3 – Performance-Fundament (1–2 Wochen)
1. Room-Migration des Katalog-Caches inkl. reaktiver ViewModel-Pipeline (2.1, 4.3) — erledigt zugleich Story #2
2. Coil-`ImageLoader` konfigurieren + `AsyncImage` mit Größe (2.4)
3. Dependency-Updates gestuft: Compose BOM/Lifecycle → Media3 → Coil (2.3)

### Phase 4 – Player-Bedienung (2–4 Tage, hoher spürbarer Nutzen)
1. `MediaSession` einführen → Hardware-Medientasten + Assistant (5.1)
2. Seek-Logik vereinheitlichen: Buttons nutzen die akkumulierende D-Pad-Logik (5.6)
3. Progressive Seek-Beschleunigung 10s → 30s → 60s → 5min (5.3)
4. Timeline D-Pad-fokussierbar machen, Links/Rechts scrubbt (5.7)
5. Kleinere Korrektheits-Fixes aus 5.11 (delay(500), Effect-Keys, Position-Polling)
6. Zapping: Wrap-around + Fallback-Kontext ohne categoryId (5.10)

### Phase 5 – UX & Struktur (fortlaufend)
1. EPG-Implementierung: Now/Next auf Karten, dann Timeline-Grid (3.1)
2. i18n konsolidieren + `HardcodedText`-Lint (3.2)
3. Fehler-Mapping mit Retry-UX (3.4)
4. Voice-Search (3.5), Accessibility-Feinschliff (3.6)
5. `AppNavigation`/`PlayerScreen` aufteilen, Hilt-Cleanup (4.1, 4.2)
6. Fokus-Navigation der Player-Controls auf Compose-Standard umstellen, Long-Press-Framework vereinfachen (5.8, 5.9)
7. Schrittweise Compose-for-TV-Migration (3.3)

---

## 7. Grenzen dieser Analyse

- Statisches Code-Audit; die App lief nicht auf TV-Hardware – Performance-Aussagen zu 2.1/2.2 basieren auf den Code-Pfaden, nicht auf Messungen. Vor/nach Phase 3 mit Macrobenchmark oder zumindest `adb shell dumpsys gfxinfo` verifizieren.
- Firestore-Security-Rules liegen nicht im Repo und konnten nicht geprüft werden → in der Firebase Console verifizieren (siehe 1.2).
- Keine Git-History-Analyse auf früher committete Secrets; `local.properties` war nie getrackt, aber ein einmaliger Scan (z. B. `gitleaks detect`) ist billig und empfohlen.
- Kein Dependency-CVE-Audit (kein `npm audit`-Äquivalent gelaufen); die veralteten Versionen aus 2.3 sind der Haupttreiber.
