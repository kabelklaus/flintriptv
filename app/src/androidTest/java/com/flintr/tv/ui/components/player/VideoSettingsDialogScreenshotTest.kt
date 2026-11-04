package com.flintr.tv.ui.components.player

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.flintr.tv.ui.theme.FlintRTVTheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * Renders [VideoSettingsDialog] on-device with default (no-player) settings state and
 * dumps it as a PNG for design review. Also guards against render regressions: the test
 * fails loudly if the dialog's title is no longer found.
 */
class VideoSettingsDialogScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun videoSettingsDialog_render_screenshot() {
        composeTestRule.setContent {
            FlintRTVTheme {
                VideoSettingsDialog(onDismiss = {})
            }
        }

        composeTestRule.onNodeWithText("Video-Einstellungen").assertExists()

        val bitmap = composeTestRule.onNode(isDialog()).captureToImage().asAndroidBitmap()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outputDir = context.getExternalFilesDir(null)
        val outputFile = File(outputDir, "video_settings_dialog.png")
        FileOutputStream(outputFile).use { stream ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
