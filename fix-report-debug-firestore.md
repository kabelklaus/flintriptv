# Fix-Report: flintrTV Debug — Firestore tot → Endlos-Spinner (v2.48)

**Datum:** 2026-09-20 · **Diagnose+Fix:** Hermes (Diagnose via ADB auf MiBox .34) + Claude Code (Code-Fix) · **Verifikation:** Hermes

## TL;DR

- **Symptom:** flintrTV Debug v2.48 (Build 106) auf der Wohnzimmer-MiBox (192.168.2.34): App lädt Kategorien aus dem Cache, aber "Favoriten" bleibt leer, Spinner dreht endlos, kein Fehlerdialog.
- **Provider ist unschuldig:** Server-Info-Request der Debug-App kam erfolgreich zurück; Prod v2.45 auf derselben Box läuft mit demselben Account einwandfrei.
- **Root Cause:** Firestore-Verbindung des Debug-Builds schlägt mit `DEVELOPER_ERROR` fehl (7× im Logcat, Prod: 0×). GoogleApiManager/ProviderInstaller verweigern dem Paket `com.flintr.tv.debug` den Zugriff. Alle Firestore-Aufrufe hingen daraufhin ewig in `await()` ohne Timeout → Favoriten-Load nie abgeschlossen, Username-Save nie durch, UI bleibt im Spinner.
- **Code-Fix:** 15s-Timeout auf allen Firestore-Calls (zentrale `await()`-Extension) + Fehler-/Retry-UI in FavoritesContent. Endlos-Spinner ist damit strukturell ausgeschlossen.
- **Console-Fix (Chris, manuell):** Debug-Keystore-SHA-1 für `com.flintr.tv.debug` in Firebase registrieren (Schritte unten).

## Evidenz (ADB, 2 reproduzierte Läufe)

| Beobachtung | Debug v2.48 | Prod v2.45 |
|---|---|---|
| Server-Info (Xtream) | ✅ 200 nach 25 s | ✅ 200 nach 8 s |
| Username saved to Firestore | ❌ fehlt | ✅ |
| Loaded favorites from Firestore | ❌ fehlt | ✅ 30 total, 0,4 s |
| Live-Kategorien (244) via API | ❌ nur Cache | ✅ |
| DEVELOPER_ERROR im Logcat | 7× | 0× |
| ProviderInstaller | FAILED | ok |

Timing: Debug-App schrieb um 12:51 noch normal Cache/Creds (als v2.47), Update auf v2.48 um 13:30 → seitdem Defekt. Der v2.48-Umbau (CredentialsRepository lazy/warmup) korreliert, ist aber NICHT der Auslöser — der Transport (GoogleApiManager) blockiert auf Ebene der Google-Play-Services, bevor Firestore-Code wirkt. Der Code-Fix behandelt die Folge (Hängen), die Ursache liegt in der Firebase/Cloud-Key-Konfiguration.

## Root Cause (technisch)

Der im `google-services.json` eingebettete API-Key (`flintrtv-nativ`) wird von Google Play Services beim Firestore-Verbindungsaufbau gegen die Registry geprüft. Für den Debug-Client (`com.flintr.tv.debug`) existiert keine gültige Kombination aus Paketname + Signatur-Hash (SHA-1/SHA-256) in der Google Cloud Console → `DEVELOPER_ERROR`, Firestore-Kanal tot. Der Prod-Client ist registriert → Prod läuft.

Hinweis: `describe`-Aufruf gegen die Key-Restriktionen wurde im Run nicht freigegeben (gcloud-Bestätigungsprompt) — die exakte Restriktionsform (API-Restriktion vs. App-Restriktion) ist Console-Check. Evidenz für die Ursache ist die DEVELOPER_ERROR-Asymmetrie Debug/Prod.

## Geänderte Dateien (CC-Build)

1. **`app/src/main/java/com/flintr/tv/data/repository/UserRepository.kt`**
   - Zentrale `Task<T>.await()`-Extension (Zeile ~1108) wrapt jetzt `withTimeout(15_000)`. Da ALLE Firestore-Calls durch diese Extension laufen, ist jeder Call bounded — egal ob Verbindungsaufbau, Get oder Set.
