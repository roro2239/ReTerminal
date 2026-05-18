package com.rk.terminal.ui.screens.terminal.virtualkeys

import android.view.KeyEvent
import java.util.HashMap

class VirtualKeysConstants private constructor() {
    class VirtualKeyDisplayMap : CleverMap<String, String>()

    open class CleverMap<K, V> : HashMap<K, V>() {
        @Suppress("UNCHECKED_CAST")
        fun get(key: K, defaultValue: V): V = if (containsKey(key)) get(key) as V else defaultValue
    }

    object EXTRA_KEY_DISPLAY_MAPS {
        @JvmField val CLASSIC_ARROWS_DISPLAY = VirtualKeyDisplayMap().apply {
            put("LEFT", "←"); put("RIGHT", "→"); put("UP", "↑"); put("DOWN", "↓")
        }
        @JvmField val WELL_KNOWN_CHARACTERS_DISPLAY = VirtualKeyDisplayMap().apply {
            put("ENTER", "↲"); put("TAB", "↹"); put("BKSP", "⌫"); put("DEL", "⌦")
            put("DRAWER", "☰"); put("KEYBOARD", "⌨"); put("PASTE", "⎘")
        }
        @JvmField val LESS_KNOWN_CHARACTERS_DISPLAY = VirtualKeyDisplayMap().apply {
            put("HOME", "⇱"); put("END", "⇲"); put("PGUP", "⇑"); put("PGDN", "⇓")
        }
        @JvmField val ARROW_TRIANGLE_VARIATION_DISPLAY = VirtualKeyDisplayMap().apply {
            put("LEFT", "◀"); put("RIGHT", "▶"); put("UP", "▲"); put("DOWN", "▼")
        }
        @JvmField val NOT_KNOWN_ISO_CHARACTERS = VirtualKeyDisplayMap().apply {
            put("CTRL", "⎈"); put("ALT", "⎇"); put("ESC", "⎋")
        }
        @JvmField val NICER_LOOKING_DISPLAY = VirtualKeyDisplayMap().apply { put("-", "―") }
        @JvmField val FULL_ISO_CHAR_DISPLAY = VirtualKeyDisplayMap().apply {
            putAll(CLASSIC_ARROWS_DISPLAY); putAll(WELL_KNOWN_CHARACTERS_DISPLAY)
            putAll(LESS_KNOWN_CHARACTERS_DISPLAY); putAll(NICER_LOOKING_DISPLAY); putAll(NOT_KNOWN_ISO_CHARACTERS)
        }
        @JvmField val ARROWS_ONLY_CHAR_DISPLAY = VirtualKeyDisplayMap().apply {
            putAll(CLASSIC_ARROWS_DISPLAY); putAll(NICER_LOOKING_DISPLAY)
        }
        @JvmField val LOTS_OF_ARROWS_CHAR_DISPLAY = VirtualKeyDisplayMap().apply {
            putAll(CLASSIC_ARROWS_DISPLAY); putAll(WELL_KNOWN_CHARACTERS_DISPLAY)
            putAll(LESS_KNOWN_CHARACTERS_DISPLAY); putAll(NICER_LOOKING_DISPLAY)
        }
        @JvmField val DEFAULT_CHAR_DISPLAY = VirtualKeyDisplayMap().apply {
            putAll(CLASSIC_ARROWS_DISPLAY); putAll(WELL_KNOWN_CHARACTERS_DISPLAY); putAll(NICER_LOOKING_DISPLAY)
        }
    }

    companion object {
        @JvmField val CONTROL_CHARS_ALIASES = VirtualKeyDisplayMap().apply {
            put("ESCAPE", "ESC"); put("CONTROL", "CTRL"); put("SHFT", "SHIFT"); put("RETURN", "ENTER")
            put("FUNCTION", "FN"); put("LT", "LEFT"); put("RT", "RIGHT"); put("DN", "DOWN")
            put("PAGEUP", "PGUP"); put("PAGE_UP", "PGUP"); put("PAGE UP", "PGUP"); put("PAGE-UP", "PGUP")
            put("PAGEDOWN", "PGDN"); put("PAGE_DOWN", "PGDN"); put("PAGE-DOWN", "PGDN")
            put("DELETE", "DEL"); put("BACKSPACE", "BKSP")
            put("BACKSLASH", "\\"); put("QUOTE", "\""); put("APOSTROPHE", "'")
        }

        @JvmField val PRIMARY_REPETITIVE_KEYS: List<String> = listOf("UP", "DOWN", "LEFT", "RIGHT", "BKSP", "DEL")

        @JvmField val PRIMARY_KEY_CODES_FOR_STRINGS: Map<String, Int> = mapOf(
            "SPACE" to KeyEvent.KEYCODE_SPACE, "ESC" to KeyEvent.KEYCODE_ESCAPE,
            "TAB" to KeyEvent.KEYCODE_TAB, "HOME" to KeyEvent.KEYCODE_MOVE_HOME,
            "END" to KeyEvent.KEYCODE_MOVE_END, "PGUP" to KeyEvent.KEYCODE_PAGE_UP,
            "PGDN" to KeyEvent.KEYCODE_PAGE_DOWN, "INS" to KeyEvent.KEYCODE_INSERT,
            "DEL" to KeyEvent.KEYCODE_FORWARD_DEL, "BKSP" to KeyEvent.KEYCODE_DEL,
            "UP" to KeyEvent.KEYCODE_DPAD_UP, "LEFT" to KeyEvent.KEYCODE_DPAD_LEFT,
            "RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT, "DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
            "ENTER" to KeyEvent.KEYCODE_ENTER, "F1" to KeyEvent.KEYCODE_F1,
            "F2" to KeyEvent.KEYCODE_F2, "F3" to KeyEvent.KEYCODE_F3,
            "F4" to KeyEvent.KEYCODE_F4, "F5" to KeyEvent.KEYCODE_F5,
            "F6" to KeyEvent.KEYCODE_F6, "F7" to KeyEvent.KEYCODE_F7,
            "F8" to KeyEvent.KEYCODE_F8, "F9" to KeyEvent.KEYCODE_F9,
            "F10" to KeyEvent.KEYCODE_F10, "F11" to KeyEvent.KEYCODE_F11,
            "F12" to KeyEvent.KEYCODE_F12,
        )
    }
}
