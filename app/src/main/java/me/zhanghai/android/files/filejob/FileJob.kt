/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.storage.safeRemoteErrorMessage
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Random

abstract class FileJob {
    val id = Random().nextInt()

    internal lateinit var service: FileJobService
        private set

    fun runOn(service: FileJobService) {
        this.service = service
        try {
            run()
            // TODO: Toast
        } catch (e: InterruptedIOException) {
            // User cancellation is expected; do not log provider paths or credentials.
        } catch (e: Exception) {
            // Provider errors may contain FTP/WebDAV userinfo, tokens or private key data.
            // Never show or log the raw Throwable from a file operation.
            service.showToast(safeRemoteErrorMessage(e, emptyList()))
        } finally {
            service.notificationManager.cancel(id)
            FileJobProgressLiveData.remove(id)
        }
    }

    @Throws(IOException::class)
    protected abstract fun run()
}
