package com.termux.terminal

object TextStyle {
    const val CHARACTER_ATTRIBUTE_BOLD: Int = 1
    const val CHARACTER_ATTRIBUTE_ITALIC: Int = 1 shl 1
    const val CHARACTER_ATTRIBUTE_UNDERLINE: Int = 1 shl 2
    const val CHARACTER_ATTRIBUTE_BLINK: Int = 1 shl 3
    const val CHARACTER_ATTRIBUTE_INVERSE: Int = 1 shl 4
    const val CHARACTER_ATTRIBUTE_INVISIBLE: Int = 1 shl 5
    const val CHARACTER_ATTRIBUTE_STRIKETHROUGH: Int = 1 shl 6
    const val CHARACTER_ATTRIBUTE_PROTECTED: Int = 1 shl 7
    const val CHARACTER_ATTRIBUTE_DIM: Int = 1 shl 8
    private const val CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND: Int = 1 shl 9
    private const val CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND: Int = 1 shl 10

    const val COLOR_INDEX_FOREGROUND: Int = 256
    const val COLOR_INDEX_BACKGROUND: Int = 257
    const val COLOR_INDEX_CURSOR: Int = 258
    const val NUM_INDEXED_COLORS: Int = 259

    @JvmField
    val NORMAL: Long = encode(COLOR_INDEX_FOREGROUND, COLOR_INDEX_BACKGROUND, 0)

    @JvmStatic
    fun encode(foreColor: Int, backColor: Int, effect: Int): Long {
        var result = (effect and 0b111111111).toLong()
        result =
            if ((0xff000000.toInt() and foreColor) == 0xff000000.toInt()) {
                result or
                    CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND.toLong() or
                    ((foreColor and 0x00ffffff).toLong() shl 40)
            } else {
                result or ((foreColor and 0b111111111).toLong() shl 40)
            }
        result =
            if ((0xff000000.toInt() and backColor) == 0xff000000.toInt()) {
                result or
                    CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND.toLong() or
                    ((backColor and 0x00ffffff).toLong() shl 16)
            } else {
                result or ((backColor and 0b111111111).toLong() shl 16)
            }
        return result
    }

    @JvmStatic
    fun decodeForeColor(style: Long): Int =
        if ((style and CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND.toLong()) == 0L) {
            ((style ushr 40) and 0b111111111L).toInt()
        } else {
            0xff000000.toInt() or ((style ushr 40) and 0x00ffffffL).toInt()
        }

    @JvmStatic
    fun decodeBackColor(style: Long): Int =
        if ((style and CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND.toLong()) == 0L) {
            ((style ushr 16) and 0b111111111L).toInt()
        } else {
            0xff000000.toInt() or ((style ushr 16) and 0x00ffffffL).toInt()
        }

    @JvmStatic
    fun decodeEffect(style: Long): Int = (style and 0b11111111111L).toInt()
}
