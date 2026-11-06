/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import me.zhanghai.android.files.provider.common.newDirectoryStream
import me.zhanghai.android.files.util.ActionState
import me.zhanghai.android.files.util.isFinished
import me.zhanghai.android.files.util.isReady

class EditSmbServerViewModel : ViewModel() {
    private val _connectState = MutableStateFlow<ActionState<SmbServer, Unit>>(ActionState.Ready())
    val connectState = _connectState.asStateFlow()

    private var connectionJob: Job? = null

    fun connect(server: SmbServer) {
        check(_connectState.value.isReady)
        _connectState.value = ActionState.Running(server)
        connectionJob = viewModelScope.launch {
            _connectState.value = try {
                runInterruptible(Dispatchers.IO) {
                    SmbServerAuthenticator.addTransientServer(server)
                    try {
                        val path = server.path
                        path.fileSystem.use {
                            path.newDirectoryStream().toList()
                        }
                    } finally {
                        SmbServerAuthenticator.removeTransientServer(server)
                    }
                }
                currentCoroutineContext().ensureActive()
                ActionState.Success(server, Unit)
            } catch (_: CancellationException) {
                return@launch
            } catch (e: Exception) {
                ActionState.Error(server, e)
            }
        }
    }

    fun cancelConnecting() {
        connectionJob?.cancel()
        connectionJob = null
        _connectState.value = ActionState.Ready()
    }

    fun finishConnecting() {
        viewModelScope.launch {
            check(_connectState.value.isFinished)
            _connectState.value = ActionState.Ready()
        }
    }
}
