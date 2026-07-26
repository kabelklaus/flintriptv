# TV Design-Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Das Farbsystem von FlintR TV auf neutrale Fast-Schwarz-Töne mit azurblauem Akzent umstellen und die Fokusdarstellung so vereindeutigen, dass ein Rahmen ausschließlich Fokus bedeutet.

**Architecture:** Das zur Laufzeit nicht erreichbare Light-Theme fällt weg, wodurch die Zwischenschicht `ThemeSpecificColors` samt ihrer Kopierfunktionen entfällt und `appColors()` die Palette direkt liefert. Die heute in 19 Dateien einzeln implementierte Rahmenlogik zieht in einen einzigen Modifier `tvFocusRing` im Theme-Paket um.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Robolectric + JUnit4 für Unit-Tests, Gradle.

**Spec:** `docs/superpowers/specs/2026-07-26-tv-design-refresh-design.md`

---

## Dateistruktur

| Datei | Verantwortung nach dem Umbau |
|---|---|
| `ui/theme/Color.kt` | Nur noch die Palette: `SharedColors`, `DarkColors`. Keine Light-Palette, keine Legacy-Konstanten. |
| `ui/theme/Theme.kt` | `FlintRTVTheme`, `AppColors`, `appColors()`. Ohne Light-Zweig und ohne Kopierschicht. |
| `ui/theme/FocusRing.kt` (neu) | `Modifier.tvFocusRing` und `focusRingColor` — die einzige Stelle, an der Fokusdarstellung definiert ist. |
| `ui/theme/Dimensions.kt` | Maße, ohne die ungenutzten `navbarVerticalBar*`. |

`FocusRing.kt` wird bewusst eine eigene Datei und nicht Teil von `Theme.kt`: es ist Verhalten, nicht Palette, und `Theme.kt` ist schon groß genug.

---

## Task 1: Light-Theme entfernen

**Files:**
- Modify: `app/src/main/java/com/flintr/tv/ui/theme/Color.kt:54-109`
- Modify: `app/src/main/java/com/flintr/tv/ui/theme/Theme.kt` (Vollersatz)
- Modify: `app/src/main/java/com/flintr/tv/ui/sidebars/CategoryRow.kt:244-252`

Begründung: `FlintRTVTheme` wird nur in `MainActivity.kt:74` ohne Parameter aufgerufen, also greift immer `darkTheme = true`. Es gibt keine Einstellung, die das umschaltet.

- [ ] **Step 1: Ausgangslage sichern**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. Notiere die Anzahl der Tests — sie muss am Ende dieser Task identisch sein.

- [ ] **Step 2: `LightColors` aus Color.kt löschen**

Lösche in `app/src/main/java/com/flintr/tv/ui/theme/Color.kt` den kompletten Block von Zeile 54 bis 109, also den Kommentar `/** Light mode color definitions */` samt `object LightColors { ... }`. Nichts anderes in dieser Datei anfassen.

- [ ] **Step 3: Theme.kt vollständig ersetzen**

Ersetze den gesamten Inhalt von `app/src/main/java/com/flintr/tv/ui/theme/Theme.kt` durch:

