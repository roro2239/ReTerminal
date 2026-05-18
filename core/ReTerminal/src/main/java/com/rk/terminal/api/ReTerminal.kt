package com.rk.terminal.api

import android.app.Application
import android.app.Activity
import android.content.Context
import android.content.Intent
import com.rk.libcommons.application
import com.rk.resources.Res
import com.rk.libcommons.alpineHomeDir
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.screens.terminal.Rootfs

data class ReTerminalConfig(
    val installRuntimeOnEntryOpen: Boolean = true,
    val enableForegroundService: Boolean = true,
)

object ReTerminal {
    private var config = ReTerminalConfig()

    val isInitialized: Boolean
        get() = application != null

    val isRuntimeReady: Boolean
        get() = isInitialized && Rootfs.isFilesDownloaded()

    fun init(application: Application, config: ReTerminalConfig = ReTerminalConfig()) {
        if (isInitialized) {
            this.config = config
            Rootfs.refreshState()
            return
        }

        com.rk.libcommons.application = application
        Res.application = application
        this.config = config
        Rootfs.refreshState()
    }

    fun requireApplication(): Application {
        return application ?: throw IllegalStateException("ReTerminal 未初始化")
    }

    fun requireRuntimeReady() {
        requireApplication()
        if (!Rootfs.isFilesDownloaded()) {
            throw IllegalStateException("ReTerminal 运行环境未初始化")
        }
    }

    fun createFullIntent(context: Context): Intent {
        initIfPossible(context)
        return Intent(context, MainActivity::class.java).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun openFull(context: Context) {
        context.startActivity(createFullIntent(context))
    }

    fun createSession(
        request: ReTerminalSessionRequest,
        listener: ReTerminalSessionListener = object : ReTerminalSessionListener {},
    ): ReTerminalSessionHandle {
        return ReTerminalSessionFactory.create(request, listener)
    }

    fun initIfPossible(context: Context) {
        if (!isInitialized) {
            val app = context.applicationContext as? Application
                ?: throw IllegalStateException("ReTerminal 需要 Application 上下文")
            init(app, config)
        }
    }

    internal fun defaultWorkingDir(): String {
        return alpineHomeDir().path
    }
}
