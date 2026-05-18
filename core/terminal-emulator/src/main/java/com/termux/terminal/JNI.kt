package com.termux.terminal

internal object JNI {
    init {
        System.loadLibrary("termux")
    }

    @JvmStatic
    external fun createSubprocess(
        cmd: String,
        cwd: String,
        args: Array<String>,
        envVars: Array<String>,
        processId: IntArray,
        rows: Int,
        columns: Int,
        cellWidth: Int,
        cellHeight: Int,
    ): Int

    @JvmStatic
    external fun setPtyWindowSize(
        fd: Int,
        rows: Int,
        cols: Int,
        cellWidth: Int,
        cellHeight: Int,
    )

    @JvmStatic
    external fun waitFor(processId: Int): Int

    @JvmStatic
    external fun close(fileDescriptor: Int)
}