```kotlin
package com.flintr.tv.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Farbschema der App. Die App läuft ausschließlich dunkel — ein Light-Theme
 * gab es nie erreichbar und wurde entfernt.
 */
private val DarkColorScheme = darkColorScheme(
    primary = DarkColors.accentColor,
    secondary = DarkColors.accentColor,
    surface = DarkColors.bgList,
    error = DarkColors.dangerText,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onSurface = DarkColors.textPrimary,
    onError = Color.White,
    background = DarkColors.bgPreview,
    surfaceVariant = DarkColors.bgCard
)

@Composable
fun FlintRTVTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * App-spezifische Farben. Einmal aufgebaut statt bei jeder Recomposition,
 * da es nur eine Palette gibt.
 */
private val AppColorsDark = AppColors(
    bgSidebar = DarkColors.bgSidebar,
    bgSidebarCard = DarkColors.bgSidebarCard,
    bgList = DarkColors.bgList,
    bgPreview = DarkColors.bgPreview,
    bgSelected = DarkColors.bgSelected,
    bgHover = DarkColors.bgHover,
    bgCard = DarkColors.bgCard,
    cardBorder = DarkColors.cardBorder,
    textPrimary = DarkColors.textPrimary,
    textSecondary = DarkColors.textSecondary,
    accentColor = DarkColors.accentColor,
    accentColorHoverBg = DarkColors.accentColorHoverBg,
    accentColorBorderLight = DarkColors.accentColorBorderLight,
    navActiveBg = DarkColors.navActiveBg,
    navActiveText = DarkColors.navActiveText,
    badgeBg = DarkColors.badgeBg,
    badgeText = DarkColors.badgeText,
    dangerBg = DarkColors.dangerBg,
    dangerBorder = DarkColors.dangerBorder,
    dangerText = DarkColors.dangerText,
    successColor = DarkColors.successColor,
    errorColor = DarkColors.errorColor,
    successGreen = DarkColors.successGreen,
    buttonPrimary = DarkColors.buttonPrimary,
    buttonPrimaryHover = DarkColors.buttonPrimaryHover,
    proBadgeBg = DarkColors.proBadgeBg,
    iconColor = DarkColors.iconColor,
    iconColorHover = DarkColors.iconColorHover,
    categoryHoverBg = DarkColors.categoryHoverBg,
    categoryDefaultText = DarkColors.categoryDefaultText,
    colorBlocked = SharedColors.blocked,
    avatarPlaceholder = SharedColors.avatarPlaceholder,
    avatarAccentBg = SharedColors.avatarAccentBg,
    avatarAccentText = SharedColors.avatarAccentText,
    gradientIndigoStart = SharedColors.indigo,
    gradientIndigoEnd = SharedColors.indigoEnd,
    gradientPinkStart = SharedColors.pink,
    gradientPinkEnd = SharedColors.pinkEnd,
    gradientTealStart = SharedColors.teal,
    gradientTealEnd = SharedColors.tealEnd,
    gradientOrangeStart = SharedColors.orange,
    gradientRedEnd = SharedColors.red,
    videoPlayerBg = SharedColors.videoPlayerBg,
    favoriteLive = SharedColors.indigo,
    favoriteMovie = SharedColors.pink,
    favoriteSeries = SharedColors.teal,
    overlayBlack = SharedColors.overlayBlack,
    overlayWhite = SharedColors.overlayWhite,
    categoryChannelCountText = SharedColors.slate400
)

@Composable
fun appColors(): AppColors = AppColorsDark

/**
 * Data class für app-spezifische Farben.
 */
data class AppColors(
    val bgSidebar: Color,
    val bgSidebarCard: Color,
    val bgList: Color,
    val bgPreview: Color,
    val bgSelected: Color,
    val bgHover: Color,
    val bgCard: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentColor: Color,
    val accentColorHoverBg: Color,
    val accentColorBorderLight: Color,
    val navActiveBg: Color,
    val navActiveText: Color,
    val badgeBg: Color,
    val badgeText: Color,
    val dangerBg: Color,
    val dangerBorder: Color,
    val dangerText: Color,
    val successColor: Color,
    val errorColor: Color,
    val successGreen: Color,
    val buttonPrimary: Color,
    val buttonPrimaryHover: Color,
    val proBadgeBg: Color,
    val iconColor: Color,
    val iconColorHover: Color,
    val colorBlocked: Color,
    val avatarPlaceholder: Color,
    val avatarAccentBg: Color,
    val avatarAccentText: Color,
    val gradientIndigoStart: Color,
    val gradientIndigoEnd: Color,
    val gradientPinkStart: Color,
    val gradientPinkEnd: Color,
    val gradientTealStart: Color,
    val gradientTealEnd: Color,
    val gradientOrangeStart: Color,
    val gradientRedEnd: Color,
    val videoPlayerBg: Color,
    val favoriteLive: Color,
    val favoriteMovie: Color,
    val favoriteSeries: Color,
    val overlayBlack: Color,
    val overlayWhite: Color,
    val categoryHoverBg: Color,
    val categoryDefaultText: Color,
    val categoryChannelCountText: Color
)
```

Damit entfallen `LightColorScheme`, `ColorScheme.isDark`, `ThemeSpecificColors`, beide `toThemeSpecificColors()`-Extensions und `toAppColors()`. Die Token-Liste bleibt in dieser Task noch unverändert — aufgeräumt wird sie in Task 2, damit die beiden Änderungen getrennt reviewbar sind.

- [ ] **Step 4: `isDark`-Verwendung in CategoryRow auflösen**

In `app/src/main/java/com/flintr/tv/ui/sidebars/CategoryRow.kt` die Zeile 32 löschen:

```kotlin
import com.flintr.tv.ui.theme.isDark
```

Zeile 244 löschen:

```kotlin
    val isDark = colorScheme.isDark
```

Zeile 252 ersetzen:

```kotlin
                if (isDark) SharedColors.sidebarCard else colors.bgList
```

durch:

```kotlin
                SharedColors.sidebarCard
```

