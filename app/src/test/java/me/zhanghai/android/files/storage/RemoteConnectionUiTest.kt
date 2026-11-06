package me.zhanghai.android.files.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConnectionUiTest {
    @Test
    fun passwordsAndUriUserInfoAreRedactedFromErrors() {
        val message = safeRemoteErrorMessage(
            IllegalStateException("Failed ftp://bob:p455w0rd@server.local/ password=p455w0rd"),
            listOf("p455w0rd")
        )
        assertFalse(message.contains("p455w0rd"))
        assertFalse(message.contains("bob:"))
        assertTrue(message.contains("[redacted]"))
    }

    @Test
    fun privateKeyDataIsNotExposedInDialogMessage() {
        val secret = "BEGIN PRIVATE KEY\\nvery sensitive key data\\nEND PRIVATE KEY"
        val message = safeRemoteErrorMessage(
            IllegalArgumentException("Cannot authenticate using $secret"),
            listOf(secret)
        )
        assertFalse(message.contains("sensitive key data"))
    }

    @Test
    fun largeOrMultilineErrorsUseGenericRecoveryMessage() {
        val message = safeRemoteErrorMessage(
            IllegalStateException("network exception\nPassword is xyz"),
            emptyList()
        )
        assertFalse(message.contains("xyz"))
        assertTrue(message.contains("connection"))
    }
}
