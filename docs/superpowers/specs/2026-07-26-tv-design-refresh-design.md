# Design-Refresh: neutrale Schwarztöne, azurblauer Akzent, eindeutiger Fokus

Datum: 2026-07-26
Status: freigegeben, bereit für die Implementierungsplanung

## Ausgangslage

Vorlage ist ein Dribbble-Shot einer Web-Streaming-Plattform (XPLAY,
https://dribbble.com/shots/19867825-Video-Streaming-Platform). Übernommen wird
daraus die Farbwelt: neutrale Fast-Schwarz-Töne statt der heutigen blaustichigen
Dunkelgrautöne, dazu ein kräftiger Akzent in der Rolle, die im Shot das Pink
`#f20e89` spielt.

Der Shot ist ein Desktop-Web-Design mit Maus. FlintR TV ist eine Leanback-App mit
D-Pad-Bedienung aus mehreren Metern Entfernung. Layoutdichte, Hover-Logik und
Schriftgrößen des Shots sind deshalb **nicht** Teil dieses Vorhabens.

## Umfang

Enthalten:

- Farbsystem auf neutrale Schwarztöne und azurblauen Akzent umstellen
- Fokus- und Selektionsdarstellung eindeutig trennen
- Entfernen des zur Laufzeit nicht erreichbaren Light-Themes und toter Token

Nicht enthalten (bewusst auf später gelegt):

- Righteous als Display-Schrift
- Hero-Bereich und horizontale Reihen auf der Startseite
- Umbau der Navigation oder der Kategorienspalte
- Anpassung der Schriftgrößen an TV-Abstände

## Warum Blau nicht gleich Blau ist

Das bestehende Primary `#1152D4` kann das Pink des Shots nicht ersetzen. Auf
Fast-Schwarz erreicht es nur **4,9:1** Kontrast, das Pink im Shot dagegen
**7,9:1**. Auf dem neuen dunkleren Hintergrund würde `#1152D4` also matter
wirken als heute, nicht kräftiger. Magenta ist von Natur aus hell, tiefes Blau
nicht.

`#1B84FF` erreicht **8,7:1** und übernimmt die Rolle des Pinks damit
gleichwertig. `#1152D4` wird nicht verworfen, sondern bekommt eine klar
abgegrenzte neue Aufgabe: Füllfarbe für Selektion und Buttons, wo weißer Text
darauf liegt und der schwache Kontrast gegen Schwarz keine Rolle spielt.

## Farbsystem

### Neutrale

| Token | alt | neu |
|---|---|---|
| `DarkColors.bgPreview`, `DarkColors.bgList` | `#0B111A` | `#0D0D0F` |
| `DarkColors.bgSidebar` | `#101722` | `#141518` |
| `SharedColors.navSidebarBg` | `#151B26` | `#141518` |
| `DarkColors.bgCard`, `SharedColors.sidebarCard` | `#1A2230` | `#1C1D1F` |
| `DarkColors.bgHover` | `#2D2E30` | `#2A2C2F` |
| `DarkColors.cardBorder` | `#3C4043` | `#2E3033` |
| `DarkColors.textPrimary` | `#E8EAED` | `#F2F3F5` |
| `DarkColors.textSecondary` | `#9AA0A6` | `#8B9096` |
| `SharedColors.contentCardBgColor` | `#000000` | `#1C1D1F` |

Der letzte Wechsel ist zugleich eine Korrektur: Karten liegen heute auf reinem
Schwarz und verschwinden dadurch im Hintergrund, solange kein Artwork geladen
ist. Als Fläche gelesen ist `#1C1D1F` richtiger.

### Akzente

| Token | Wert | Rolle |
|---|---|---|
| `accentColor` | `#1B84FF` | Fokusrahmen und Markenfarbe |
| `selectionFill` (neu) | `#1152D4` | Füllfarbe für Selektion und Buttons |
| `accentBright` (neu) | `#38BDF8` | Fortschrittsbalken, sekundäre Akzente |
| `SharedColors.primaryHover` | `#0E42A8` | unverändert, Button-Hover |
| `accentColorHoverBg` | 14 % `#1B84FF` | war 10 % `#1152D4` |

`accentColor` zeigt künftig auf `#1B84FF`. Da die meisten bestehenden
Verwendungen Rahmen sind, liegen sie damit automatisch richtig. Nur die zwei
Stellen, die `accentColor` als Füllung nutzen, ziehen auf `selectionFill` um:
`MainNavbar.kt:126` und `CategoryRow.kt:83`.

Semantische Farben bleiben unverändert: Fehler, Erfolg, `blocked` und die
Kategorie-Gradienten behalten ihre Werte. Insbesondere bleibt Rot die Farbe für
LIVE und Aufnahme — wird sie blau, geht das Signal im nun überall vorhandenen
Blau unter.

## Fokus- und Selektionsmodell

### Die drei Regeln

**1 — Der Rahmen hängt ausschließlich an `isFocused`.** Nie an `isSelected`, nie
an einer Oder-Verknüpfung. Zu jedem Zeitpunkt existiert genau ein Rahmen auf dem
Bildschirm, und der markiert, wo der Nutzer steht.

**2 — Die Rahmenfarbe richtet sich nach dem Untergrund.** Auf dunkler Fläche
`#1B84FF`, auf der Selektionsfüllung `#1152D4` dagegen Weiß. Grund: Azur auf
diesem Blau erreicht nur **1,76:1** und verschmiert aus Sitzabstand, Weiß
erreicht **6,5:1**. Für den Nutzer bleibt die Regel trotzdem eine einzige —
Rahmen bedeutet Fokus, unabhängig von seiner Farbe.

**3 — Fokus trägt immer ein zweites, farbunabhängiges Signal.** Das fokussierte
Element wird auf Faktor **1,04** skaliert, über `graphicsLayer`, damit kein
Layout-Reflow entsteht und benachbarte Elemente nicht springen. Damit bleibt der
Fokus erkennbar, wenn Farbe versagt: auf hellem Poster-Artwork, bei schlecht
kalibrierten Fernsehern, bei Farbfehlsichtigkeit. Farbe allein darf die
Information nie tragen.

Maße: Rahmenbreite **3dp**, eine Anhebung gegenüber den heutigen 2dp aus
`Dimensions.categoryBorderWidth` — 2dp sind aus Sitzabstand zu dünn. Die
Außenkante für Karten ist 1dp in `#0D0D0F`.

### Die vier Zustände

| Zustand | Füllung | Rahmen |
|---|---|---|
| neutral | Kartenfläche | keiner |
| fokussiert | Kartenfläche | `#1B84FF` |
| ausgewählt | `#1152D4` | keiner |
| ausgewählt + fokussiert | `#1152D4` | Weiß |

### Zu behebende Fehler

An drei Stellen zeichnet heute bereits die reine Selektion einen Rahmen, ohne
dass der Fokus dort steht:

- `CategoryRow.kt:90` — `if (isSelected || hasFocus)`
- `ContentCard.kt:624` — `if (config.isSelected || isFocused)`
- `SeriesEpisodesScreen.kt:927` — `if (isSelected || isFocused)`

Folge: Die ausgewählte Kategorie trägt permanent einen Rahmen, auch wenn der
Fokus längst in der Inhaltsliste steht. Der Nutzer sieht zwei Rahmen und muss
raten, welcher er selbst ist. Mit dem helleren Azur fällt das stärker auf als
bisher.

Ungeklärt und im Audit zu bewerten: `SeriesEpisodesScreen.kt:1143` verodert zwei
verschiedene Fokus-Flags (`isFocused || isFocusedState`). Das kann bei zwei
echten Fokusquellen korrekt sein oder bedeuten, dass dort zwei Rahmen
gleichzeitig gezeichnet werden.

### Zentralisierung

Die Rahmenlogik gehört nicht in 19 Dateien einzeln. Dass heute jede Komponente
sie selbst implementiert, ist die Ursache der drei Fehler und würde sie erneut
entstehen lassen. Stattdessen ein Modifier im Theme-Paket:

```kotlin
fun Modifier.tvFocusRing(
    isFocused: Boolean,
    onSelectionFill: Boolean = false,
    shape: Shape = RoundedCornerShape(Dimensions.radiusMD)
): Modifier
```

Er kapselt alle drei Regeln und zusätzlich die dunkle 1dp-Außenkante für Karten
auf Artwork. Diese Außenkante sorgt dafür, dass das Azur immer zwischen
Karteninhalt und Hintergrund liegt und sich auch von hellem Artwork absetzt —
ohne dass der Rahmen dafür auf Weiß ausweichen muss.

Komponenten geben nur noch an, *ob* sie fokussiert sind. *Wie* das aussieht,
steht an einer Stelle.

## Aufräumen

`FlintRTVTheme` wird genau einmal aufgerufen, in `MainActivity.kt:74`, ohne
Parameter. Damit greift der Default `darkTheme = true`, und es existiert keine
Einstellung, die das umschaltet. `LightColors`, `LightColorScheme` und der
Light-Zweig in `appColors()` sind zur Laufzeit nicht erreichbar.

Zu entfernen:

- `LightColors`, `LightColorScheme`, Light-Verzweigung in `appColors()`
- acht Token ohne jede Referenz außerhalb von `ui/theme`, je Palette:
  `bgSelected`, `accentColorBorderLight`, `navActiveBg`, `navActiveText`,
  `badgeBg`, `badgeText`, `proBadgeBg`, `dangerBorder`
- `navbarVerticalBarWidth`, `-Height`, `-Radius` in `Dimensions.kt:61-63` —
  definiert, nirgends verwendet, und von Modell A nicht gebraucht
- sieben Legacy-Konstanten ohne Referenz: `FocusHighlight`, `FocusBackground`,
  `OnPrimary`, `OnSecondary`, `OnBackground`, `OnSurface`, `OnError`.
  `FocusHighlight` ist ein Material-2-Türkis (`#03DAC6`) ohne Bezug zum Design.

Einzeln zu prüfen statt pauschal zu löschen: `Primary`, `Secondary`,
`Background`, `Surface`, `Error` haben vereinzelte Treffer.

Der größte Gewinn folgt daraus automatisch: Mit nur einer Palette werden
`ThemeSpecificColors`, beide `toThemeSpecificColors()`-Extensions und
`toAppColors()` überflüssig. `appColors()` baut `AppColors` direkt aus
`DarkColors`. Die Kommentarblöcke in `Theme.kt:164` und `Theme.kt:245`
begründen diese Duplikation als „strukturell notwendig" — das gilt nur, solange
zwei Paletten existieren. Rund 120 Zeilen mechanischer Kopiererei entfallen.

## Umsetzungsreihenfolge

Vier Schritte, jeder für sich lauffähig und committbar:

1. **Aufräumen.** Light-Zweig und tote Token entfernen, `appColors()`
   vereinfachen. Optisch keine Änderung, aber die Fläche für alles Weitere
   halbiert sich.
2. **Farbwerte tauschen.** Neutrale auf die Schwarztöne, `accentColor` auf
   `#1B84FF`, `selectionFill` und `accentBright` ergänzen. Ab hier sieht die App
   anders aus.
3. **`Modifier.tvFocusRing` einführen**, die zwei Füllstellen auf
   `selectionFill` umziehen, Robolectric-Test ergänzen.
4. **Audit.** Alle 19 Dateien mit Rahmenlogik auf den Modifier umstellen und die
   drei Fehler beheben.

Betroffene Dateien in Schritt 4: `ContentCard.kt`, `PopupMenu.kt`,
`SearchOverlay.kt`, `UpdateDialog.kt`, `player/CustomPlayerControls.kt`,
`player/NextEpisodeButtonGroup.kt`, `player/VideoSettingsDialog.kt`,
`DataLoadingScreen.kt`, `LoginScreen.kt`, `SeriesEpisodesScreen.kt`,
`SeriesResumeButton.kt`, `SettingsComponents.kt`, `SettingsScreen.kt`,
`SubSettingsGeneral.kt`, `SubSettingsHiddenCategories.kt`, `SubSettingsInfo.kt`,
`SubSettingsPlayback.kt`, `CategoryRow.kt`, `MainNavbar.kt`.

## Tests

Heute existiert kein Theme- oder Fokus-Test. Nach Schritt 3 kommt ein
Robolectric-Test hinzu, der die vier Zustände prüft: Rahmen nur bei Fokus, Weiß
auf Selektionsfüllung, transparent sonst. Genau an dieser Logik sind die drei
Fehler entstanden.

Ablage in `app/src/test`, nicht in `androidTest` — androidTest zählt nicht in die
Sonar-Coverage. Muster liefert `HomeSidebarClickTest.kt`.

Nach Schritt 4 wird je Komponentengruppe geprüft, dass kein Rahmen mehr ohne
Fokus gezeichnet wird.

## Offene Punkte

Keine. Alle Entscheidungen sind getroffen; die Layout-Stufe ist bewusst
ausgeklammert und wird bei Bedarf eigenständig konzipiert.
