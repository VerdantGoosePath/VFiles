/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.random.Random

class InputStreamCopyToTest {
    private val data = Random(42).nextBytes(3 * COPY_BUFFER_SIZE + 17)

    @Test
    fun copiesAllBytesAndReportsTotalProgress() {
        val output = ByteArrayOutputStream()
        var reported = 0L
        ByteArrayInputStream(data).copyTo(output, 0) { reported += it }
        assertArrayEquals(data, output.toByteArray())
        assertEquals(data.size.toLong(), reported)
    }

    @Test
    fun nestedCopyOnSameThreadDoesNotShareBuffer() {
        val inner = Random(7).nextBytes(COPY_BUFFER_SIZE + 5)
        val innerOutput = ByteArrayOutputStream()
        val outerOutput = ByteArrayOutputStream()
        val outerInput = object : InputStream() {
            private val delegate = ByteArrayInputStream(data)
            private var nested = false

            override fun read(): Int = delegate.read()

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!nested) {
                    nested = true
                    ByteArrayInputStream(inner).copyTo(innerOutput, 0, null)
                }
                return delegate.read(b, off, len)
            }
        }
        val checkingOutput = object : OutputStream() {
            override fun write(b: Int) = outerOutput.write(b)

            override fun write(b: ByteArray, off: Int, len: Int) {
                // A nested copy reusing the outer array would overwrite it between read and
                // write; run another nested copy here to make that visible.
                ByteArrayInputStream(inner).copyTo(ByteArrayOutputStream(), 0, null)
                outerOutput.write(b, off, len)
            }
        }
        outerInput.copyTo(checkingOutput, 0, null)
        assertArrayEquals(inner, innerOutput.toByteArray())
        assertArrayEquals(data, outerOutput.toByteArray())
    }
}
