package me.zhanghai.android.files.ftpserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtpPortValidationTest {
    @Test fun acceptsOnlyTcpListeningPorts() {
        assertEquals(21, parseFtpListeningPort("21"))
        assertEquals(65535, parseFtpListeningPort("65535"))
        listOf("", "abc", "-1", "0", "65536", "2147483648").forEach {
            assertNull(it, parseFtpListeningPort(it))
        }
    }
}
