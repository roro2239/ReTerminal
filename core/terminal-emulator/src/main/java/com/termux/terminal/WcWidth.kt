package com.termux.terminal

object WcWidth {
    @JvmStatic
    fun width(chars: CharArray, start: Int): Int {
        val first = chars[start]
        val codePoint =
            if (Character.isHighSurrogate(first) && start + 1 < chars.size) {
                Character.toCodePoint(first, chars[start + 1])
            } else {
                first.code
            }
        return width(codePoint)
    }

    @JvmStatic
    fun width(codePoint: Int): Int =
        when {
            codePoint == 0 -> 0
            codePoint < 32 || codePoint in 0x7f..0x9f -> 0
            Character.getType(codePoint) in zeroWidthTypes -> 0
            isWide(codePoint) -> 2
            else -> 1
        }

    @JvmStatic
    fun zeroWidthCharsCount(chars: CharArray, start: Int, end: Int): Int {
        var count = 0
        var index = start
        while (index < end) {
            val char = chars[index]
            val codePoint =
                if (Character.isHighSurrogate(char) && index + 1 < end) {
                    index++
                    Character.toCodePoint(char, chars[index])
                } else {
                    char.code
                }
            if (width(codePoint) == 0) count++
            index++
        }
        return count
    }

    private fun isWide(codePoint: Int): Boolean =
        codePoint in 0x1100..0x115f ||
            codePoint in 0x2329..0x232a ||
            codePoint in 0x2e80..0xa4cf ||
            codePoint in 0xac00..0xd7a3 ||
            codePoint in 0xf900..0xfaff ||
            codePoint in 0xfe10..0xfe19 ||
            codePoint in 0xfe30..0xfe6f ||
            codePoint in 0xff00..0xff60 ||
            codePoint in 0xffe0..0xffe6 ||
            codePoint in 0x1f300..0x1f64f ||
            codePoint in 0x1f900..0x1f9ff ||
            codePoint in 0x20000..0x3fffd

    private val zeroWidthTypes =
        setOf(
            Character.NON_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt(),
            Character.FORMAT.toInt(),
        )
}