Falls `colorScheme` nach dem Löschen von Zeile 244 unbenutzt ist, auch dessen Deklaration entfernen — der Compiler warnt darauf hin.

- [ ] **Step 5: Kompilieren und Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, gleiche Testanzahl wie in Step 1. Optisch darf sich nichts geändert haben — es wurde nur unerreichbarer Code entfernt.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flintr/tv/ui/theme/Color.kt app/src/main/java/com/flintr/tv/ui/theme/Theme.kt app/src/main/java/com/flintr/tv/ui/sidebars/CategoryRow.kt
git commit -m "refactor(theme): remove unreachable light theme

FlintRTVTheme was only ever called without arguments, so the light
branch could not be reached at runtime. Dropping it also removes the
ThemeSpecificColors copy layer, whose duplication only existed to serve
two palettes."
```

---

## Task 2: Tote Token entfernen

**Files:**
- Modify: `app/src/main/java/com/flintr/tv/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/flintr/tv/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/flintr/tv/ui/theme/Dimensions.kt:61-63`

Alle hier entfernten Namen wurden per Volltextsuche über `app/src` als referenzlos außerhalb von `ui/theme` bestätigt.

- [ ] **Step 1: Referenzlosigkeit erneut bestätigen**

Run:
```bash
for t in bgSelected accentColorBorderLight navActiveBg navActiveText badgeBg badgeText proBadgeBg dangerBorder primaryBorderLight navbarVerticalBarWidth navbarVerticalBarHeight navbarVerticalBarRadius FocusHighlight FocusBackground OnPrimary OnSecondary OnBackground OnSurface OnError; do echo "$t -> $(grep -rn "\b$t\b" app/src --include='*.kt' | grep -v 'ui/theme/' | wc -l | tr -d ' ')"; done
```
Expected: Für jeden Namen `-> 0`. Wenn irgendwo nicht 0 steht, diesen Namen aus den folgenden Schritten ausnehmen und im Commit begründen.

- [ ] **Step 2: Token aus `SharedColors` und `DarkColors` löschen**

In `app/src/main/java/com/flintr/tv/ui/theme/Color.kt`:

Aus `SharedColors` diese Zeile löschen:

```kotlin
    val primaryBorderLight = Color(0x331152D4)
```

Aus `DarkColors` diese Zeilen löschen:

```kotlin
    val bgSelected = Color(0xFF414B5C)
    val accentColorBorderLight = SharedColors.primaryBorderLight
    val navActiveBg = Color(0xFF004A77)
    val navActiveText = Color(0xFFC2E7FF)
    val badgeBg = Color(0xFFA8C7FA)
    val badgeText = Color(0xFF0B57D0)
    val dangerBorder = Color(0x73F87171)
    val proBadgeBg = SharedColors.proBadge
```

- [ ] **Step 3: Legacy-Konstanten löschen**

Am Ende von `Color.kt` den gesamten Block ab dem Kommentar `// Legacy compatibility - keep for existing code` bis zum Dateiende löschen, also `Primary`, `Secondary`, `Background`, `Surface`, `Error`, `OnPrimary`, `OnSecondary`, `OnBackground`, `OnSurface`, `OnError`, `FocusHighlight`, `FocusBackground` und `black`.

`FocusHighlight` war `#03DAC6`, ein Material-2-Türkis ohne Bezug zum Design.

- [ ] **Step 4: Felder aus `AppColors` und dem Builder löschen**

In `app/src/main/java/com/flintr/tv/ui/theme/Theme.kt` sowohl aus dem `AppColorsDark`-Builder als auch aus der `AppColors`-Data-Class diese acht Felder entfernen: `bgSelected`, `accentColorBorderLight`, `navActiveBg`, `navActiveText`, `badgeBg`, `badgeText`, `dangerBorder`, `proBadgeBg`.

- [ ] **Step 5: Ungenutzte Maße löschen**

In `app/src/main/java/com/flintr/tv/ui/theme/Dimensions.kt` die Zeilen 61 bis 63 löschen:

```kotlin
    val navbarVerticalBarWidth = size4.dp
    val navbarVerticalBarHeight = 32.0.dp
    val navbarVerticalBarRadius = size2.dp
```

- [ ] **Step 6: Kompilieren und Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, gleiche Testanzahl wie zuvor.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/flintr/tv/ui/theme/
git commit -m "refactor(theme): drop colour tokens with no references

