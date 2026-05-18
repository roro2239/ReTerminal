package com.termux.terminal

import android.view.KeyEvent.KEYCODE_BACK
import android.view.KeyEvent.KEYCODE_BREAK
import android.view.KeyEvent.KEYCODE_DEL
import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.KeyEvent.KEYCODE_DPAD_UP
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.KeyEvent.KEYCODE_ESCAPE
import android.view.KeyEvent.KEYCODE_F1
import android.view.KeyEvent.KEYCODE_F10
import android.view.KeyEvent.KEYCODE_F11
import android.view.KeyEvent.KEYCODE_F12
import android.view.KeyEvent.KEYCODE_F2
import android.view.KeyEvent.KEYCODE_F3
import android.view.KeyEvent.KEYCODE_F4
import android.view.KeyEvent.KEYCODE_F5
import android.view.KeyEvent.KEYCODE_F6
import android.view.KeyEvent.KEYCODE_F7
import android.view.KeyEvent.KEYCODE_F8
import android.view.KeyEvent.KEYCODE_F9
import android.view.KeyEvent.KEYCODE_FORWARD_DEL
import android.view.KeyEvent.KEYCODE_INSERT
import android.view.KeyEvent.KEYCODE_MOVE_END
import android.view.KeyEvent.KEYCODE_MOVE_HOME
import android.view.KeyEvent.KEYCODE_NUMPAD_0
import android.view.KeyEvent.KEYCODE_NUMPAD_1
import android.view.KeyEvent.KEYCODE_NUMPAD_2
import android.view.KeyEvent.KEYCODE_NUMPAD_3
import android.view.KeyEvent.KEYCODE_NUMPAD_4
import android.view.KeyEvent.KEYCODE_NUMPAD_5
import android.view.KeyEvent.KEYCODE_NUMPAD_6
import android.view.KeyEvent.KEYCODE_NUMPAD_7
import android.view.KeyEvent.KEYCODE_NUMPAD_8
import android.view.KeyEvent.KEYCODE_NUMPAD_9
import android.view.KeyEvent.KEYCODE_NUMPAD_ADD
import android.view.KeyEvent.KEYCODE_NUMPAD_COMMA
import android.view.KeyEvent.KEYCODE_NUMPAD_DIVIDE
import android.view.KeyEvent.KEYCODE_NUMPAD_DOT
import android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
import android.view.KeyEvent.KEYCODE_NUMPAD_EQUALS
import android.view.KeyEvent.KEYCODE_NUMPAD_MULTIPLY
import android.view.KeyEvent.KEYCODE_NUMPAD_SUBTRACT
import android.view.KeyEvent.KEYCODE_NUM_LOCK
import android.view.KeyEvent.KEYCODE_PAGE_DOWN
import android.view.KeyEvent.KEYCODE_PAGE_UP
import android.view.KeyEvent.KEYCODE_SPACE
import android.view.KeyEvent.KEYCODE_SYSRQ
import android.view.KeyEvent.KEYCODE_TAB

object KeyHandler {
    const val KEYMOD_ALT: Int = -0x80000000
    const val KEYMOD_CTRL: Int = 0x40000000
    const val KEYMOD_SHIFT: Int = 0x20000000
    const val KEYMOD_NUM_LOCK: Int = 0x10000000

