package com.rk.terminal.ui.screens.terminal

import com.rk.terminal.ui.screens.terminal.virtualkeys.VirtualKeysView
import com.termux.view.TerminalView
import java.lang.ref.WeakReference

object TerminalUiRegistry {
    var terminalView = WeakReference<TerminalView?>(null)
    var virtualKeysView = WeakReference<VirtualKeysView?>(null)
}