2. **`app/src/main/java/com/flintr/tv/data/local/FavoritesRepository.kt`**
   - Timeout/Cancel wird jetzt als sauberes `Result.failure` surfaced; Favoriten-State bleibt konsistent (leer statt halb-geladen). Neuer Einstiegspunkt `retryLoadFromFirestore()` (umgeht Sync-Cooldowm) für den Retry-Button.
3. **`app/src/main/java/com/flintr/tv/ui/tabs/FavoritesContent.kt`**
   - Favoriten-Tab zeigt bei Load-Fehler jetzt Fehlerzustand mit Retry-Button statt ewigem Spinner; Retry triggert `retryLoadFromFirestore()`.

## Verifikation (durch Hermes, echte Runs)

- `:app:compileDebugKotlin` → **BUILD SUCCESSFUL** (EXIT=0)
- `:app:testDebugUnitTest` (gezielt: CredentialsRepository*, FavoritesRepository*, UserRepository*, SecurityGuardrails*, UserRepositoryGuardrails*) mit `--rerun` → **BUILD SUCCESSFUL** (EXIT=0), Guardrails grün (alle `.set()` mit `SetOptions.merge()`, kein Body-Logging)
- `:app:assembleDebug` → **BUILD SUCCESSFUL** (EXIT=0), APK 32,7 MB, installiert auf MiBox .34 (versionName 2.48)
- **Gerätetest (MiBox .34, 18:25):** Fix greift exakt — Logcat zeigt `Failed to load favorites: TimeoutCancellationException: Timed out waiting for 15000 ms` (statt ewigem Hängen), Prozess lebt, UI zeigt „Keine Inhalte geladen" statt Endlos-Spinner. 
- **UX-Restpunkt:** Im TV-Tab → Kategorie „Favoriten" erscheint der generische Leerzustand OHNE Retry-Button. Das Fehler-/Retry-UI von CC liegt im Favoriten-Tab (eigene Nav-Destination). Nach dem Console-Fix obsolete (Favoriten laden dann ja), bei Bedarf Follow-up-CC-Runde.
- **Nicht gelaufen:** volle Suite (zentrales Gate, folgt bei Merge-Vorbereitung)

## Console-Fix (Chris, manuell, ~5 min)

Debug-Keystore-Fingerprint (aus `~/.android/debug.keystore`):

```
SHA-1:   FB:E3:7E:E3:D2:4B:C7:CA:41:DC:EC:C5:77:C6:E7:C7:EB:74:C7:33
SHA-256: 8A:C6:CA:EA:AD:F0:F0:85:E6:28:6F:C3:92:AE:AD:0B:3B:58:54:F7:B1:18:48:CA:49:7E:A1:A3:2B:D0:C3:01
```

1. **Firebase Console** → Projekt **flintrtv** → Zahnrad → Projekteinstellungen → Deine Apps: Prüfen, ob eine Android-App mit Paket **`com.flintr.tv.debug`** existiert. Wenn nein: Android-App hinzufügen mit exakt diesem Paketnamen + SHA-1 oben eintragen → `google-services.json` neu laden und nach `app/google-services.json` ersetzen (Build danach einmal antesten).
2. **Google Cloud Console** → APIs & Services → Credentials → Key **`flintrtv-nativ`** (steckt in google-services.json): Unter "Anwendungseinschränkungen" → Android → sowohl die Release-SHA-1 (Prod) als auch die Debug-SHA-1 oben + beide Paketnamen (`com.flintr.tv`, `com.flintr.tv.debug`) ergänzen. Alternativ: separater Key für Debug.
3. Wenn der Key API-Restriktionen hat: Firestore API + Firebase Installations + FCM müssen erlaubt bleiben.

## Restrisiken / offene Punkte

- Wenn die Console-Fixes nicht greifen, bleibt der Firestore-Pfad im Debug tot — dann via `gcloud services api-keys describe` die Restriktionen exakt prüfen (promptet Bestätigung, daher im Run übersprungen).
- Die Debug-App auf der Box (v2.48/106) hat weiterhin den alten Stand bis zur nächsten Installation.
- v2.47→v2.48-Diff-Verdacht (CredentialsRepository-Umbau) wurde durch die DEVELOPER_ERROR-Asymmetrie entkräftet — der Umbau ist nicht schuld, bleibt aber beobachtenswerter Punkt (Warmup-Race ist theoretisch möglich, hier nicht der Auslöser).