    private val termcapToKeyCode =
        mapOf(
            "%i" to (KEYMOD_SHIFT or KEYCODE_DPAD_RIGHT),
            "#2" to (KEYMOD_SHIFT or KEYCODE_MOVE_HOME),
            "#4" to (KEYMOD_SHIFT or KEYCODE_DPAD_LEFT),
            "*7" to (KEYMOD_SHIFT or KEYCODE_MOVE_END),
            "k1" to KEYCODE_F1,
            "k2" to KEYCODE_F2,
            "k3" to KEYCODE_F3,
            "k4" to KEYCODE_F4,
            "k5" to KEYCODE_F5,
            "k6" to KEYCODE_F6,
            "k7" to KEYCODE_F7,
            "k8" to KEYCODE_F8,
            "k9" to KEYCODE_F9,
            "k;" to KEYCODE_F10,
            "F1" to KEYCODE_F11,
            "F2" to KEYCODE_F12,
            "F3" to (KEYMOD_SHIFT or KEYCODE_F1),
            "F4" to (KEYMOD_SHIFT or KEYCODE_F2),
            "F5" to (KEYMOD_SHIFT or KEYCODE_F3),
            "F6" to (KEYMOD_SHIFT or KEYCODE_F4),
            "F7" to (KEYMOD_SHIFT or KEYCODE_F5),
            "F8" to (KEYMOD_SHIFT or KEYCODE_F6),
            "F9" to (KEYMOD_SHIFT or KEYCODE_F7),
            "FA" to (KEYMOD_SHIFT or KEYCODE_F8),
            "FB" to (KEYMOD_SHIFT or KEYCODE_F9),
            "FC" to (KEYMOD_SHIFT or KEYCODE_F10),
            "FD" to (KEYMOD_SHIFT or KEYCODE_F11),
            "FE" to (KEYMOD_SHIFT or KEYCODE_F12),
            "kb" to KEYCODE_DEL,
            "kd" to KEYCODE_DPAD_DOWN,
            "kh" to KEYCODE_MOVE_HOME,
            "kl" to KEYCODE_DPAD_LEFT,
            "kr" to KEYCODE_DPAD_RIGHT,
            "K1" to KEYCODE_MOVE_HOME,
            "K3" to KEYCODE_PAGE_UP,
            "K4" to KEYCODE_MOVE_END,
            "K5" to KEYCODE_PAGE_DOWN,
            "ku" to KEYCODE_DPAD_UP,
            "kB" to (KEYMOD_SHIFT or KEYCODE_TAB),
            "kD" to KEYCODE_FORWARD_DEL,
            "kDN" to (KEYMOD_SHIFT or KEYCODE_DPAD_DOWN),
            "kF" to (KEYMOD_SHIFT or KEYCODE_DPAD_DOWN),
            "kI" to KEYCODE_INSERT,
            "kN" to KEYCODE_PAGE_UP,
            "kP" to KEYCODE_PAGE_DOWN,
            "kR" to (KEYMOD_SHIFT or KEYCODE_DPAD_UP),
            "kUP" to (KEYMOD_SHIFT or KEYCODE_DPAD_UP),
            "@7" to KEYCODE_MOVE_END,
            "@8" to KEYCODE_NUMPAD_ENTER,
        )

    @JvmStatic
    fun getCodeFromTermcap(
        termcap: String,
        cursorKeysApplication: Boolean,
        keypadApplication: Boolean,
    ): String? {
        val keyCodeAndMod = termcapToKeyCode[termcap] ?: return null
        var keyCode = keyCodeAndMod
        var keyMod = 0
        if ((keyCode and KEYMOD_SHIFT) != 0) {
            keyMod = keyMod or KEYMOD_SHIFT
            keyCode = keyCode and KEYMOD_SHIFT.inv()
        }
        if ((keyCode and KEYMOD_CTRL) != 0) {
            keyMod = keyMod or KEYMOD_CTRL
            keyCode = keyCode and KEYMOD_CTRL.inv()
        }
        if ((keyCode and KEYMOD_ALT) != 0) {
            keyMod = keyMod or KEYMOD_ALT
            keyCode = keyCode and KEYMOD_ALT.inv()
        }
        if ((keyCode and KEYMOD_NUM_LOCK) != 0) {
            keyMod = keyMod or KEYMOD_NUM_LOCK
            keyCode = keyCode and KEYMOD_NUM_LOCK.inv()
        }
        return getCode(keyCode, keyMod, cursorKeysApplication, keypadApplication)
    }

