package me.zhanghai.android.files.filelist

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.zhanghai.android.files.compose.theme.VFilesAppearance
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.filejob.FileJobSpeedSample
import me.zhanghai.android.files.filejob.FileJobSpeedTracker
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.sin
import kotlin.random.Random

/** Design-review renders of the speed chart in its section tone (not golden tests). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class TaskSpeedChartPreviewTest {
    @get:Rule val composeRule = createComposeRule()

    private val now = 1_000_000L

    /** Feeds a byte counter through the real tracker, as the app does. */
    private fun track(updates: List<Pair<Long, Long>>): List<FileJobSpeedSample> {
        val tracker = FileJobSpeedTracker()
        for ((time, bytes) in updates) tracker.update(time, bytes, null)
        return tracker.snapshot(now).samples
    }

    /** Updates every ~500 ms with jitter from [from] to [to] at [rate] bytes/s. */
    private fun transfer(from: Long, to: Long, rate: (Long) -> Double, seed: Int = 1): List<Pair<Long, Long>> {
        val random = Random(seed)
        val updates = mutableListOf<Pair<Long, Long>>()
        var t = from
        var bytes = 0.0
        while (t <= to) {
            updates += t to bytes.toLong()
            val step = 400L + random.nextLong(250)
            bytes += rate(t) * step / 1000
            t += step
        }
        return updates
    }

    private val steady = track(transfer(now - 140_000, now - 300, { t ->
        12_000_000.0 * (1 + 0.12 * sin(t / 4_000.0) + 0.05 * sin(t / 900.0))
    }))

    private val rampUp = track(transfer(now - 5_200, now - 200, { 30_000_000.0 }))

    private val bursty = track(run {
        // Many small files: irregular bursts, then quiet gaps of up to a second.
        val random = Random(7)
        val updates = mutableListOf<Pair<Long, Long>>()
        var t = now - 70_000
        var bytes = 0L
        while (t < now - 200) {
            updates += t to bytes
            t += if (random.nextInt(4) == 0) 900L + random.nextLong(600) else 150L + random.nextLong(300)
            bytes += random.nextLong(200_000, 1_600_000)
        }
        updates
    })

    private val stall = track(transfer(now - 70_000, now - 9_000, { 8_000_000.0 }))

    private val spike = track(transfer(now - 70_000, now - 300, { t ->
        if (t in now - 30_000..now - 26_000) 90_000_000.0 else 6_000_000.0
    }))

    private val zero = track(transfer(now - 20_000, now - 300, { 0.0 }))

    private val scenarios = listOf(
        "Steady" to steady, "Ramp-up, 5 s" to rampUp, "Bursty small files" to bursty,
        "Stalled 9 s ago" to stall, "Spike leaving the window" to spike, "All zero" to zero,
        "No samples" to emptyList()
    )

    @Composable
    private fun Section(label: String, samples: List<FileJobSpeedSample>, clock: () -> Long) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    label, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TaskSpeedChart(
                    samples, Modifier.fillMaxWidth().padding(top = 12.dp).height(96.dp),
                    clock = clock
                )
            }
        }
    }

    @Composable
    private fun Gallery() {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, samples) in scenarios) Section(label, samples) { now }
        }
    }

    @Test @Config(qualifiers = "w411dp-h1600dp-xxhdpi")
    fun scenariosLight() = render("task-speed-chart-light", false) { Gallery() }

    @Test @Config(qualifiers = "w411dp-h1600dp-night-xxhdpi")
    fun scenariosDark() = render("task-speed-chart-dark", true) { Gallery() }

    @Test @Config(qualifiers = "w411dp-h1600dp-xxhdpi")
    fun scenariosRtl() = render("task-speed-chart-rtl", false) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Gallery() }
    }

    /**
     * Frames 250 ms apart while samples keep arriving irregularly, then stop: the curve should
     * scroll by the same distance every frame and fall away after the stall grace period.
     */
    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun scrollingFrames() {
        val updates = transfer(now - 40_000, now + 4_000, { t ->
            10_000_000.0 * (1 + 0.3 * sin(t / 3_000.0))
        }, seed = 3)
        val samples = mutableStateOf(emptyList<FileJobSpeedSample>())
        val tracker = FileJobSpeedTracker()
        var fed = 0
        fun feed(until: Long) {
            while (fed < updates.size && updates[fed].first <= until) {
                tracker.update(updates[fed].first, updates[fed].second, null)
                fed++
            }
            samples.value = tracker.snapshot(until).samples
        }
        feed(now)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(16.dp).testTag("live")) {
                        Section("Live", samples.value) { now }
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        val start = composeRule.mainClock.currentTime
        val frames = mutableListOf<Bitmap>()
        for (i in 0 until 12) {
            // Stop feeding after 4 s so the last frames show a stall.
            feed(now + minOf(composeRule.mainClock.currentTime - start, 4_000))
            composeRule.mainClock.advanceTimeBy(if (i == 0) 0 else 750)
            frames += composeRule.onNodeWithTag("live").captureToImage().asAndroidBitmap()
        }
        val strip = Bitmap.createBitmap(frames[0].width, frames.sumOf { it.height }, Bitmap.Config.ARGB_8888)
        Canvas(strip).apply {
            var y = 0f
            for (frame in frames) {
                drawBitmap(frame, 0f, y, null)
                y += frame.height
            }
        }
        save("task-speed-chart-frames", strip)
    }

    private fun render(name: String, dark: Boolean, content: @Composable () -> Unit) {
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance(dark = dark)) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { content() }
            }
        }
        composeRule.waitForIdle()
        save(name, composeRule.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap())
    }

    private fun save(name: String, bitmap: Bitmap) {
        val dir = File("build/m3e-previews").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
