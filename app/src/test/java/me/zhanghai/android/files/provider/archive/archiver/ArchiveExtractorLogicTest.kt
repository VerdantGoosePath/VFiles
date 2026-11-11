/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive.archiver

import java8.nio.file.Path
import me.zhanghai.android.files.provider.archive.ArchiveFileSystem
import me.zhanghai.android.files.provider.archive.ArchiveFileSystemProvider
import me.zhanghai.android.files.provider.common.ByteStringPath
import me.zhanghai.android.files.provider.common.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveExtractorLogicTest {
    private val archiveFileSystem =
        ArchiveFileSystem(ArchiveFileSystemProvider, ByteStringPath("/a.zip".toByteString()))
    // Stands in for a foreign target provider; only path operations are used.
    private val targetFileSystem =
        ArchiveFileSystem(ArchiveFileSystemProvider, ByteStringPath("/target".toByteString()))

    private val archiveRoot = archiveFileSystem.rootDirectory

    private fun source(path: String): Path = archiveFileSystem.getPath(path)

    private fun target(path: String): Path = targetFileSystem.getPath(path)

    private fun normalize(name: String, isDirectory: Boolean = false): String? =
        ArchiveReader.normalizeEntryPath(archiveRoot, name, isDirectory)?.toString()

    @Test
    fun normalizeEntryPath_resolvesAgainstRoot() {
        assertEquals("/a/b.txt", normalize("a/b.txt"))
        assertEquals("/a/b", normalize("a/b/", true))
        assertEquals("/a/c", normalize("./a/./b/../c"))
    }

    @Test
    fun normalizeEntryPath_preventsTraversal() {
        assertEquals("/etc/passwd", normalize("../../etc/passwd"))
        assertEquals("/etc/passwd", normalize("/../etc/passwd"))
        assertNull(normalize("a/../.."))
        assertNull(normalize("./", true))
    }

    @Test
    fun normalizeEntryPath_keepsOnlyDirectoryRoot() {
        assertEquals("/", normalize("/", true))
        assertNull(normalize("/", false))
    }

    @Test
    fun tree_synthesizesMissingAncestorsTopDown() {
        val root = ArchiveExtractor.Root(archiveRoot, target("/out/a"))
        val tree = ExtractionTree(listOf(root))
        val file = source("/x/y/z.txt")
        val resolution = tree.resolve(file, 0, false) as ExtractionTree.Resolution.Pending
        assertSame(root, resolution.root)
        assertEquals(listOf("/", "/x", "/x/y"), resolution.missingAncestors.map { it.toString() })
        for (ancestor in resolution.missingAncestors) {
            tree.markDirectory(
                ancestor, ExtractionTree.SYNTHETIC_INDEX, tree.getTarget(ancestor, root), true
            )
        }
        assertEquals("/out/a", tree.getTarget(archiveRoot, root).toString())
        val fileTarget = tree.getTarget(file, root)
        assertEquals("/out/a/x/y/z.txt", fileTarget.toString())
        assertTrue(fileTarget.startsWith(root.target))
        val next = tree.resolve(source("/x/y/w.txt"), 1, false)
            as ExtractionTree.Resolution.Pending
        assertTrue(next.missingAncestors.isEmpty())
    }

    @Test
    fun tree_firstDuplicateWinsButRetryOfSameIndexProceeds() {
        val root = ArchiveExtractor.Root(archiveRoot, target("/out"))
        val tree = ExtractionTree(listOf(root))
        tree.markDirectory(archiveRoot, ExtractionTree.SYNTHETIC_INDEX, root.target, true)
        val file = source("/f")
        tree.markNonDirectory(file, 3)
        assertSame(ExtractionTree.Resolution.Duplicate, tree.resolve(file, 7, false))
        assertTrue(tree.resolve(file, 3, false) is ExtractionTree.Resolution.Pending)
        tree.markSkipped(file, 7)
        // A later duplicate must not override the winning entry.
        assertSame(ExtractionTree.Resolution.Duplicate, tree.resolve(file, 8, false))
    }

    @Test
    fun tree_lateDirectoryEntryIsReportedOnce() {
        val root = ArchiveExtractor.Root(archiveRoot, target("/out"))
        val tree = ExtractionTree(listOf(root))
        tree.markDirectory(archiveRoot, ExtractionTree.SYNTHETIC_INDEX, root.target, true)
        val directory = source("/d")
        tree.markDirectory(directory, ExtractionTree.SYNTHETIC_INDEX, target("/out/d"), true)
        val late = tree.resolve(directory, 5, true) as ExtractionTree.Resolution.LateDirectory
        assertEquals("/out/d", late.target.toString())
        assertTrue(late.isCreated)
        assertSame(ExtractionTree.Resolution.Duplicate, tree.resolve(directory, 6, true))
    }

    @Test
    fun tree_childrenOfNonDirectoriesAndSkippedDirectoriesAreUnreachable() {
        val root = ArchiveExtractor.Root(archiveRoot, target("/out"))
        val tree = ExtractionTree(listOf(root))
        tree.markDirectory(archiveRoot, ExtractionTree.SYNTHETIC_INDEX, root.target, true)
        // E.g. a symbolic link to somewhere outside the target, followed by a file "through" it.
        tree.markNonDirectory(source("/link"), 0)
        assertSame(
            ExtractionTree.Resolution.Unreachable, tree.resolve(source("/link/x"), 1, false)
        )
        tree.markSkipped(source("/skipped"), 2)
        assertSame(
            ExtractionTree.Resolution.Unreachable, tree.resolve(source("/skipped/a/b"), 3, false)
        )
    }

    @Test
    fun tree_selectsOnlyEntriesUnderRoots() {
        val root = ArchiveExtractor.Root(source("/dir"), target("/out/dir"))
        val tree = ExtractionTree(listOf(root))
        assertSame(
            ExtractionTree.Resolution.NotSelected, tree.resolve(source("/other/x"), 0, false)
        )
        assertSame(
            ExtractionTree.Resolution.NotSelected, tree.resolve(source("/dirx"), 1, false)
        )
        val resolution = tree.resolve(source("/dir/x"), 2, false)
            as ExtractionTree.Resolution.Pending
        assertEquals(listOf("/dir"), resolution.missingAncestors.map { it.toString() })
        assertEquals("/out/dir", tree.getTarget(source("/dir"), root).toString())
    }

    @Test
    fun tree_renamedDirectoryRedirectsChildren() {
        val root = ArchiveExtractor.Root(source("/dir"), target("/out/dir"))
        val tree = ExtractionTree(listOf(root))
        tree.markDirectory(source("/dir"), 0, target("/out/dir (1)"), true)
        assertEquals("/out/dir (1)/x", tree.getTarget(source("/dir/x"), root).toString())
    }

    @Test
    fun tree_tracksCreatedDirectories() {
        val root = ArchiveExtractor.Root(archiveRoot, target("/out"))
        val tree = ExtractionTree(listOf(root))
        assertFalse(tree.isInCreatedDirectory(archiveRoot, root))
        tree.markDirectory(archiveRoot, ExtractionTree.SYNTHETIC_INDEX, root.target, false)
        assertFalse(tree.isInCreatedDirectory(source("/a"), root))
        tree.markDirectory(source("/a"), 0, target("/out/a"), true)
        assertTrue(tree.isInCreatedDirectory(source("/a/b"), root))
        assertTrue(tree.contains(source("/a")))
        assertFalse(tree.contains(source("/a/b")))
    }

    @Test
    fun byteBudget_boundsBufferedBytes() {
        val budget = ByteBudget(100)
        assertTrue(budget.tryAcquire(60))
        assertTrue(budget.tryAcquire(40))
        assertFalse(budget.tryAcquire(1))
        budget.release(40)
        assertEquals(60, budget.usedBytes)
        assertFalse(budget.tryAcquire(41))
        assertTrue(budget.tryAcquire(0))
        budget.release(60)
        assertEquals(0, budget.usedBytes)
    }

    @Test
    fun byteBudget_admitsOversizedRequestOnlyWhenEmpty() {
        val budget = ByteBudget(10)
        assertTrue(budget.tryAcquire(50))
        assertFalse(budget.tryAcquire(1))
        budget.release(50)
        assertTrue(budget.tryAcquire(1))
        assertFalse(budget.tryAcquire(50))
    }

    @Test(expected = IllegalArgumentException::class)
    fun byteBudget_rejectsOverRelease() {
        val budget = ByteBudget(10)
        budget.tryAcquire(5)
        budget.release(6)
    }
}