Eight AppColors fields, thirteen legacy constants and three navbar bar
dimensions had no usage anywhere outside the theme package."
```

---

## Task 3: Neue Farbwerte setzen

**Files:**
- Modify: `app/src/main/java/com/flintr/tv/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/flintr/tv/ui/theme/Theme.kt`

- [ ] **Step 1: `SharedColors` auf die neue Palette bringen**

In `app/src/main/java/com/flintr/tv/ui/theme/Color.kt` den Block der Brand-Farben ersetzen. Vorher:

```kotlin
    // Brand/Accent colors
    val primary = Color(0xFF1152D4)
    val primaryHover = Color(0xFF0E42A8)
    val primaryHoverBg = Color(0x1A1152D4)
```

Nachher:

```kotlin
    // Brand/Accent colors
    //
    // accent trägt die Marke und den Fokus. selectionFill füllt ausgewählte
    // Elemente, dort liegt weißer Text darauf und der schwächere Kontrast
    // gegen Schwarz spielt keine Rolle.
    val accent = Color(0xFF1B84FF)
    val accentBright = Color(0xFF38BDF8)
    val selectionFill = Color(0xFF1152D4)
    val primaryHover = Color(0xFF0E42A8)
    val accentHoverBg = Color(0x241B84FF)
```

Ebenfalls in `SharedColors` ersetzen:

```kotlin
    val sidebarCard = Color(0xFF1A2230)
    val navSidebarBg = Color(0xFF151B26)
```

durch:

```kotlin
    val sidebarCard = Color(0xFF1C1D1F)
    val navSidebarBg = Color(0xFF141518)
```

und:

```kotlin
    val contentCardBgColor = Color(0xFF000000) // Black
```

durch:

```kotlin
    // Karten liegen als Fläche über dem Hintergrund, nicht auf reinem Schwarz —
    // sonst verschwinden sie, solange kein Artwork geladen ist.
    val contentCardBgColor = Color(0xFF1C1D1F)
```

- [ ] **Step 2: `DarkColors` auf die neuen Neutralen bringen**

In `object DarkColors` ersetzen:

```kotlin
    val bgSidebar = Color(0xFF101722)
    val bgList = Color(0xFF0B111A)
    val bgPreview = Color(0xFF0B111A)
    val bgHover = Color(0xFF2D2E30)
    val bgCard = Color(0xFF1A2230)
    val cardBorder = Color(0xFF3C4043)
    val textPrimary = Color(0xFFE8EAED)
    val textSecondary = Color(0xFF9AA0A6)
```

durch:

```kotlin
    val bgSidebar = Color(0xFF141518)
    val bgList = Color(0xFF0D0D0F)
    val bgPreview = Color(0xFF0D0D0F)
    val bgHover = Color(0xFF2A2C2F)
    val bgCard = Color(0xFF1C1D1F)
    val cardBorder = Color(0xFF2E3033)
    val textPrimary = Color(0xFFF2F3F5)
    val textSecondary = Color(0xFF8B9096)
```

Und die Akzent-Zuweisungen:

```kotlin
    val accentColor = SharedColors.primary
    val accentColorHoverBg = SharedColors.primaryHoverBg
```

durch:

```kotlin
    val accentColor = SharedColors.accent
    val accentColorHoverBg = SharedColors.accentHoverBg
    val selectionFill = SharedColors.selectionFill
    val accentBright = SharedColors.accentBright
```

Sowie die Button-Farben:

```kotlin
    val buttonPrimary = SharedColors.primary
```

durch:

```kotlin
    val buttonPrimary = SharedColors.selectionFill
```

- [ ] **Step 3: Die neuen Felder in `AppColors` aufnehmen**

In `app/src/main/java/com/flintr/tv/ui/theme/Theme.kt` in die Data-Class `AppColors` direkt nach `accentColorHoverBg` ergänzen:

```kotlin
    val selectionFill: Color,
    val accentBright: Color,
```

und im `AppColorsDark`-Builder direkt nach `accentColorHoverBg = DarkColors.accentColorHoverBg,` ergänzen:

```kotlin
    selectionFill = DarkColors.selectionFill,
    accentBright = DarkColors.accentBright,
```

- [ ] **Step 4: Die zwei Füllstellen auf `selectionFill` umziehen**

In `app/src/main/java/com/flintr/tv/ui/sidebars/CategoryRow.kt:83` ersetzen:

```kotlin
        isSelected -> colors.accentColor
```

durch:

```kotlin
        isSelected -> colors.selectionFill
```

In `app/src/main/java/com/flintr/tv/ui/sidebars/MainNavbar.kt:126` ersetzen:

```kotlin
            isSelected -> colors.accentColor
```

durch:

```kotlin
            isSelected -> colors.selectionFill
