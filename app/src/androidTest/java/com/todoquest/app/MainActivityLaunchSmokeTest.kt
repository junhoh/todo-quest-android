package com.todoquest.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.todoquest.MainActivity
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityLaunchSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchRendersInitialCalendar() {
        val deviceInfo = collectDeviceInfo()
        val activityState = waitForActivityReady(deviceInfo)

        val calendarScroll = composeRule.onNodeWithTag("task-lazy-list")
        composeRule.onNodeWithTag("battle-map").assertIsDisplayed()
        composeRule.onAllNodesWithText("Todo Quest").assertCountEquals(0)
        calendarScroll.performScrollToNode(hasTestTag("calendar-month-grid"))
        composeRule.onNodeWithTag("calendar-month-grid").assertIsDisplayed()
        calendarScroll.performScrollToNode(hasTestTag("task-list"))
        composeRule.onNodeWithTag("task-list").assertIsDisplayed()
        calendarScroll.performScrollToNode(hasTestTag("add-task-button"))
        composeRule.onNodeWithTag("add-task-button").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-navigation-calendar").assertIsSelected()
        composeRule.onNodeWithTag("bottom-navigation-character").assertIsDisplayed()
        composeRule.onNodeWithTag("battle-map").assertIsDisplayed()
        composeRule.waitForIdle()

        val screenshot = captureAppRender()
        val stats = screenshot.centerBrightnessStats()
        val outputDir = additionalOutputDirectory()
        val screenshotFile = File(outputDir, "main-activity-app-render-${deviceInfo.safeLabel}.png")
        screenshotFile.outputStream().use { output ->
            assertTrue("Unable to encode app render PNG", screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        val metadataFile = File(outputDir, "main-activity-app-render-${deviceInfo.safeLabel}.txt")
        writeMetadata(metadataFile, deviceInfo, activityState, stats, screenshotFile)

        assertFalse(
            String.format(
                Locale.US,
                "App render black screen suspected: blackPixelRatio=%.4f, averageBrightness=%.2f, deviceModel=%s, sdk=%s, release=%s, emulator=%s, renderer=%s, lifecycle=%s, attached=%s, shown=%s, fingerprint=%s, screenshot=%s, metadata=%s",
                stats.blackPixelRatio,
                stats.averageBrightness,
                deviceInfo.model,
                deviceInfo.sdk,
                deviceInfo.release,
                deviceInfo.emulatorLabel,
                deviceInfo.renderer,
                activityState.lifecycleState,
                activityState.attached,
                activityState.shown,
                deviceInfo.fingerprint,
                screenshotFile.absolutePath,
                metadataFile.absolutePath,
            ),
            stats.blackPixelRatio >= BLACK_PIXEL_RATIO_FAILURE_THRESHOLD &&
                stats.averageBrightness <= AVERAGE_BRIGHTNESS_FAILURE_THRESHOLD,
        )
    }

    private fun waitForActivityReady(deviceInfo: DeviceInfo): ActivityRenderState {
        var latestState = collectActivityRenderState()
        runCatching {
            composeRule.waitUntil(timeoutMillis = ACTIVITY_WAIT_TIMEOUT_MILLIS) {
                latestState = collectActivityRenderState()
                latestState.isReady
            }
        }

        assertTrue(
            "MainActivity is not ready for app render capture: deviceModel=${deviceInfo.model}, " +
                "sdk=${deviceInfo.sdk}, release=${deviceInfo.release}, emulator=${deviceInfo.emulatorLabel}, " +
                "renderer=${deviceInfo.renderer}, fingerprint=${deviceInfo.fingerprint}, " +
                "lifecycle=${latestState.lifecycleState}, attached=${latestState.attached}, " +
                "shown=${latestState.shown}, size=${latestState.width}x${latestState.height}",
            latestState.isReady,
        )
        return latestState
    }

    private fun collectActivityRenderState(): ActivityRenderState {
        val scenario = composeRule.activityRule.scenario
        val lifecycleState = scenario.state
        var attached = false
        var shown = false
        var width = 0
        var height = 0
        runCatching {
            scenario.onActivity { activity ->
                val decorView = activity.window.decorView
                attached = decorView.isAttachedToWindow
                shown = decorView.isShown
                width = decorView.width
                height = decorView.height
            }
        }
        return ActivityRenderState(
            lifecycleState = lifecycleState,
            attached = attached,
            shown = shown,
            width = width,
            height = height,
        )
    }

    private fun captureAppRender(): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return composeRule.onRoot().captureToImage().asAndroidBitmap()
        }

        lateinit var bitmap: Bitmap
        composeRule.activityRule.scenario.onActivity { activity ->
            val decorView = activity.window.decorView
            bitmap = Bitmap.createBitmap(decorView.width, decorView.height, Bitmap.Config.ARGB_8888)
            decorView.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun collectDeviceInfo(): DeviceInfo {
        val model = readShellCommand("getprop ro.product.model").orUnknown()
        val sdk = readShellCommand("getprop ro.build.version.sdk").orUnknown()
        val release = readShellCommand("getprop ro.build.version.release").orUnknown()
        val fingerprint = readShellCommand("getprop ro.build.fingerprint").orUnknown()
        val kernelQemu = readShellCommand("getprop ro.kernel.qemu").orUnknown()
        val renderer = readShellCommand("getprop debug.hwui.renderer").orUnknown()
        val emulatorLabel = when (kernelQemu) {
            "1" -> "true"
            "0" -> "false"
            else -> "unknown($kernelQemu)"
        }
        return DeviceInfo(
            model = model,
            sdk = sdk,
            release = release,
            fingerprint = fingerprint,
            emulatorLabel = emulatorLabel,
            renderer = renderer,
            safeLabel = safeFileToken("$model-sdk$sdk-$emulatorLabel"),
        )
    }

    private fun readShellCommand(command: String): String {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        return uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input ->
                input.readBytes().toString(Charsets.UTF_8).trim()
            }
        }
    }

    private fun additionalOutputDirectory(): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val additionalOutputDir = InstrumentationRegistry.getArguments()
            .getString("additionalTestOutputDir")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
        val context = instrumentation.targetContext
        return (additionalOutputDir ?: context.getExternalFilesDir(null) ?: context.filesDir).also(File::mkdirs)
    }

    private fun writeMetadata(
        outputFile: File,
        deviceInfo: DeviceInfo,
        activityState: ActivityRenderState,
        stats: BrightnessStats,
        screenshotFile: File,
    ) {
        outputFile.writeText(
            listOf(
                "model=${deviceInfo.model}",
                "sdk=${deviceInfo.sdk}",
                "release=${deviceInfo.release}",
                "emulator=${deviceInfo.emulatorLabel}",
                "renderer=${deviceInfo.renderer}",
                "fingerprint=${deviceInfo.fingerprint}",
                "lifecycle=${activityState.lifecycleState}",
                "attached=${activityState.attached}",
                "shown=${activityState.shown}",
                "renderSize=${activityState.width}x${activityState.height}",
                String.format(Locale.US, "blackPixelRatio=%.4f", stats.blackPixelRatio),
                String.format(Locale.US, "averageBrightness=%.2f", stats.averageBrightness),
                "screenshot=${screenshotFile.absolutePath}",
            ).joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private fun Bitmap.centerBrightnessStats(): BrightnessStats {
        val left = width / 4
        val right = width - left
        val top = height / 4
        val bottom = height - top
        val stepX = maxOf(1, (right - left) / SAMPLE_TARGET_PER_AXIS)
        val stepY = maxOf(1, (bottom - top) / SAMPLE_TARGET_PER_AXIS)

        var sampleCount = 0
        var blackPixelCount = 0
        var brightnessTotal = 0L
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = getPixel(x, y)
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                if (brightness <= BLACK_BRIGHTNESS_THRESHOLD) {
                    blackPixelCount += 1
                }
                brightnessTotal += brightness.toLong()
                sampleCount += 1
                x += stepX
            }
            y += stepY
        }

        return BrightnessStats(
            blackPixelRatio = blackPixelCount.toDouble() / sampleCount.toDouble(),
            averageBrightness = brightnessTotal.toDouble() / sampleCount.toDouble(),
        )
    }

    private data class ActivityRenderState(
        val lifecycleState: Lifecycle.State,
        val attached: Boolean,
        val shown: Boolean,
        val width: Int,
        val height: Int,
    ) {
        val isReady: Boolean = lifecycleState == Lifecycle.State.RESUMED &&
            attached && shown && width > 0 && height > 0
    }

    private data class BrightnessStats(
        val blackPixelRatio: Double,
        val averageBrightness: Double,
    )

    private data class DeviceInfo(
        val model: String,
        val sdk: String,
        val release: String,
        val fingerprint: String,
        val emulatorLabel: String,
        val renderer: String,
        val safeLabel: String,
    )

    private fun String.orUnknown(): String = takeIf { it.isNotBlank() } ?: UNKNOWN_DEVICE_VALUE

    private fun safeFileToken(rawValue: String): String {
        val safeValue = rawValue
            .replace(Regex("[^A-Za-z0-9_-]+"), "-")
            .trim('-', '_')
        return safeValue.ifBlank { UNKNOWN_DEVICE_VALUE }
    }

    private companion object {
        const val UNKNOWN_DEVICE_VALUE = "unknown"
        const val BLACK_BRIGHTNESS_THRESHOLD = 16
        const val BLACK_PIXEL_RATIO_FAILURE_THRESHOLD = 0.95
        const val AVERAGE_BRIGHTNESS_FAILURE_THRESHOLD = 24
        const val SAMPLE_TARGET_PER_AXIS = 40
        const val ACTIVITY_WAIT_TIMEOUT_MILLIS = 5_000L
    }
}
