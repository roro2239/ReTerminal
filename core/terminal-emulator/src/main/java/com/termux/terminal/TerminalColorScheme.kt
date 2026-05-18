package com.termux.terminal

import java.util.Properties

class TerminalColorScheme {
    val mDefaultColors: IntArray = IntArray(TextStyle.NUM_INDEXED_COLORS)

    init {
        reset()
    }

    private fun reset() {
        defaultColorScheme.copyInto(mDefaultColors)
    }

    fun updateWith(props: Properties) {
        reset()
        var cursorPropExists = false
        for ((rawKey, rawValue) in props) {
            val key = rawKey as String
            val value = rawValue as String
            val colorIndex =
                when {
                    key == "foreground" -> TextStyle.COLOR_INDEX_FOREGROUND
                    key == "background" -> TextStyle.COLOR_INDEX_BACKGROUND
                    key == "cursor" -> {
                        cursorPropExists = true
                        TextStyle.COLOR_INDEX_CURSOR
                    }
                    key.startsWith("color") ->
                        key.substring(5).toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid property: '$key'")
                    else -> throw IllegalArgumentException("Invalid property: '$key'")
                }

            val colorValue = TerminalColors.parse(value)
            if (colorValue == 0) {
                throw IllegalArgumentException("Property '$key' has invalid color: '$value'")
            }
            mDefaultColors[colorIndex] = colorValue
        }

        if (!cursorPropExists) {
            setCursorColorForBackground()
        }
    }

    fun setCursorColorForBackground() {
        val backgroundColor = mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND]
        val brightness = TerminalColors.getPerceivedBrightnessOfColor(backgroundColor)
        if (brightness > 0) {
            mDefaultColors[TextStyle.COLOR_INDEX_CURSOR] =
                if (brightness < 130) 0xffffffff.toInt() else 0xff000000.toInt()
        }
    }

    private companion object {
        private val defaultColorScheme: IntArray =
            buildList {
                addAll(
                    listOf(
                        0xff000000.toInt(),
                        0xffcd0000.toInt(),
                        0xff00cd00.toInt(),
                        0xffcdcd00.toInt(),
                        0xff6495ed.toInt(),
                        0xffcd00cd.toInt(),
                        0xff00cdcd.toInt(),
                        0xffe5e5e5.toInt(),
                        0xff7f7f7f.toInt(),
                        0xffff0000.toInt(),
                        0xff00ff00.toInt(),
                        0xffffff00.toInt(),
                        0xff5c5cff.toInt(),
                        0xffff00ff.toInt(),
                        0xff00ffff.toInt(),
                        0xffffffff.toInt(),
                    ),
                )
                val cube = intArrayOf(0, 95, 135, 175, 215, 255)
                for (red in cube) {
                    for (green in cube) {
                        for (blue in cube) {
                            add(0xff000000.toInt() or (red shl 16) or (green shl 8) or blue)
                        }
                    }
                }
                repeat(24) { index ->
                    val component = 8 + index * 10
                    add(
                        0xff000000.toInt() or
                            (component shl 16) or
                            (component shl 8) or
                            component,
                    )
                }
                add(0xffffffff.toInt())
                add(0xff000000.toInt())
                add(0xffffffff.toInt())
            }.toIntArray()
    }
}
