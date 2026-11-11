/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive

import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java8.nio.file.FileVisitResult
import java8.nio.file.Files
import java8.nio.file.LinkOption
import java8.nio.file.Path
import java8.nio.file.SimpleFileVisitor
import java8.nio.file.attribute.BasicFileAttributes
import me.zhanghai.android.files.provider.archive.ArchiveFixtures.MIB
import me.zhanghai.android.files.provider.archive.ArchiveFixtures.toJava8Path
import me.zhanghai.android.files.provider.archive.archiver.ArchiveExtractor
import me.zhanghai.android.files.provider.common.ProgressCopyOption
import me.zhanghai.android.files.provider.common.copyTo
import me.zhanghai.android.files.provider.common.resolveForeign
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Extraction throughput benchmark. Skipped unless the instrumentation argument
 * `archiveBench=true` is passed. Optional arguments (comma separated where plural):
 * `benchFormats` (zip,tar,tar.gz,tar.zst,7z), `benchScenarios` (small,large),
 * `benchMethods` (legacy,new,new1), `benchRuns` (3), `benchLegacyCapSeconds` (120),
 * `benchSmallFiles` (5000).
 *
 * Results go to logcat tag `ArchiveBench` and to `files/archive-bench.csv` in the app's data.
 * Every timed run starts from a refreshed ArchiveFileSystem, so index building is included for
 * all methods.
 */
class ArchiveExtractionBenchmarkTest {
    private enum class Scenario(val label: String) { SMALL("small"), LARGE("large") }

    private enum class Method(val label: String) {
        LEGACY("legacy"),
        NEW("new"),
        NEW_SINGLE_WRITER("new1")
    }

    private class Outcome(
        val status: String,
        val seconds: Double,
        val filesDone: Int,
        val note: String = ""
    )

    private val arguments = InstrumentationRegistry.getArguments()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun argument(name: String, default: String): String =
        arguments.getString(name)?.takeIf { it.isNotBlank() } ?: default

    private fun listArgument(name: String, default: String): List<String> =
        argument(name, default).split(',').map { it.trim() }.filter { it.isNotEmpty() }

    @Test
    fun benchmarkExtraction() {
        assumeTrue(
            "Pass -e archiveBench true to run the archive benchmark",
            argument("archiveBench", "false").toBoolean()
        )
        val formats = listArgument("benchFormats", "zip,tar,tar.gz,tar.zst,7z").map { label ->
            FixtureFormat.values().first { it.label == label }
        }
        val scenarios = listArgument("benchScenarios", "small,large").map { label ->
            Scenario.values().first { it.label == label }
        }
        val methods = listArgument("benchMethods", "legacy,new,new1").map { label ->
            Method.values().first { it.label == label }
        }
        val runs = argument("benchRuns", "3").toInt()
        val legacyCapSeconds = argument("benchLegacyCapSeconds", "120").toLong()
        val smallFiles = argument("benchSmallFiles", "5000").toInt()

        val workDir = File(context.cacheDir, "archive-bench")
        ArchiveFixtures.deleteIfExistsRecursively(workDir)
        workDir.mkdirs()
        val csv = File(context.filesDir, "archive-bench.csv")
        if (!csv.exists()) {
            csv.writeText(
                "timestamp,device,sdk,scenario,format,method,run,status,seconds,files_done," +
                    "files_total,bytes_total,mib_per_s,note\n"
            )
        }
        log("start formats=$formats scenarios=$scenarios methods=$methods runs=$runs " +
            "legacyCap=${legacyCapSeconds}s smallFiles=$smallFiles device=${Build.MODEL}")
        try {
            for (scenario in scenarios) {
                val entries = entriesFor(scenario, smallFiles)
                val fileCount = entries.count { it is FixtureEntry.RegularFile }
                val totalBytes = entries.sumOf { (it as? FixtureEntry.RegularFile)?.size ?: 0L }
                for (format in formats) {
                    val archive = File(workDir, "${scenario.label}.${format.extension}")
                    val buildStart = System.nanoTime()
                    ArchiveFixtures.writeArchive(archive, format, entries)
                    log("fixture ${scenario.label} ${format.label}: ${archive.length()} bytes in " +
                        seconds(buildStart).format2() + " s")
                    for (method in methods) {
                        val outcomes = mutableListOf<Outcome>()
                        // Run 0 is the warm-up; it is recorded but excluded from the median.
                        for (run in 0..runs) {
                            val out = File(workDir, "out")
                            ArchiveFixtures.deleteIfExistsRecursively(out)
                            val outcome = runOnce(method, archive, out, legacyCapSeconds)
                            ArchiveFixtures.deleteIfExistsRecursively(out)
                            record(csv, scenario, format, method, run.toString(), outcome,
                                fileCount, totalBytes)
                            if (run > 0) {
                                outcomes += outcome
                            }
                            // A capped or skipped warm-up would only repeat the same answer.
                            if (outcome.status != "ok") {
                                if (run == 0) {
                                    outcomes += outcome
                                }
                                break
                            }
                        }
                        val median = outcomes.sortedBy { it.seconds }[outcomes.size / 2]
                        record(csv, scenario, format, method, "median", Outcome(
                            median.status, median.seconds, median.filesDone,
                            "n=${outcomes.size} ${median.note}".trim()
                        ), fileCount, totalBytes)
                    }
                    archive.delete()
                }
            }
        } finally {
            ArchiveFixtures.deleteIfExistsRecursively(workDir)
            log("csv at ${csv.absolutePath}")
        }
    }