```

Wichtig: Nur die Zuweisungen an `backgroundColor` ändern. Die Zuweisungen an `borderColor` in denselben Funktionen bleiben auf `accentColor` — die werden erst in Task 5 angefasst.

- [ ] **Step 5: Kompilieren und Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, gleiche Testanzahl.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flintr/tv/ui/theme/ app/src/main/java/com/flintr/tv/ui/sidebars/
git commit -m "feat(theme): switch to neutral surfaces and azure accent

The former primary #1152D4 only reaches 4.9:1 against the new near-black
background and cannot carry the accent role. It becomes the selection
fill, where white text sits on top, while #1B84FF takes over focus and
brand duty at 8.7:1."
```

---

## Task 4: `Modifier.tvFocusRing` einführen

**Files:**
- Create: `app/src/main/java/com/flintr/tv/ui/theme/FocusRing.kt`
- Create: `app/src/test/java/com/flintr/tv/ui/theme/FocusRingColorTest.kt`

- [ ] **Step 1: Die Rahmenbreite zentral anheben**

Die Fokusrahmen hängen an `Dimensions.listItemHoverBorderWidth`, das an fünf Stellen verwendet wird — `SubSettingsHiddenCategories.kt:339`, `SettingsScreen.kt:372`, `SeriesEpisodesScreen.kt:1092`, `CategoryRow.kt:226` und `MainNavbar.kt:226`. Die Anhebung auf 3dp gehört deshalb nach `Dimensions`, nicht in die einzelnen Dateien.

In `app/src/main/java/com/flintr/tv/ui/theme/Dimensions.kt` bei den Basiswerten ergänzen:

```kotlin
    private const val size3 = 3.0
```

Und im Abschnitt „List Item Hover Style" ersetzen:

```kotlin
    val listItemHoverBorderWidth = categoryBorderWidth
```

durch:

```kotlin
    // 3dp statt 2dp — aus Sitzabstand sind 2dp zu dünn.
    val focusBorderWidth = size3.dp
    val listItemHoverBorderWidth = focusBorderWidth
```

`categoryBorderWidth` bleibt bei 2dp, da es in `PopupMenu.kt:303` einen Dekorationsrahmen zeichnet, keinen Fokusrahmen.

- [ ] **Step 2: Den fehlschlagenden Test schreiben**

Erstelle `app/src/test/java/com/flintr/tv/ui/theme/FocusRingColorTest.kt`:

```kotlin
package com.flintr.tv.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die Rahmenfarbe ist die Stelle, an der Fokus und Selektion früher
 * vermischt wurden. Reine Funktion, deshalb ohne Robolectric testbar.
 */
class FocusRingColorTest {

    @Test
    fun noBorderWhenNotFocused() {
        assertEquals(
            Color.Transparent,
            focusRingColor(isFocused = false, onSelectionFill = false)
        )
    }

    @Test
    fun selectionAloneNeverDrawsABorder() {
        assertEquals(
            "Eine ausgewählte, aber nicht fokussierte Fläche darf keinen Rahmen tragen",
            Color.Transparent,
            focusRingColor(isFocused = false, onSelectionFill = true)
        )
    }

    @Test
    fun focusOnDarkSurfaceUsesAccent() {
        assertEquals(
            SharedColors.accent,
            focusRingColor(isFocused = true, onSelectionFill = false)
        )
    }

    @Test
    fun focusOnSelectionFillUsesWhiteForContrast() {
        assertEquals(
            "Azur auf der blauen Füllung erreicht nur 1,76:1 und verschmiert",
            Color.White,
            focusRingColor(isFocused = true, onSelectionFill = true)
        )
    }
}
```

- [ ] **Step 3: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*FocusRingColorTest*"`
Expected: FAIL — Kompilierfehler „unresolved reference: focusRingColor".

- [ ] **Step 4: Minimale Implementierung schreiben**

Erstelle `app/src/main/java/com/flintr/tv/ui/theme/FocusRing.kt`:

