package com.termux.terminal

import java.nio.charset.StandardCharsets

abstract class TerminalOutput {
    fun write(data: String?) {
        if (data == null) return
        val bytes = data.toByteArray(StandardCharsets.UTF_8)
        write(bytes, 0, bytes.size)
    }

    abstract fun write(data: ByteArray, offset: Int, count: Int)

    abstract fun titleChanged(oldTitle: String?, newTitle: String?)

    abstract fun onCopyTextToClipboard(text: String)

    abstract fun onPasteTextFromClipboard()

    abstract fun onBell()

    abstract fun onColorsChanged()
}
