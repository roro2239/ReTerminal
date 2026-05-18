package com.termux.terminal

import android.graphics.Color
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

class TerminalColors {
    @JvmField
    val mCurrentColors: IntArray = IntArray(TextStyle.NUM_INDEXED_COLORS)

    init {
        reset()
    }

    fun reset(index: Int) {
        mCurrentColors[index] = COLOR_SCHEME.mDefaultColors[index]
    }

    fun reset() {
        COLOR_SCHEME.mDefaultColors.copyInto(mCurrentColors)
    }

    fun tryParseColor(intoIndex: Int, textParameter: String) {
        val color = parse(textParameter)
        if (color != 0) {
            mCurrentColors[intoIndex] = color
        }
    }

    companion object {
        val COLOR_SCHEME: TerminalColorScheme = TerminalColorScheme()

        internal fun parse(c: String): Int {
            try {
                val (skipInitial, skipBetween) =
                    when {
                        c[0] == '#' -> 1 to 0
                        c.startsWith("rgb:") -> 4 to 1
                        else -> return 0
                    }
                val charsForColors = c.length - skipInitial - 2 * skipBetween
                if (charsForColors % 3 != 0) return 0
                val componentLength = charsForColors / 3
                val mult = 255 / (2.0.pow(componentLength * 4) - 1)

                var currentPosition = skipInitial
                val rString = c.substring(currentPosition, currentPosition + componentLength)
                currentPosition += componentLength + skipBetween
                val gString = c.substring(currentPosition, currentPosition + componentLength)
                currentPosition += componentLength + skipBetween
                val bString = c.substring(currentPosition, currentPosition + componentLength)

                val red = (rString.toInt(16) * mult).toInt()
                val green = (gString.toInt(16) * mult).toInt()
                val blue = (bString.toInt(16) * mult).toInt()
                return (0xff shl 24) or (red shl 16) or (green shl 8) or blue
            } catch (_: NumberFormatException) {
                return 0
            } catch (_: IndexOutOfBoundsException) {
                return 0
            }
        }

        fun getPerceivedBrightnessOfColor(color: Int): Int =
            floor(
                sqrt(
                    Color.red(color).toDouble().pow(2) * 0.241 +
                        Color.green(color).toDouble().pow(2) * 0.691 +
                        Color.blue(color).toDouble().pow(2) * 0.068,
                ),
            ).toInt()
    }
}