```kotlin
package com.flintr.tv.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Maße der Fokusdarstellung.
 */
object FocusRing {
    /** Zentral in Dimensions gepflegt, weil fünf Bestandsstellen daran hängen. */
    val width = Dimensions.focusBorderWidth

    /** Trennt den Rahmen von hellem Artwork. */
    val innerEdgeWidth = 1.dp

    /** Zweites, farbunabhängiges Fokussignal. */
    const val focusScale = 1.04f

    const val animationMillis = 200
}

/**
 * Die Rahmenfarbe. Ein Rahmen bedeutet ausschließlich Fokus — niemals
 * Selektion. Auf der blauen Selektionsfüllung weicht er auf Weiß aus, weil
 * Azur dort nur 1,76:1 erreicht, Weiß dagegen 6,5:1.
 */
fun focusRingColor(isFocused: Boolean, onSelectionFill: Boolean): Color = when {
    !isFocused -> Color.Transparent
    onSelectionFill -> Color.White
    else -> SharedColors.accent
}

/**
 * Einheitliche Fokusdarstellung für alle fokussierbaren Elemente.
 *
 * @param isFocused ob das Element gerade den Fokus hält. Selektion gehört
 *   ausdrücklich nicht hierher.
 * @param onSelectionFill ob das Element auf der blauen Selektionsfüllung liegt.
 * @param onArtwork ob hinter dem Rahmen Bildmaterial liegt. Dann wird eine
 *   dunkle Innenkante ergänzt, damit der Rahmen sich auch von hellem Artwork
 *   absetzt.
 */
@Composable
fun Modifier.tvFocusRing(
    isFocused: Boolean,
    onSelectionFill: Boolean = false,
    onArtwork: Boolean = false,
    shape: Shape = RoundedCornerShape(Dimensions.radiusMD)
): Modifier {
    val ringColor by animateColorAsState(
        targetValue = focusRingColor(isFocused, onSelectionFill),
        animationSpec = tween(durationMillis = FocusRing.animationMillis),
        label = "focusRingColor"
    )
    val elementScale by animateFloatAsState(
        targetValue = if (isFocused) FocusRing.focusScale else 1f,
        animationSpec = tween(durationMillis = FocusRing.animationMillis),
        label = "focusRingScale"
    )

    return this
        .scale(elementScale)
        .border(width = FocusRing.width, color = ringColor, shape = shape)
        .then(
            if (onArtwork) {
                Modifier
                    .padding(FocusRing.width)
                    .border(
                        width = FocusRing.innerEdgeWidth,
                        color = DarkColors.bgPreview,
                        shape = shape
                    )
                    .padding(FocusRing.innerEdgeWidth)
            } else {
                Modifier
            }
        )
}
```

Hinweis zur Geometrie: `onArtwork = true` rückt den Inhalt um 4dp ein, unabhängig vom Fokuszustand. Die Außenmaße des Elements ändern sich dadurch nicht, das Artwork wird lediglich etwas kleiner. Die Einrückung ist bewusst konstant, damit beim Durchsteppen nichts springt.

- [ ] **Step 5: Test laufen lassen und Erfolg bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*FocusRingColorTest*"`
Expected: PASS, 4 Tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/flintr/tv/ui/theme/FocusRing.kt app/src/test/java/com/flintr/tv/ui/theme/FocusRingColorTest.kt
git commit -m "feat(theme): add single source of truth for focus styling

