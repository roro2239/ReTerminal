package com.rk.terminal.ui.screens.terminal

import androidx.compose.runtime.mutableStateOf
import com.rk.libcommons.alpineDir
import com.rk.libcommons.application
import com.rk.libcommons.child

object Rootfs {
    val reTerminal = application!!.filesDir.also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }

    var isDownloaded = mutableStateOf(isFilesDownloaded())

    fun isFilesDownloaded(): Boolean {
        return reTerminal.child("alpine.tar.gz").exists() &&
            reTerminal.child("proot").exists() &&
            reTerminal.child("libtalloc.so.2").exists()
    }

    fun markDownloaded(sha256: String) {
        reTerminal.child("alpine.sha256").writeText(sha256)
        isDownloaded.value = true
    }

    fun resetInstalledRootfs() {
        alpineDir().deleteRecursively()
    }
}
