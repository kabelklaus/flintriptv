### 1: FIXED

File: `app/src/main/java/com/flintr/tv/ui/components/player/VideoSettingsDialog.kt` (~line 364)

Root cause: `Dialog(onDismissRequest = onDismiss)` used the default `DialogProperties`, which means `usePlatformDefaultWidth = true`. The platform dialog window caps the width at its default (narrow) value, so the Card's `fillMaxWidth(0.9f)` only took 90% of that narrow window, not 90% of the screen. That made the help text wrap.

Changes:
- `Dialog(...)`: added `properties = DialogProperties(usePlatformDefaultWidth = false)`, so the window is no longer capped by the platform width (same pattern as `SubSettingsHiddenCategories.kt:407`)
- Card: `.fillMaxWidth(0.9f)` → `.fillMaxWidth(0.75f)`, now relative to the full screen width: about 720dp on a 1080p TV (960dp wide) and about 600–680dp on a phone in landscape
- Added import `androidx.compose.ui.window.DialogProperties`

Not changed: `fillMaxHeight(0.8f)`, the inner padding (`spacingXL`), the header row (`SpaceBetween`, so the close button stays top right), and the selects (`fillMaxWidth`, so they take the new width automatically). The caller in `PlayerScreen.kt:2027` sets no width, so it needed no change.

Help text: no `maxLines` or `overflow` limits exist. The texts (`fontSizeXS`) fit on one line at about 700dp, so no ellipsis was added.

DONE: dialog width is no longer capped by the platform default; it is now 75% of the screen width.

NOTES:
- Not built or tested (Tirith blocks gradle). The operator needs to check it on a TV and a phone.
- In phone portrait (~400dp) the dialog is now about 300dp wide, narrower than before. If portrait matters, change it to e.g. `.fillMaxWidth(0.75f).widthIn(min = 360.dp)` or use a width that depends on orientation.
- Not committed.
