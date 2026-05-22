package com.rk.terminal.api

import android.util.Log
import com.rk.libcommons.TerminalCommand
import com.rk.terminal.ui.screens.terminal.MkSession
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

data class ReTerminalSessionRequest(
    val id: String,
    val shell: String? = null,
    val args: Array<String> = emptyArray(),
    val workingDir: String? = null,
    val env: Array<String> = emptyArray(),
    val columns: Int = 80,
    val rows: Int = 24,
    val cellWidthPixels: Int = 8,
    val cellHeightPixels: Int = 16,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReTerminalSessionRequest

        if (id != other.id) return false
        if (shell != other.shell) return false
        if (!args.contentEquals(other.args)) return false
        if (workingDir != other.workingDir) return false
        if (!env.contentEquals(other.env)) return false
        if (columns != other.columns) return false
        if (rows != other.rows) return false
        if (cellWidthPixels != other.cellWidthPixels) return false
        if (cellHeightPixels != other.cellHeightPixels) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (shell?.hashCode() ?: 0)
        result = 31 * result + args.contentHashCode()
        result = 31 * result + (workingDir?.hashCode() ?: 0)
        result = 31 * result + env.contentHashCode()
        result = 31 * result + columns
        result = 31 * result + rows
        result = 31 * result + cellWidthPixels
        result = 31 * result + cellHeightPixels
        return result
    }
}

interface ReTerminalSessionListener {
    fun onTextChanged(session: ReTerminalSessionHandle, transcript: String) {}
    fun onTitleChanged(session: ReTerminalSessionHandle, title: String?) {}
    fun onFinished(session: ReTerminalSessionHandle) {}
}

class ReTerminalSessionHandle internal constructor(
    val id: String,
    private val terminalSession: TerminalSession,
) {
    val transcript: String
        get() = terminalSession.emulator?.screen?.getTranscriptText().orEmpty()

    fun write(text: String) {
        terminalSession.write(text)
    }

    fun resize(columns: Int, rows: Int, cellWidthPixels: Int = 8, cellHeightPixels: Int = 16) {
        terminalSession.updateSize(columns, rows, cellWidthPixels, cellHeightPixels)
    }

    fun close() {
        terminalSession.finishIfRunning()
    }
}

internal object ReTerminalSessionFactory {
    fun create(
        request: ReTerminalSessionRequest,
        listener: ReTerminalSessionListener,
    ): ReTerminalSessionHandle {
        ReTerminal.requireRuntimeReady()

        val command = request.shell?.let {
            TerminalCommand(
                shell = request.shell,
                args = request.args,
                id = request.id,
                workingDir = request.workingDir ?: ReTerminal.defaultWorkingDir(),
                env = request.env,
            )
        }

        lateinit var handle: ReTerminalSessionHandle
        val client = HeadlessTerminalSessionClient(
            onTextChanged = { session ->
                listener.onTextChanged(handle, session.emulator?.screen?.getTranscriptText().orEmpty())
            },
            onTitleChanged = { session ->
                listener.onTitleChanged(handle, session.getTitle())
            },
            onFinished = {
                listener.onFinished(handle)
            },
        )
        val session = MkSession.createSession(ReTerminal.requireApplication(), client, request.id, command)
        handle = ReTerminalSessionHandle(request.id, session)
        session.updateSize(request.columns, request.rows, request.cellWidthPixels, request.cellHeightPixels)
        return handle
    }
}

private class HeadlessTerminalSessionClient(
    private val onTextChanged: (TerminalSession) -> Unit,
    private val onTitleChanged: (TerminalSession) -> Unit,
    private val onFinished: (TerminalSession) -> Unit,
) : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) = onTextChanged.invoke(changedSession)
    override fun onTitleChanged(changedSession: TerminalSession) = onTitleChanged.invoke(changedSession)
    override fun onSessionFinished(finishedSession: TerminalSession) = onFinished.invoke(finishedSession)
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) = Unit
    override fun onPasteTextFromClipboard(session: TerminalSession?) = Unit
    override fun onBell(session: TerminalSession) = Unit
    override fun onColorsChanged(session: TerminalSession) = Unit
    override fun onTerminalCursorStateChange(state: Boolean) = Unit
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) = Unit
    override fun getTerminalCursorStyle(): Int = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
    override fun logError(tag: String?, message: String?) {
        Log.e(tag.orEmpty(), message.orEmpty())
    }
    override fun logWarn(tag: String?, message: String?) {
        Log.w(tag.orEmpty(), message.orEmpty())
    }
    override fun logInfo(tag: String?, message: String?) {
        Log.i(tag.orEmpty(), message.orEmpty())
    }
    override fun logDebug(tag: String?, message: String?) {
        Log.d(tag.orEmpty(), message.orEmpty())
    }
    override fun logVerbose(tag: String?, message: String?) {
        Log.v(tag.orEmpty(), message.orEmpty())
    }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag.orEmpty(), message.orEmpty())
        e?.printStackTrace()
    }
    override fun logStackTrace(tag: String?, e: Exception?) {
        e?.printStackTrace()
    }
}
