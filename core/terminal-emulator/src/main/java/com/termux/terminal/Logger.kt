package com.termux.terminal

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

object Logger {
    @JvmStatic
    fun logError(client: TerminalSessionClient?, logTag: String, message: String) {
        client?.logError(logTag, message) ?: Log.e(logTag, message)
    }

    @JvmStatic
    fun logWarn(client: TerminalSessionClient?, logTag: String, message: String) {
        client?.logWarn(logTag, message) ?: Log.w(logTag, message)
    }

    @JvmStatic
    fun logInfo(client: TerminalSessionClient?, logTag: String, message: String) {
        client?.logInfo(logTag, message) ?: Log.i(logTag, message)
    }

    @JvmStatic
    fun logDebug(client: TerminalSessionClient?, logTag: String, message: String) {
        client?.logDebug(logTag, message) ?: Log.d(logTag, message)
    }

    @JvmStatic
    fun logVerbose(client: TerminalSessionClient?, logTag: String, message: String) {
        client?.logVerbose(logTag, message) ?: Log.v(logTag, message)
    }

    @JvmStatic
    fun logStackTraceWithMessage(
        client: TerminalSessionClient?,
        tag: String,
        message: String?,
        throwable: Throwable?,
    ) {
        logError(client, tag, getMessageAndStackTraceString(message, throwable).orEmpty())
    }

    @JvmStatic
    fun getMessageAndStackTraceString(message: String?, throwable: Throwable?): String? =
        when {
            message == null && throwable == null -> null
            message != null && throwable != null -> "$message:\n${getStackTraceString(throwable)}"
            throwable == null -> message
            else -> getStackTraceString(throwable)
        }

    @JvmStatic
    fun getStackTraceString(throwable: Throwable?): String? {
        if (throwable == null) return null
        return StringWriter().use { errors ->
            PrintWriter(errors).use { writer ->
                throwable.printStackTrace(writer)
            }
            errors.toString()
        }
    }
}
