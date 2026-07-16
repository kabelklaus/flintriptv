package com.flintr.tv.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.model.UpdateInfo
import com.flintr.tv.ui.theme.FlintRTVTheme
import com.flintr.tv.utils.DownloadProgress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verhalten des Update-Dialogs als lokale Unit-Tests (Robolectric),
 * damit die Abdeckung in den Jacoco-Report einfließt, den Sonar konsumiert.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class UpdateDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun updateInfo(changelog: String? = "Neue Funktionen und Fehlerbehebungen") = UpdateInfo(
        hasUpdate = true,
        stableVersion = "2.44",
        downloadUrl = "https://example.com/app.apk",
        changelog = changelog
    )

    private fun setDialog(
        downloadProgress: DownloadProgress? = null,
        isDownloading: Boolean = false,
        changelog: String? = "Neue Funktionen und Fehlerbehebungen",
        onUpdateNow: () -> Unit = {},
        onLater: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            FlintRTVTheme {
                UpdateDialog(
                    updateInfo = updateInfo(changelog),
                    currentVersion = "2.43",
                    downloadProgress = downloadProgress,
                    isDownloading = isDownloading,
                    onUpdateNow = onUpdateNow,
                    onLater = onLater
                )
            }
        }
    }

    @Test
    fun zeigtTitelVersionenUndChangelog() {
        setDialog()

        composeTestRule.onNodeWithText("Update verfügbar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Aktuelle Version: 2.43").assertIsDisplayed()
        composeTestRule.onNodeWithText("Neue Version: 2.44").assertIsDisplayed()
        composeTestRule.onNodeWithText("Neue Funktionen und Fehlerbehebungen").assertIsDisplayed()
    }

    @Test
    fun herunterladenKlickLoestUpdateAus() {
        var updateClicked = false
        setDialog(onUpdateNow = { updateClicked = true })

        composeTestRule.onNodeWithText("Herunterladen").performClick()

        assertTrue(updateClicked)
    }

    @Test
    fun spaeterKlickSchliesstDialog() {
        var laterClicked = false
        setDialog(onLater = { laterClicked = true })

        composeTestRule.onNodeWithText("Später").performClick()

        assertTrue(laterClicked)
    }

    @Test
    fun laufenderDownloadZeigtProzentUndDeaktiviertButton() {
        var updateClicked = false
        setDialog(
            downloadProgress = DownloadProgress(bytesDownloaded = 45, totalBytes = 100, isComplete = false),
            isDownloading = true,
            onUpdateNow = { updateClicked = true }
        )

        val button = composeTestRule.onNodeWithText("45% heruntergeladen")
        button.assertIsDisplayed()
        button.assertIsNotEnabled()
        assertFalse(updateClicked)
    }

    @Test
    fun abgeschlossenerDownloadZeigtInstallationUndSchliesstAutomatisch() {
        var laterCalled = false
        setDialog(
            downloadProgress = DownloadProgress(bytesDownloaded = 100, totalBytes = 100, isComplete = true),
            isDownloading = false,
            onLater = { laterCalled = true }
        )

        composeTestRule.onNodeWithText("Installation startet…").assertIsDisplayed()

        composeTestRule.mainClock.advanceTimeBy(600L)
        composeTestRule.waitForIdle()
        assertTrue(laterCalled)
    }

    @Test
    fun ohneChangelogFehltDerChangelogBereich() {
        setDialog(changelog = null)

        composeTestRule.onNodeWithText("Update verfügbar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Neue Funktionen und Fehlerbehebungen").assertDoesNotExist()
    }
}
