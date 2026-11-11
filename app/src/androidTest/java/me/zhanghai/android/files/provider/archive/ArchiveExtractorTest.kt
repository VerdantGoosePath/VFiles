/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive

import androidx.test.platform.app.InstrumentationRegistry
import java8.nio.file.Path
import me.zhanghai.android.files.provider.archive.ArchiveFixtures.MIB
import me.zhanghai.android.files.provider.archive.ArchiveFixtures.toJava8Path
import me.zhanghai.android.files.provider.archive.archiver.ArchiveExtractor
import me.zhanghai.android.files.provider.archive.archiver.ArchiveExtractor.ConflictDecision
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Correctness of [ArchiveExtractor] against fixtures written on device with this app's
 * WriteArchive. Run a single format with
 * `-Pandroid.testInstrumentationRunnerArguments.class=...ArchiveExtractorTest`.
 */
@RunWith(Parameterized::class)
class ArchiveExtractorTest(private val format: FixtureFormat) {
    companion object {
        private const val BASE_MTIME = 1_600_000_000L

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun formats(): List<Array<Any>> = FixtureFormat.values().map { arrayOf<Any>(it) }

        private fun file(name: String, size: Long, index: Int) =
            FixtureEntry.RegularFile(name, size, seed = name.hashCode().toLong() * 31 + index,
                mtimeSeconds = BASE_MTIME + index * 3600L)

        private fun directory(name: String, index: Int) =
            FixtureEntry.Directory(name, BASE_MTIME + index * 60L)

        val TREE_ENTRIES: List<FixtureEntry> = buildList {
            var i = 0
            add(file("top.txt", 1000, i++))
            add(file("empty.bin", 0, i++))
            add(directory("dir/", i++))
            add(directory("dir/nested/", i++))
            add(directory("dir/nested/deeper/", i++))
            add(file("dir/nested/deeper/leaf.txt", 3000, i++))
            add(file("dir/below-threshold.bin", MIB - 1, i++))
            add(file("dir/at-threshold.bin", MIB, i++))
            add(file("dir/above-threshold.bin", MIB + 1, i++))
            add(file("dir/large.bin", 8 * MIB + 123, i++))
            add(directory("dir/emptysub/", i++))
            add(directory("emptydir/", i++))
            add(file("implicit/parent/child.txt", 10, i++))
            add(file("unicode/日本語 ファイル.txt", 4096, i++))
            add(file("unicode/Ünïcödé-файл-\uD83D\uDE00.bin", 777, i++))
            add(FixtureEntry.SymbolicLink("links/to-top", "../top.txt", BASE_MTIME))
            add(FixtureEntry.SymbolicLink("dir/dangling", "does/not/exist", BASE_MTIME))
            for (j in 0 until 40) {
                add(file("many/f%03d.dat".format(j), 4096L + j, i++))
            }
            add(file("dup.txt", 11, i++))
            add(file("dup.txt", 22, i++))
        }

        val CANCEL_ENTRIES: List<FixtureEntry> = buildList {
            for (j in 0 until 300) {
                add(file("bulk/f%04d.dat".format(j), 64 * 1024L, j))
            }
            add(file("bulk/big1.bin", 8 * MIB, 1000))
            add(file("bulk/big2.bin", 8 * MIB, 1001))
        }

        val TRAVERSAL_ENTRIES: List<FixtureEntry> = listOf(
            file("ok.txt", 5, 0),
            file("../evil.txt", 5, 1),
            file("safe/../../evil2.txt", 5, 2),
            file("/abs-evil.txt", 5, 3),
            // A link out of the target followed by an entry beneath it must not write through it.
            FixtureEntry.SymbolicLink("escape", "../../outside", BASE_MTIME),
            file("escape/pwned.txt", 5, 4),
            file("after.txt", 5, 5)
        )
    }