Every component currently reimplements its own border logic, which is how
three of them ended up drawing a border on selection alone. tvFocusRing
holds the rule in one place: border means focus, nothing else."
```

---

## Task 5: Die drei Fehler beheben

**Files:**
- Modify: `app/src/main/java/com/flintr/tv/ui/sidebars/CategoryRow.kt:75-94`
- Modify: `app/src/main/java/com/flintr/tv/ui/components/ContentCard.kt:617-651`
- Modify: `app/src/main/java/com/flintr/tv/ui/screens/SeriesEpisodesScreen.kt:920-935`
- Test: `app/src/test/java/com/flintr/tv/ui/sidebars/CategoryChipColorsTest.kt` (neu)

An diesen drei Stellen zeichnet heute die reine Selektion einen Rahmen. Folge: Die ausgewählte Kategorie trägt permanent einen Rahmen, auch wenn der Fokus längst in der Inhaltsliste steht.

- [ ] **Step 1: Den fehlschlagenden Test schreiben**

`calculateChipColors` in `CategoryRow.kt:75` ist `private`. Ändere die Sichtbarkeit auf `internal`, damit der Test sie erreicht — ebenso die Data-Class `CategoryChipColors` in Zeile 64:

```kotlin
internal data class CategoryChipColors(
```

```kotlin
internal fun calculateChipColors(
```

Erstelle dann `app/src/test/java/com/flintr/tv/ui/sidebars/CategoryChipColorsTest.kt`:

```kotlin
package com.flintr.tv.ui.sidebars

import androidx.compose.ui.graphics.Color
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.theme.appColorsForTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryChipColorsTest {

    private val colors = appColorsForTest()

    @Test
    fun selectedButNotFocusedChipHasNoBorder() {
        val result = calculateChipColors(isSelected = true, hasFocus = false, colors = colors)

        assertEquals(
            "Die ausgewählte Kategorie darf keinen Rahmen tragen, während der Fokus woanders steht",
            Color.Transparent,
            result.border
        )
    }

    @Test
    fun focusedChipHasAccentBorder() {
        val result = calculateChipColors(isSelected = false, hasFocus = true, colors = colors)

        assertEquals(SharedColors.accent, result.border)
    }

    @Test
    fun selectedAndFocusedChipHasWhiteBorderOnTheFill() {
        val result = calculateChipColors(isSelected = true, hasFocus = true, colors = colors)

        assertEquals(Color.White, result.border)
        assertEquals(SharedColors.selectionFill, result.background)
    }

    @Test
    fun neutralChipHasNoBorder() {
        val result = calculateChipColors(isSelected = false, hasFocus = false, colors = colors)

        assertEquals(Color.Transparent, result.border)
    }
}
```

Der Test braucht eine `AppColors`-Instanz ohne Compose-Kontext. Ergänze dafür am Ende von `app/src/main/java/com/flintr/tv/ui/theme/Theme.kt`:

```kotlin
/**
 * Die Palette ohne Compose-Kontext, für Unit-Tests reiner Farbfunktionen.
 */
fun appColorsForTest(): AppColors = AppColorsDark
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*CategoryChipColorsTest*"`
Expected: FAIL bei `selectedButNotFocusedChipHasNoBorder` und `selectedAndFocusedChipHasWhiteBorderOnTheFill` — erwartet `Transparent` bzw. `White`, bekommt die Akzentfarbe.

- [ ] **Step 3: `calculateChipColors` korrigieren**

In `app/src/main/java/com/flintr/tv/ui/sidebars/CategoryRow.kt` die Zeile 90 ersetzen:

```kotlin
    val border = if (isSelected || hasFocus) colors.accentColor else Color.Transparent
```

durch:

```kotlin
    val border = focusRingColor(isFocused = hasFocus, onSelectionFill = isSelected)
```

Ergänze den Import:

```kotlin
import com.flintr.tv.ui.theme.focusRingColor
```

- [ ] **Step 4: Test laufen lassen und Erfolg bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*CategoryChipColorsTest*"`
Expected: PASS, 4 Tests.

- [ ] **Step 5: `ContentCard` korrigieren**

In `app/src/main/java/com/flintr/tv/ui/components/ContentCard.kt` Zeile 624 ersetzen:

```kotlin
        targetValue = if (config.isSelected || isFocused) colors.accentColor else Color.Transparent,
```

durch:

```kotlin
        targetValue = focusRingColor(isFocused = isFocused, onSelectionFill = config.isSelected),
```

Ergänze den Import:

```kotlin
import com.flintr.tv.ui.theme.focusRingColor
```

Zusätzlich in Zeile 62 die Rahmenbreite anheben:

```kotlin
private val ContentCardBorderWidth = 2.dp // categoryBorderWidth
```

wird zu:

```kotlin
private val ContentCardBorderWidth = FocusRing.width
```

mit Import `import com.flintr.tv.ui.theme.FocusRing`.

- [ ] **Step 6: `SeriesEpisodesScreen` korrigieren**

In `app/src/main/java/com/flintr/tv/ui/screens/SeriesEpisodesScreen.kt` Zeile 927 ersetzen:

```kotlin
        targetValue = if (isSelected || isFocused) colors.accentColor else Color.Transparent,
```

durch:

```kotlin
        targetValue = focusRingColor(isFocused = isFocused, onSelectionFill = isSelected),
```

Ergänze den Import `import com.flintr.tv.ui.theme.focusRingColor`.

Prüfe außerdem Zeile 1143:

```kotlin
        targetValue = if (isFocused || isFocusedState) colors.accentColor else Color.Transparent,
```

Hier werden zwei Fokus-Flags verodert. Stelle fest, woher beide stammen: Wenn sie zwei verschiedene Fokusquellen desselben Elements sind, ist die Verodung korrekt und wird zu `focusRingColor(isFocused = isFocused || isFocusedState, onSelectionFill = false)`. Wenn sie zwei verschiedene Elemente meinen, werden dort zwei Rahmen gleichzeitig gezeichnet — dann im Commit-Text festhalten und die Stelle auf das Flag reduzieren, das tatsächlich zu diesem Element gehört.

- [ ] **Step 7: Alle Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, vier Tests mehr als am Anfang von Task 5.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/flintr/tv/ui/ app/src/main/java/com/flintr/tv/ui/theme/Theme.kt app/src/test/java/com/flintr/tv/ui/sidebars/CategoryChipColorsTest.kt
git commit -m "fix(ui): stop drawing a focus border on selection alone

CategoryRow, ContentCard and SeriesEpisodesScreen each drew their border
when an item was selected, regardless of where the focus actually was.
A selected category kept its border while the user had long moved into
the content list, leaving two borders on screen and no way to tell which
one was the cursor."
```

---

## Task 6: Restliche Komponenten auf den Modifier umstellen

**Files:** je Komponente einzeln, siehe Liste unten.

- [ ] **Step 1: Bestandsaufnahme**

Run:
```bash
grep -rn "\.border(\|borderColor" app/src/main/java --include='*.kt' | grep -v "ui/theme/"
```

Erwartet: Treffer in `PopupMenu.kt`, `SearchOverlay.kt`, `UpdateDialog.kt`, `player/CustomPlayerControls.kt`, `player/NextEpisodeButtonGroup.kt`, `player/VideoSettingsDialog.kt`, `DataLoadingScreen.kt`, `LoginScreen.kt`, `SeriesResumeButton.kt`, `SettingsComponents.kt`, `SettingsScreen.kt`, `SubSettingsGeneral.kt`, `SubSettingsHiddenCategories.kt`, `SubSettingsInfo.kt`, `SubSettingsPlayback.kt`, `MainNavbar.kt` sowie die in Task 5 bereits erledigten `CategoryRow.kt`, `ContentCard.kt`, `SeriesEpisodesScreen.kt`.

- [ ] **Step 2: Je Datei prüfen und umstellen**

Für jede Datei aus Step 1 diese drei Fragen beantworten und dokumentieren:

1. Hängt die Rahmenfarbe an einer Bedingung, die `isSelected`, `isActive` oder Ähnliches ohne Fokus enthält? Dann ist es derselbe Fehler wie in Task 5 und wird auf `focusRingColor(isFocused = …, onSelectionFill = …)` umgestellt.
2. Liegt der Rahmen auf einer gefüllten Akzentfläche? Dann `onSelectionFill = true`.
3. Liegt hinter dem Rahmen Bildmaterial? Dann statt der manuellen `.border()`-Kette der Modifier `.tvFocusRing(isFocused = …, onArtwork = true)`.

Reine Dekorationsrahmen ohne Fokusbezug — etwa Trennlinien oder Umrandungen von Dialogen — bleiben unangetastet. Diese Unterscheidung ist der eigentliche Zweck der Task: nicht jede `.border()` ist ein Fokusrahmen.

Beispiel für die typische Umstellung, hier `MainNavbar.kt:136-140`:

```kotlin
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accentColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
```

wird zu:

```kotlin
    val borderColor by animateColorAsState(
        targetValue = focusRingColor(isFocused = isFocused, onSelectionFill = isSelected),
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
```

`MainNavbar` ist der Fall, in dem `onSelectionFill` wirklich greift: das ausgewählte Item ist mit `selectionFill` gefüllt, und wenn der Fokus darauf steht, muss der Rahmen weiß werden.

- [ ] **Step 3: Nach jeder Datei kompilieren**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit je Datei oder je zusammengehöriger Gruppe**

```bash
git add app/src/main/java/com/flintr/tv/ui/<pfad>
git commit -m "refactor(ui): route <Komponente> focus border through focusRingColor"
```

Die Player-Komponenten dürfen als eine Gruppe committet werden, ebenso die `SubSettings*`-Dateien.

- [ ] **Step 5: Abschlussprüfung**

Run:
```bash
grep -rn "isSelected || \|isActive || \|isSelected ||$" app/src/main/java --include='*.kt' | grep -i "border\|accent"
```
Expected: keine Ausgabe. Jeder verbleibende Treffer ist eine Stelle, an der Selektion noch einen Rahmen zeichnet.

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

---

## Manuelle Abnahme

Automatisierte Tests decken die Farblogik ab, nicht den optischen Eindruck. Nach Task 6 auf einem echten Gerät oder im TV-Emulator prüfen:

1. Mit dem D-Pad durch Navbar, Kategorien und Inhaltsliste steppen. Zu jedem Zeitpunkt darf **genau ein** Rahmen sichtbar sein.
2. Fokus auf die ausgewählte Kategorie stellen — der Rahmen muss weiß sein und sich klar von der blauen Füllung absetzen.
3. Fokus in die Inhaltsliste bewegen — die ausgewählte Kategorie behält ihre Füllung, verliert aber den Rahmen.
4. Eine Karte mit hellem Artwork fokussieren — der azurblaue Rahmen muss sich sichtbar vom Bild absetzen.
5. Aus Sitzabstand prüfen, ob 3dp Rahmenbreite ausreichen. Falls nicht, ist `FocusRing.width` der einzige zu ändernde Wert.