    @JvmStatic
    fun getCode(
        keyCode: Int,
        keyModeStart: Int,
        cursorApp: Boolean,
        keypadApplication: Boolean,
    ): String? {
        var keyMode = keyModeStart
        val numLockOn = (keyMode and KEYMOD_NUM_LOCK) != 0
        keyMode = keyMode and KEYMOD_NUM_LOCK.inv()
        return when (keyCode) {
            KEYCODE_DPAD_CENTER -> "\r"
            KEYCODE_DPAD_UP -> if (keyMode == 0) if (cursorApp) "\u001bOA" else "\u001b[A" else transformForModifiers("\u001b[1", keyMode, 'A')
            KEYCODE_DPAD_DOWN -> if (keyMode == 0) if (cursorApp) "\u001bOB" else "\u001b[B" else transformForModifiers("\u001b[1", keyMode, 'B')
            KEYCODE_DPAD_RIGHT -> if (keyMode == 0) if (cursorApp) "\u001bOC" else "\u001b[C" else transformForModifiers("\u001b[1", keyMode, 'C')
            KEYCODE_DPAD_LEFT -> if (keyMode == 0) if (cursorApp) "\u001bOD" else "\u001b[D" else transformForModifiers("\u001b[1", keyMode, 'D')
            KEYCODE_MOVE_HOME -> if (keyMode == 0) if (cursorApp) "\u001bOH" else "\u001b[H" else transformForModifiers("\u001b[1", keyMode, 'H')
            KEYCODE_MOVE_END -> if (keyMode == 0) if (cursorApp) "\u001bOF" else "\u001b[F" else transformForModifiers("\u001b[1", keyMode, 'F')
            KEYCODE_F1 -> if (keyMode == 0) "\u001bOP" else transformForModifiers("\u001b[1", keyMode, 'P')
            KEYCODE_F2 -> if (keyMode == 0) "\u001bOQ" else transformForModifiers("\u001b[1", keyMode, 'Q')
            KEYCODE_F3 -> if (keyMode == 0) "\u001bOR" else transformForModifiers("\u001b[1", keyMode, 'R')
            KEYCODE_F4 -> if (keyMode == 0) "\u001bOS" else transformForModifiers("\u001b[1", keyMode, 'S')
            KEYCODE_F5 -> transformForModifiers("\u001b[15", keyMode, '~')
            KEYCODE_F6 -> transformForModifiers("\u001b[17", keyMode, '~')
            KEYCODE_F7 -> transformForModifiers("\u001b[18", keyMode, '~')
            KEYCODE_F8 -> transformForModifiers("\u001b[19", keyMode, '~')
            KEYCODE_F9 -> transformForModifiers("\u001b[20", keyMode, '~')
            KEYCODE_F10 -> transformForModifiers("\u001b[21", keyMode, '~')
            KEYCODE_F11 -> transformForModifiers("\u001b[23", keyMode, '~')
            KEYCODE_F12 -> transformForModifiers("\u001b[24", keyMode, '~')
            KEYCODE_SYSRQ -> "\u001b[32~"
            KEYCODE_BREAK -> "\u001b[34~"
            KEYCODE_ESCAPE, KEYCODE_BACK -> "\u001b"
            KEYCODE_INSERT -> transformForModifiers("\u001b[2", keyMode, '~')
            KEYCODE_FORWARD_DEL -> transformForModifiers("\u001b[3", keyMode, '~')
            KEYCODE_PAGE_UP -> transformForModifiers("\u001b[5", keyMode, '~')
            KEYCODE_PAGE_DOWN -> transformForModifiers("\u001b[6", keyMode, '~')
            KEYCODE_DEL -> {
                val prefix = if ((keyMode and KEYMOD_ALT) == 0) "" else "\u001b"
                prefix + if ((keyMode and KEYMOD_CTRL) == 0) "\u007f" else "\u0008"
            }
            KEYCODE_NUM_LOCK -> if (keypadApplication) "\u001bOP" else null
            KEYCODE_SPACE -> if ((keyMode and KEYMOD_CTRL) == 0) null else "\u0000"
            KEYCODE_TAB -> if ((keyMode and KEYMOD_SHIFT) == 0) "\t" else "\u001b[Z"
            KEYCODE_ENTER -> if ((keyMode and KEYMOD_ALT) == 0) "\r" else "\u001b\r"
            KEYCODE_NUMPAD_ENTER -> if (keypadApplication) transformForModifiers("\u001bO", keyMode, 'M') else "\n"
            KEYCODE_NUMPAD_MULTIPLY -> if (keypadApplication) transformForModifiers("\u001bO", keyMode, 'j') else "*"
            KEYCODE_NUMPAD_ADD -> if (keypadApplication) transformForModifiers("\u001bO", keyMode, 'k') else "+"
            KEYCODE_NUMPAD_COMMA -> ","
            KEYCODE_NUMPAD_DOT -> if (numLockOn) {
                if (keypadApplication) "\u001bOn" else "."
            } else {
                transformForModifiers("\u001b[3", keyMode, '~')
            }
            KEYCODE_NUMPAD_SUBTRACT -> if (keypadApplication) transformForModifiers("\u001bO", keyMode, 'm') else "-"
            KEYCODE_NUMPAD_DIVIDE -> if (keypadApplication) transformForModifiers("\u001bO", keyMode, 'o') else "/"
            KEYCODE_NUMPAD_0 -> if (numLockOn) {
                if (keypadApplication) transformForModifiers("\u001bO", keyMode, 'p') else "0"
            } else {
                transformForModifiers("\u001b[2", keyMode, '~')
            }
            KEYCODE_NUMPAD_1 -> if (numLockOn) {
                if (keypadApplication) transformForModifiers("\u001bO", keyMode, 'q') else "1"
            } else {
                if (keyMode == 0) if (cursorApp) "\u001bOF" else "\u001b[F" else transformForModifiers("\u001b[1", keyMode, 'F')
            }
            KEYCODE_NUMPAD_2 -> if (numLockOn) {
                if (keypadApplication) transformForModifiers("\u001bO", keyMode, 'r') else "2"
            } else {
                if (keyMode == 0) if (cursorApp) "\u001bOB" else "\u001b[B" else transformForModifiers("\u001b[1", keyMode, 'B')
            }
            KEYCODE_NUMPAD_3 -> if (numLockOn) {
                if (keypadApplication) transformForModifiers("\u001bO", keyMode, 's') else "3"
            } else {
                "\u001b[6~"
            }
            KEYCODE_NUMPAD_4 -> if (numLockOn) {
                if (keypadApplication) transformForModifiers("\u001bO", keyMode, 't') else "4"
            } else {
                if (keyMode == 0) if (cursorApp) "\u001bOD" else "\u001b[D" else transformForModifiers("\u001b[1", keyMode, 'D')
            }
            KEYCODE_NUMPAD_5 -> if (keypadApplication) transformForModifiers("\u001bO", keyMode, 'u') else "5"
            KEYCODE_NUMPAD_6 -> if (numLockOn) {
                if (keypadApplication) transformForModifiers("\u001bO", keyMode, 'v') else "6"
            } else {
                if (keyMode == 0) if (cursorApp) "\u001bOC" else "\u001b[C" else transformForModifiers("\u001b[1", keyMode, 'C')
            }
            KEYCODE_NUMPAD_7 -> if (numLockOn) {
                if (keypadApplication) transformForModifiers("\u001bO", keyMode, 'w') else "7"
            } else {
                if (keyMode == 0) if (cursorApp) "\u001bOH" else "\u001b[H" else transformForModifiers("\u001b[1", keyMode, 'H')
            }
            KEYCODE_NUMPAD_8 -> if (numLockOn) {
                if (keypadApplication) transformForModifiers("\u001bO", keyMode, 'x') else "8"
            } else {
                if (keyMode == 0) if (cursorApp) "\u001bOA" else "\u001b[A" else transformForModifiers("\u001b[1", keyMode, 'A')
            }
            KEYCODE_NUMPAD_9 -> if (numLockOn) {
                if (keypadApplication) transformForModifiers("\u001bO", keyMode, 'y') else "9"
            } else {
                "\u001b[5~"
            }
            KEYCODE_NUMPAD_EQUALS -> if (keypadApplication) transformForModifiers("\u001bO", keyMode, 'X') else "="
            else -> null
        }
    }

    private fun transformForModifiers(start: String, keymod: Int, lastChar: Char): String {
        val modifier =
            when (keymod) {
                KEYMOD_SHIFT -> 2
                KEYMOD_ALT -> 3
                KEYMOD_SHIFT or KEYMOD_ALT -> 4
                KEYMOD_CTRL -> 5
                KEYMOD_SHIFT or KEYMOD_CTRL -> 6
                KEYMOD_ALT or KEYMOD_CTRL -> 7
                KEYMOD_SHIFT or KEYMOD_ALT or KEYMOD_CTRL -> 8
                else -> return start + lastChar
            }
        return "$start;$modifier$lastChar"
    }
}