    private lateinit var workDir: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        workDir = File(context.cacheDir, "archive-extractor-test/${format.name}")
        ArchiveFixtures.deleteIfExistsRecursively(workDir)
        assertTrue(workDir.mkdirs())
    }

    @After
    fun tearDown() {
        ArchiveFixtures.deleteIfExistsRecursively(workDir)
    }

    private fun fixture(name: String, entries: List<FixtureEntry>): File {
        val archive = File(workDir, "$name.${format.extension}")
        ArchiveFixtures.writeArchive(archive, format, entries)
        return archive
    }

    private fun extract(
        archive: File,
        source: (Path) -> Path,
        target: File,
        callback: ArchiveExtractor.Callback = RecordingCallback { ConflictDecision.Replace },
        options: ArchiveExtractor.Options = ArchiveExtractor.Options()
    ) {
        val root = ArchiveFixtures.refreshedArchiveRoot(archive)
        ArchiveExtractor(archive.toJava8Path(), { emptyList() }, options).extract(
            listOf(ArchiveExtractor.Root(source(root), target.toJava8Path())), callback
        )
    }

    @Test
    fun extractsWholeArchive() {
        val archive = fixture("tree", TREE_ENTRIES)
        val out = File(workDir, "out")
        extract(archive, { it }, out)
        ArchiveFixtures.assertTreeMatches(
            ArchiveFixtures.expectedTree(TREE_ENTRIES), ArchiveFixtures.snapshot(out)
        )
    }

    @Test
    fun extractsSubdirectory() {
        val archive = fixture("tree", TREE_ENTRIES)
        val out = File(workDir, "out-dir")
        extract(archive, { it.resolve("dir") }, out)
        ArchiveFixtures.assertTreeMatches(
            ArchiveFixtures.expectedTree(TREE_ENTRIES, "dir"), ArchiveFixtures.snapshot(out)
        )
    }

    @Test
    fun extractsSingleFile() {
        val archive = fixture("tree", TREE_ENTRIES)
        val out = File(workDir, "single.bin")
        extract(archive, { it.resolve("dir/large.bin") }, out)
        val expected = TREE_ENTRIES.first { it.name == "dir/large.bin" } as FixtureEntry.RegularFile
        assertEquals(expected.size, out.length())
        assertEquals(
            ArchiveFixtures.contentSha256(expected.seed, expected.size),
            ArchiveFixtures.fileSha256(out)
        )
        assertEquals(expected.mtimeSeconds, android.system.Os.lstat(out.path).st_mtime)
    }

    @Test
    fun writerThreadCountDoesNotChangeResult() {
        val archive = fixture("tree", TREE_ENTRIES)
        val out1 = File(workDir, "out-1")
        val out4 = File(workDir, "out-4")
        extract(archive, { it }, out1, options = ArchiveExtractor.Options(writerThreads = 1))
        extract(archive, { it }, out4, options = ArchiveExtractor.Options(writerThreads = 4))
        val snapshot1 = ArchiveFixtures.snapshot(out1)
        val snapshot4 = ArchiveFixtures.snapshot(out4)
        ArchiveFixtures.assertTreeMatches(ArchiveFixtures.expectedTree(TREE_ENTRIES), snapshot1)
        assertEquals(snapshot1, snapshot4)
    }

    private fun conflictCase(decision: (File) -> ConflictDecision): Pair<File, RecordingCallback> {
        val archive = fixture("tree", TREE_ENTRIES)
        val out = File(workDir, "out-conflict")
        assertTrue(out.mkdirs())
        File(out, "top.txt").writeText("old")
        val callback = RecordingCallback { decision(out) }
        extract(archive, { it }, out, callback)
        assertTrue(
            "onConflict not invoked for pre-existing file: ${callback.conflicts}",
            callback.conflicts.any { it.second.toString() == File(out, "top.txt").path }
        )
        return out to callback
    }

    private fun expectedTop(): Pair<Long, String> {
        val top = TREE_ENTRIES.first { it.name == "top.txt" } as FixtureEntry.RegularFile
        return top.size to ArchiveFixtures.contentSha256(top.seed, top.size)
    }

    @Test
    fun conflictSkipKeepsExistingFile() {
        val (out, _) = conflictCase { ConflictDecision.Skip }
        assertEquals("old", File(out, "top.txt").readText())
        val expected = ArchiveFixtures.expectedTree(TREE_ENTRIES).toMutableMap()
        expected.remove("top.txt")
        val actual = ArchiveFixtures.snapshot(out).toMutableMap()
        actual.remove("top.txt")
        ArchiveFixtures.assertTreeMatches(expected, actual)
    }

    @Test
    fun conflictReplaceOverwritesExistingFile() {
        val (out, _) = conflictCase { ConflictDecision.Replace }
        val (size, sha) = expectedTop()
        assertEquals(size, File(out, "top.txt").length())
        assertEquals(sha, ArchiveFixtures.fileSha256(File(out, "top.txt")))
    }

    @Test
    fun conflictRenameWritesToNewTarget() {
        val (out, _) = conflictCase {
            ConflictDecision.Rename(File(it, "top (renamed).txt").toJava8Path())
        }
        assertEquals("old", File(out, "top.txt").readText())
        val renamed = File(out, "top (renamed).txt")
        val (size, sha) = expectedTop()
        assertEquals(size, renamed.length())
        assertEquals(sha, ArchiveFixtures.fileSha256(renamed))
    }

    @Test
    fun interruptLeavesOnlyCompleteFiles() {
        val archive = fixture("cancel", CANCEL_ENTRIES)
        val out = File(workDir, "out-cancel")
        var progressCalls = 0
        val callback = object : RecordingCallback({ ConflictDecision.Replace }) {
            override fun onProgress(currentSource: Path, bytes: Long, files: Int) {
                super.onProgress(currentSource, bytes, files)
                if (++progressCalls == 3) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        try {
            extract(archive, { it }, out, callback)
            fail("extract() returned normally after interrupt")
        } catch (e: InterruptedIOException) {
            // Expected.
        } catch (e: Throwable) {
            throw AssertionError("Expected InterruptedIOException but got $e", e)
        } finally {
            Thread.interrupted()
        }
        assertTrue("onProgress never called", progressCalls >= 3)
        val expected = ArchiveFixtures.expectedTree(CANCEL_ENTRIES)
        val actual = if (out.exists()) ArchiveFixtures.snapshot(out) else emptyMap()
        val bad = actual.filter { (path, node) -> expected[path]?.contains(node) != true }
        assertTrue("Partial or unexpected files after interrupt: $bad", bad.isEmpty())
        assertTrue(
            "Interrupt did not stop extraction early",
            actual.count { it.value is Node.RegularFile } < CANCEL_ENTRIES.size
        )
    }

    @Test
    fun pathTraversalStaysInsideTarget() {
        val sandbox = File(workDir, "sandbox")
        val out = File(sandbox, "inner/out")
        assertTrue(out.parentFile!!.mkdirs())
        val outside = File(sandbox, "outside")
        assertTrue(outside.mkdirs())
        val archive = fixture("traversal", TRAVERSAL_ENTRIES)
        val callback = object : RecordingCallback({ ConflictDecision.Skip }) {
            override fun onError(
                source: Path,
                target: Path,
                exception: IOException
            ): ArchiveExtractor.ErrorDecision {
                super.onError(source, target, exception)
                return ArchiveExtractor.ErrorDecision.SKIP
            }
        }
        var failure: IOException? = null
        try {
            extract(archive, { it }, out, callback)
        } catch (e: IOException) {
            failure = e
        }
        assertEquals(setOf("inner", "outside"), sandbox.list()!!.toSet())
        assertEquals(emptyList<String>(), outside.list()!!.toList())
        assertEquals(listOf("out"), File(sandbox, "inner").list()!!.toList())
        assertEquals(
            setOf("sandbox", "traversal.${format.extension}"), workDir.list()!!.toSet()
        )
        assertFalse(File("/abs-evil.txt").exists())
        if (out.exists()) {
            for ((path, node) in ArchiveFixtures.snapshot(out, checkMtimes = false)) {
                val resolved = File(out, path).canonicalFile
                if (node !is Node.SymbolicLink) {
                    assertTrue(
                        "$path resolves outside target",
                        resolved.path.startsWith(out.canonicalPath + "/")
                    )
                }
            }
        }
        if (failure == null) {
            assertTrue(File(out, "ok.txt").isFile)
            assertTrue(File(out, "after.txt").isFile)
        }
    }
}
