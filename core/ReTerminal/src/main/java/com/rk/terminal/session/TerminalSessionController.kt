package com.rk.terminal.session

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.rk.libcommons.TerminalCommand
import com.rk.terminal.ui.screens.terminal.MkSession
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

class TerminalSessionController(
    private val onSessionsChanged: () -> Unit = {},
) {
    private val sessions = hashMapOf<String, TerminalSession>()
    private var queuedCommand: TerminalCommand? = null

    val sessionList = mutableStateMapOf<String, Boolean>()
    val currentSession = mutableStateOf("main")

    fun enqueueCommand(command: TerminalCommand) {
        queuedCommand = command
        currentSession.value = command.id
    }

    fun terminateAllSessions() {
        sessions.values.forEach { it.finishIfRunning() }
        sessions.clear()
        sessionList.clear()
        onSessionsChanged()
    }

    fun createSession(id: String, client: TerminalSessionClient, context: Context): TerminalSession {
        val command = queuedCommand?.takeIf { it.id == id }
        if (command != null) {
            queuedCommand = null
        }
        return MkSession.createSession(context, client, id, command).also {
            sessions[id] = it
            sessionList[id] = true
            onSessionsChanged()
        }
    }

    fun getSession(id: String): TerminalSession? {
        return sessions[id]
    }

    fun terminateSession(id: String) {
        runCatching {
            sessions[id]?.apply {
                if (emulator != null) {
                    finishIfRunning()
                }
            }
            sessions.remove(id)
            sessionList.remove(id)
            onSessionsChanged()
        }.onFailure { it.printStackTrace() }
    }

    fun destroy() {
        sessions.values.forEach { it.finishIfRunning() }
        sessions.clear()
        sessionList.clear()
    }

    fun isEmpty(): Boolean {
        return sessions.isEmpty()
    }
}