    private fun entriesFor(scenario: Scenario, smallFiles: Int): List<FixtureEntry> =
        when (scenario) {
            Scenario.SMALL -> buildList {
                // 50 files per directory, close to how source trees and photo dumps look.
                for (i in 0 until smallFiles) {
                    if (i % 50 == 0) {
                        add(FixtureEntry.Directory("d%03d/".format(i / 50), 1_600_000_000L))
                    }
                    add(FixtureEntry.RegularFile(
                        "d%03d/f%05d.dat".format(i / 50, i), 4096, i.toLong(), 1_600_000_000L
                    ))
                }
            }
            Scenario.LARGE -> listOf(
                FixtureEntry.RegularFile("large1.bin", 64 * MIB, 1, 1_600_000_000L),
                FixtureEntry.RegularFile("large2.bin", 64 * MIB, 2, 1_600_000_000L)
            )
        }

    private fun runOnce(method: Method, archive: File, out: File, legacyCapSeconds: Long): Outcome {
        val root = ArchiveFixtures.refreshedArchiveRoot(archive)
        val start = System.nanoTime()
        return when (method) {
            Method.LEGACY -> legacyExtract(root, out.toJava8Path(), start, legacyCapSeconds)
            Method.NEW, Method.NEW_SINGLE_WRITER -> {
                val options = if (method == Method.NEW) {
                    ArchiveExtractor.Options()
                } else {
                    ArchiveExtractor.Options(writerThreads = 1)
                }
                var files = 0
                try {
                    ArchiveExtractor(archive.toJava8Path(), { emptyList() }, options).extract(
                        listOf(ArchiveExtractor.Root(root, out.toJava8Path())),
                        object : RecordingCallback() {
                            override fun onProgress(currentSource: Path, bytes: Long, files0: Int) {
                                files = maxOf(files, files0)
                            }
                        }
                    )
                    Outcome("ok", seconds(start), files)
                } catch (e: UnsupportedOperationException) {
                    Outcome("skipped", 0.0, 0, "ArchiveExtractor unsupported: ${e.message}")
                }
            }
        }
    }

    /** What CopyFileJob does for an extract today: a tree walk with one copyTo() per entry. */
    private fun legacyExtract(
        root: Path,
        target: Path,
        start: Long,
        capSeconds: Long
    ): Outcome {
        var files = 0
        var capped = false
        val deadline = start + capSeconds * 1_000_000_000L
        val progress = ProgressCopyOption(500) {}
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(
                directory: Path,
                attributes: BasicFileAttributes
            ): FileVisitResult {
                directory.copyTo(
                    target.resolveForeign(root.relativize(directory)), LinkOption.NOFOLLOW_LINKS,
                    progress
                )
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                if (System.nanoTime() > deadline) {
                    capped = true
                    return FileVisitResult.TERMINATE
                }
                file.copyTo(
                    target.resolveForeign(root.relativize(file)), LinkOption.NOFOLLOW_LINKS,
                    progress
                )
                ++files
                return FileVisitResult.CONTINUE
            }
        })
        val elapsed = seconds(start)
        return if (capped) {
            Outcome("capped", elapsed, files, ">${capSeconds}s after $files files")
        } else {
            Outcome("ok", elapsed, files)
        }
    }

    private fun record(
        csv: File,
        scenario: Scenario,
        format: FixtureFormat,
        method: Method,
        run: String,
        outcome: Outcome,
        filesTotal: Int,
        bytesTotal: Long
    ) {
        val mibPerSecond = if (outcome.status == "ok" && outcome.seconds > 0) {
            (bytesTotal.toDouble() / MIB / outcome.seconds).format2()
        } else {
            ""
        }
        val line = listOf(
            System.currentTimeMillis().toString(), Build.MODEL.replace(',', ' '),
            Build.VERSION.SDK_INT.toString(), scenario.label, format.label, method.label, run,
            outcome.status, outcome.seconds.format2(), outcome.filesDone.toString(),
            filesTotal.toString(), bytesTotal.toString(), mibPerSecond,
            outcome.note.replace(',', ';')
        ).joinToString(",")
        csv.appendText(line + "\n")
        log(line)
    }

    private fun seconds(start: Long): Double = (System.nanoTime() - start) / 1e9

    private fun Double.format2(): String = String.format(Locale.ROOT, "%.2f", this)

    private fun log(message: String) {
        Log.i("ArchiveBench", message)
    }
}
