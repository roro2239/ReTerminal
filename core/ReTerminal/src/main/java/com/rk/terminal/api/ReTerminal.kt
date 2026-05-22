package com.rk.terminal.api

import android.app.Application
import android.app.Activity
import android.content.Context
import android.content.Intent
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.application
import com.rk.resources.Res
import com.rk.libcommons.alpineHomeDir
import com.rk.terminal.runtime.RuntimeEnvironment
import com.rk.terminal.ui.activities.terminal.MainActivity

data class ReTerminalConfig(
    val installRuntimeOnEntryOpen: Boolean = true,
    val enableForegroundService: Boolean = true,
)

object ReTerminal {
    private const val EXTRA_COMMAND_ID = "com.rk.terminal.extra.COMMAND_ID"
    private const val EXTRA_COMMAND_SHELL = "com.rk.terminal.extra.COMMAND_SHELL"
    private const val EXTRA_COMMAND_ARGS = "com.rk.terminal.extra.COMMAND_ARGS"
    private const val EXTRA_COMMAND_WORKING_DIR = "com.rk.terminal.extra.COMMAND_WORKING_DIR"
    private const val EXTRA_COMMAND_ENV = "com.rk.terminal.extra.COMMAND_ENV"

    private var config = ReTerminalConfig()

    val isInitialized: Boolean
        get() = application != null

    val isRuntimeReady: Boolean
        get() = isInitialized && RuntimeEnvironment.isFilesDownloaded()

    fun init(application: Application, config: ReTerminalConfig = ReTerminalConfig()) {
        if (isInitialized) {
            this.config = config
            RuntimeEnvironment.refreshState()
            return
        }

        com.rk.libcommons.application = application
        Res.application = application
        this.config = config
        RuntimeEnvironment.refreshState()
    }

    fun requireApplication(): Application {
        return application ?: throw IllegalStateException("ReTerminal 未初始化")
    }

    fun requireRuntimeReady() {
        requireApplication()
        if (!RuntimeEnvironment.isFilesDownloaded()) {
            throw IllegalStateException("ReTerminal 运行环境未初始化")
        }
    }

    fun createFullIntent(context: Context, command: TerminalCommand? = null): Intent {
        initIfPossible(context)
        return Intent(context, MainActivity::class.java).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            command?.let {
                putExtra(EXTRA_COMMAND_ID, it.id)
                putExtra(EXTRA_COMMAND_SHELL, it.shell)
                putExtra(EXTRA_COMMAND_ARGS, it.args)
                putExtra(EXTRA_COMMAND_WORKING_DIR, it.workingDir)
                putExtra(EXTRA_COMMAND_ENV, it.env)
            }
        }
    }

    fun openFull(context: Context) {
        context.startActivity(createFullIntent(context))
    }

    fun openFull(context: Context, command: TerminalCommand) {
        context.startActivity(createFullIntent(context, command))
    }

    internal fun readCommand(intent: Intent): TerminalCommand? {
        val id = intent.getStringExtra(EXTRA_COMMAND_ID) ?: return null
        val shell = intent.getStringExtra(EXTRA_COMMAND_SHELL) ?: return null
        val workingDir = intent.getStringExtra(EXTRA_COMMAND_WORKING_DIR) ?: defaultWorkingDir()
        return TerminalCommand(
            shell = shell,
            args = intent.getStringArrayExtra(EXTRA_COMMAND_ARGS) ?: emptyArray(),
            id = id,
            workingDir = workingDir,
            env = intent.getStringArrayExtra(EXTRA_COMMAND_ENV) ?: emptyArray(),
        )
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
