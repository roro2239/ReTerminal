package com.termux.terminal

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Field
import java.nio.charset.StandardCharsets
import java.util.UUID

class TerminalSession(
    private val mShellPath: String,
    private val mCwd: String,
    private val mArgs: Array<String>,
    private val mEnv: Array<String>,
    private val mTranscriptRows: Int?,
    @JvmField var mClient: TerminalSessionClient,
) : TerminalOutput() {
    @JvmField
    val mHandle: String = UUID.randomUUID().toString()
    @JvmField
    var mEmulator: TerminalEmulator? = null
    private val mProcessToTerminalIOQueue = ByteQueue(4096)
    private val mTerminalToProcessIOQueue = ByteQueue(4096)
    private val mUtf8InputBuffer = ByteArray(5)
    @JvmField
    var mShellPid = 0
    @JvmField
    var mShellExitStatus = 0
    private var mTerminalFileDescriptor = 0
    @JvmField
    var mSessionName: String? = null
    @JvmField
    val mMainThreadHandler: Handler = MainThreadHandler()

    fun updateTerminalSessionClient(client: TerminalSessionClient) {
        mClient = client
        mEmulator?.updateTerminalSessionClient(client)
    }

    fun updateSize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        val emulator = mEmulator
        if (emulator == null) {
            initializeEmulator(columns, rows, cellWidthPixels, cellHeightPixels)
        } else {
            JNI.setPtyWindowSize(mTerminalFileDescriptor, rows, columns, cellWidthPixels, cellHeightPixels)
            emulator.resize(columns, rows, cellWidthPixels, cellHeightPixels)
        }
    }

    fun getTitle(): String? = mEmulator?.title

    fun initializeEmulator(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        mEmulator = TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, mTranscriptRows, mClient)

        val processId = IntArray(1)
        mTerminalFileDescriptor = JNI.createSubprocess(mShellPath, mCwd, mArgs, mEnv, processId, rows, columns, cellWidthPixels, cellHeightPixels)
        mShellPid = processId[0]
        mClient.setTerminalShellPid(this, mShellPid)

        val terminalFileDescriptorWrapped = wrapFileDescriptor(mTerminalFileDescriptor, mClient)

        object : Thread("TermSessionInputReader[pid=$mShellPid]") {
            override fun run() {
                try {
                    FileInputStream(terminalFileDescriptorWrapped).use { termIn: InputStream ->
                        val buffer = ByteArray(4096)
                        while (true) {
                            val read = termIn.read(buffer)
                            if (read == -1) return
                            if (!mProcessToTerminalIOQueue.write(buffer, 0, read)) return
                            mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }.start()

        object : Thread("TermSessionOutputWriter[pid=$mShellPid]") {
            override fun run() {
                val buffer = ByteArray(4096)
                try {
                    FileOutputStream(terminalFileDescriptorWrapped).use { termOut ->
                        while (true) {
                            val bytesToWrite = mTerminalToProcessIOQueue.read(buffer, true)
                            if (bytesToWrite == -1) return
                            termOut.write(buffer, 0, bytesToWrite)
                        }
                    }
                } catch (_: IOException) {
                }
            }
        }.start()

        object : Thread("TermSessionWaiter[pid=$mShellPid]") {
            override fun run() {
                val processExitCode = JNI.waitFor(mShellPid)
                mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(MSG_PROCESS_EXITED, processExitCode))
            }
        }.start()
    }

    override fun write(data: ByteArray, offset: Int, count: Int) {
        if (mShellPid > 0) mTerminalToProcessIOQueue.write(data, offset, count)
    }

    fun writeCodePoint(prependEscape: Boolean, codePoint: Int) {
        if (codePoint > 1114111 || codePoint in 0xD800..0xDFFF) {
            throw IllegalArgumentException("Invalid code point: $codePoint")
        }

        var bufferPosition = 0
        if (prependEscape) mUtf8InputBuffer[bufferPosition++] = 27

        if (codePoint <= 0b1111111) {
            mUtf8InputBuffer[bufferPosition++] = codePoint.toByte()
        } else if (codePoint <= 0b11111111111) {
            mUtf8InputBuffer[bufferPosition++] = (0b11000000 or (codePoint shr 6)).toByte()
            mUtf8InputBuffer[bufferPosition++] = (0b10000000 or (codePoint and 0b111111)).toByte()
        } else if (codePoint <= 0b1111111111111111) {
            mUtf8InputBuffer[bufferPosition++] = (0b11100000 or (codePoint shr 12)).toByte()
            mUtf8InputBuffer[bufferPosition++] = (0b10000000 or (codePoint shr 6 and 0b111111)).toByte()
            mUtf8InputBuffer[bufferPosition++] = (0b10000000 or (codePoint and 0b111111)).toByte()
        } else {
            mUtf8InputBuffer[bufferPosition++] = (0b11110000 or (codePoint shr 18)).toByte()
            mUtf8InputBuffer[bufferPosition++] = (0b10000000 or (codePoint shr 12 and 0b111111)).toByte()
            mUtf8InputBuffer[bufferPosition++] = (0b10000000 or (codePoint shr 6 and 0b111111)).toByte()
            mUtf8InputBuffer[bufferPosition++] = (0b10000000 or (codePoint and 0b111111)).toByte()
        }
        write(mUtf8InputBuffer, 0, bufferPosition)
    }

    val emulator: TerminalEmulator?
        get() = mEmulator

    fun notifyScreenUpdate() {
        mClient.onTextChanged(this)
    }

    fun reset() {
        requireNotNull(mEmulator).reset()
        notifyScreenUpdate()
    }

    fun finishIfRunning() {
        if (isRunning) {
            try {
                Os.kill(mShellPid, OsConstants.SIGKILL)
            } catch (e: ErrnoException) {
                Logger.logWarn(mClient, LOG_TAG, "Failed sending SIGKILL: ${e.message}")
            }
        }
    }

    fun cleanupResources(exitStatus: Int) {
        synchronized(this) {
            mShellPid = -1
            mShellExitStatus = exitStatus
        }
        mTerminalToProcessIOQueue.close()
        mProcessToTerminalIOQueue.close()
        JNI.close(mTerminalFileDescriptor)
    }

    override fun titleChanged(oldTitle: String?, newTitle: String?) {
        mClient.onTitleChanged(this)
    }

    @get:Synchronized
    val isRunning: Boolean
        get() = mShellPid != -1

    @Synchronized
    fun getExitStatus(): Int = mShellExitStatus

    override fun onCopyTextToClipboard(text: String) {
        mClient.onCopyTextToClipboard(this, text)
    }

    override fun onPasteTextFromClipboard() {
        mClient.onPasteTextFromClipboard(this)
    }

    override fun onBell() {
        mClient.onBell(this)
    }

    override fun onColorsChanged() {
        mClient.onColorsChanged(this)
    }

    fun getPid(): Int = mShellPid

    fun getCwd(): String? {
        if (mShellPid < 1) return null
        try {
            val cwdSymlink = String.format("/proc/%s/cwd/", mShellPid)
            val outputPath = File(cwdSymlink).canonicalPath
            var outputPathWithTrailingSlash = outputPath
            if (!outputPath.endsWith("/")) {
                outputPathWithTrailingSlash += '/'
            }
            if (cwdSymlink != outputPathWithTrailingSlash) {
                return outputPath
            }
        } catch (e: IOException) {
            Logger.logStackTraceWithMessage(mClient, LOG_TAG, "Error getting current directory", e)
        } catch (e: SecurityException) {
            Logger.logStackTraceWithMessage(mClient, LOG_TAG, "Error getting current directory", e)
        }
        return null
    }

    @SuppressLint("HandlerLeak")
    inner class MainThreadHandler : Handler(Looper.getMainLooper()) {
        private val mReceiveBuffer = ByteArray(4 * 1024)

        override fun handleMessage(msg: Message) {
            val bytesRead = mProcessToTerminalIOQueue.read(mReceiveBuffer, false)
            if (bytesRead > 0) {
                requireNotNull(mEmulator).append(mReceiveBuffer, bytesRead)
                notifyScreenUpdate()
            }

            if (msg.what == MSG_PROCESS_EXITED) {
                val exitCode = msg.obj as Int
                cleanupResources(exitCode)

                var exitDescription = "\r\n[Process completed"
                if (exitCode > 0) {
                    exitDescription += " (code $exitCode)"
                } else if (exitCode < 0) {
                    exitDescription += " (signal ${-exitCode})"
                }
                exitDescription += " - press Enter]"

                val bytesToWrite = exitDescription.toByteArray(StandardCharsets.UTF_8)
                requireNotNull(mEmulator).append(bytesToWrite, bytesToWrite.size)
                notifyScreenUpdate()
                mClient.onSessionFinished(this@TerminalSession)
            }
        }
    }

    companion object {
        private const val MSG_NEW_INPUT = 1
        private const val MSG_PROCESS_EXITED = 4
        private const val LOG_TAG = "TerminalSession"

        private fun wrapFileDescriptor(fileDescriptor: Int, client: TerminalSessionClient): FileDescriptor {
            val result = FileDescriptor()
            try {
                val descriptorField: Field = try {
                    FileDescriptor::class.java.getDeclaredField("descriptor")
                } catch (_: NoSuchFieldException) {
                    FileDescriptor::class.java.getDeclaredField("fd")
                }
                descriptorField.isAccessible = true
                descriptorField.set(result, fileDescriptor)
            } catch (e: NoSuchFieldException) {
                Logger.logStackTraceWithMessage(client, LOG_TAG, "Error accessing FileDescriptor#descriptor private field", e)
                System.exit(1)
            } catch (e: IllegalAccessException) {
                Logger.logStackTraceWithMessage(client, LOG_TAG, "Error accessing FileDescriptor#descriptor private field", e)
                System.exit(1)
            } catch (e: IllegalArgumentException) {
                Logger.logStackTraceWithMessage(client, LOG_TAG, "Error accessing FileDescriptor#descriptor private field", e)
                System.exit(1)
            }
            return result
        }
    }
}
