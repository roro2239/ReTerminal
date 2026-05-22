package com.rk.terminal.runtime

import androidx.compose.runtime.mutableStateOf
import com.rk.libcommons.alpineDir
import com.rk.libcommons.child
import com.rk.terminal.api.ReTerminal
import java.io.File

object RuntimeEnvironment {
    val reTerminal: File
        get() = ReTerminal.requireApplication().filesDir.also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }

    val isDownloaded = mutableStateOf(false)

    fun isFilesDownloaded(): Boolean {
        return reTerminal.child("alpine.tar.gz").exists() &&
            reTerminal.child("proot").exists() &&
            reTerminal.child("libtalloc.so.2").exists()
    }

    fun markDownloaded(sha256: String) {
        reTerminal.child("alpine.sha256").writeText(sha256)
        isDownloaded.value = true
    }

    fun refreshState() {
        isDownloaded.value = isFilesDownloaded()
    }

    fun resetInstalledRootfs() {
        alpineDir().deleteRecursively()
    }
}
