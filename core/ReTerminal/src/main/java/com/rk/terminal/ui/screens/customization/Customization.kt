package com.rk.terminal.ui.screens.customization

import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.resources.strings
import com.rk.libcommons.dpToPx
import com.rk.settings.Settings
import com.rk.terminal.ui.components.SettingsToggle
import com.rk.terminal.ui.screens.terminal.showVirtualKeys
import com.rk.terminal.ui.screens.terminal.TerminalUiRegistry
import com.rk.terminal.ui.screens.terminal.ShortcutAction
import com.rk.terminal.ui.screens.terminal.ShortcutCaptureDialog


private const val min_text_size = 10f
private const val max_text_size = 20f

@Composable
fun Customization(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    PreferenceLayout(label = stringResource(strings.customizations)) {
        var sliderPosition by remember { mutableFloatStateOf(Settings.terminal_font_size.toFloat()) }
        PreferenceGroup {
            PreferenceTemplate(title = { Text(stringResource(strings.text_size)) }) {
                Text(sliderPosition.toInt().toString())
            }
            PreferenceTemplate(title = {}) {
                Slider(
                    modifier = modifier,
                    value = sliderPosition,
                    onValueChange = {
                        sliderPosition = it
                        Settings.terminal_font_size = it.toInt()
                        TerminalUiRegistry.terminalView.get()?.setTextSize(dpToPx(it.toFloat(), context))
                    },
                    steps = (max_text_size - min_text_size).toInt() - 1,
                    valueRange = min_text_size..max_text_size,
                )
            }
        }

        PreferenceGroup {
            SettingsToggle(label = stringResource(strings.vibrate), description = stringResource(strings.vibrate_desc), showSwitch = true, default = Settings.vibrate, sideEffect = {
                Settings.vibrate = it
            })
        }

        PreferenceGroup {
            SettingsToggle(
                label = stringResource(strings.virtual_keys),
                description = stringResource(strings.virtual_keys_desc),
                showSwitch = true,
                default = Settings.virtualKeys, sideEffect = {
                    Settings.virtualKeys = it
                    showVirtualKeys.value = it
                })

            SettingsToggle(
                label = stringResource(strings.hide_soft_keyboard),
                description = stringResource(strings.hide_soft_keyboard_desc),
                showSwitch = true,
                default = Settings.hide_soft_keyboard_if_hwd, sideEffect = {
                    Settings.hide_soft_keyboard_if_hwd = it
                })

        }

        // Keyboard Shortcuts
        PreferenceGroup(heading = stringResource(strings.keyboard_shortcuts)) {
            var shortcutsEnabled by remember { mutableStateOf(Settings.shortcuts_enabled) }
            var showCaptureFor by remember { mutableStateOf<ShortcutAction?>(null) }

            SettingsToggle(
                label = stringResource(strings.keyboard_shortcuts),
                description = stringResource(strings.keyboard_shortcuts_desc),
                showSwitch = true,
                default = Settings.shortcuts_enabled,
                sideEffect = {
                    Settings.shortcuts_enabled = it
                    shortcutsEnabled = it
                })

            for (action in ShortcutAction.entries) {
                val binding = Settings.getShortcutBinding(action)
                val labelRes = when (action) {
                    ShortcutAction.PASTE -> strings.shortcut_paste
                    ShortcutAction.NEW_SESSION -> strings.shortcut_new_session
                    ShortcutAction.CLOSE_SESSION -> strings.shortcut_close_session
                    ShortcutAction.SWITCH_SESSION_PREV -> strings.shortcut_switch_prev
                    ShortcutAction.SWITCH_SESSION_NEXT -> strings.shortcut_switch_next
                }
                val descRes = when (action) {
                    ShortcutAction.PASTE -> strings.shortcut_paste_desc
                    ShortcutAction.NEW_SESSION -> strings.shortcut_new_session_desc
                    ShortcutAction.CLOSE_SESSION -> strings.shortcut_close_session_desc
                    ShortcutAction.SWITCH_SESSION_PREV -> strings.shortcut_switch_prev_desc
                    ShortcutAction.SWITCH_SESSION_NEXT -> strings.shortcut_switch_next_desc
                }
                SettingsToggle(
                    isEnabled = shortcutsEnabled,
                    label = stringResource(labelRes),
                    description = "${stringResource(descRes)} (${binding.toDisplayString()})",
                    showSwitch = false,
                    default = false,
                    sideEffect = { showCaptureFor = action },
                )
            }

            if (showCaptureFor != null) {
                ShortcutCaptureDialog(
                    action = showCaptureFor!!,
                    onDismiss = { showCaptureFor = null },
                    onConfirm = { binding ->
                        Settings.setShortcutBinding(showCaptureFor!!, binding)
                        showCaptureFor = null
                    },
                )
            }
        }


    }


}
