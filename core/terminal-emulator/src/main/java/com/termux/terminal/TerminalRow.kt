package com.termux.terminal

import java.util.Arrays

class TerminalRow(
    private val mColumns: Int,
    style: Long,
) {
    @JvmField
    var mText: CharArray = CharArray((SPARE_CAPACITY_FACTOR * mColumns).toInt())
    private var mSpaceUsed: Short = 0
    @JvmField
    var mLineWrap: Boolean = false
    @JvmField
    val mStyle: LongArray = LongArray(mColumns)
    @JvmField
    var mHasNonOneWidthOrSurrogateChars: Boolean = false

    init {
        clear(style)
    }

    fun copyInterval(line: TerminalRow, sourceX1Start: Int, sourceX2: Int, destinationXStart: Int) {
        var sourceX1 = sourceX1Start
        var destinationX = destinationXStart
        mHasNonOneWidthOrSurrogateChars =
            mHasNonOneWidthOrSurrogateChars or line.mHasNonOneWidthOrSurrogateChars
        val x1 = line.findStartOfColumn(sourceX1)
        val x2 = line.findStartOfColumn(sourceX2)
        var startingFromSecondHalfOfWideChar =
            sourceX1 > 0 && line.wideDisplayCharacterStartingAt(sourceX1 - 1)
        val sourceChars = if (this === line) line.mText.copyOf() else line.mText
        var latestNonCombiningWidth = 0
        var index = x1
        while (index < x2) {
            val sourceChar = sourceChars[index]
            var codePoint =
                if (Character.isHighSurrogate(sourceChar)) {
                    Character.toCodePoint(sourceChar, sourceChars[++index])
                } else {
                    sourceChar.code
                }
            if (startingFromSecondHalfOfWideChar) {
                codePoint = ' '.code
                startingFromSecondHalfOfWideChar = false
            }
            val width = WcWidth.width(codePoint)
            if (width > 0) {
                destinationX += latestNonCombiningWidth
                sourceX1 += latestNonCombiningWidth
                latestNonCombiningWidth = width
            }
            setChar(destinationX, codePoint, line.getStyle(sourceX1))
            index++
        }
    }

    fun getSpaceUsed(): Int = mSpaceUsed.toInt()

    fun findStartOfColumn(column: Int): Int {
        if (column == mColumns) return getSpaceUsed()

        var currentColumn = 0
        var currentCharIndex = 0
        while (true) {
            var newCharIndex = currentCharIndex
            val char = mText[newCharIndex++]
            val codePoint =
                if (Character.isHighSurrogate(char)) {
                    Character.toCodePoint(char, mText[newCharIndex++])
                } else {
                    char.code
                }
            val width = WcWidth.width(codePoint)
            if (width > 0) {
                currentColumn += width
                if (currentColumn == column) {
                    while (newCharIndex < mSpaceUsed) {
                        if (Character.isHighSurrogate(mText[newCharIndex])) {
                            if (WcWidth.width(
                                    Character.toCodePoint(
                                        mText[newCharIndex],
                                        mText[newCharIndex + 1],
                                    ),
                                ) <= 0
                            ) {
                                newCharIndex += 2
                            } else {
                                break
                            }
                        } else if (WcWidth.width(mText[newCharIndex].code) <= 0) {
                            newCharIndex++
                        } else {
                            break
                        }
                    }
                    return newCharIndex
                } else if (currentColumn > column) {
                    return currentCharIndex
                }
            }
            currentCharIndex = newCharIndex
        }
    }

    private fun wideDisplayCharacterStartingAt(column: Int): Boolean {
        var currentCharIndex = 0
        var currentColumn = 0
        while (currentCharIndex < mSpaceUsed) {
            val char = mText[currentCharIndex++]
            val codePoint =
                if (Character.isHighSurrogate(char)) {
                    Character.toCodePoint(char, mText[currentCharIndex++])
                } else {
                    char.code
                }
            val width = WcWidth.width(codePoint)
            if (width > 0) {
                if (currentColumn == column && width == 2) return true
                currentColumn += width
                if (currentColumn > column) return false
            }
        }
        return false
    }

    fun clear(style: Long) {
        Arrays.fill(mText, ' ')
        Arrays.fill(mStyle, style)
        mSpaceUsed = mColumns.toShort()
        mHasNonOneWidthOrSurrogateChars = false
    }

    fun setChar(columnToSetStart: Int, codePoint: Int, style: Long) {
        var columnToSet = columnToSetStart
        require(columnToSet >= 0 && columnToSet < mStyle.size) {
            "TerminalRow.setChar(): columnToSet=$columnToSet, codePoint=$codePoint, style=$style"
        }

        mStyle[columnToSet] = style
        val newCodePointDisplayWidth = WcWidth.width(codePoint)

        if (!mHasNonOneWidthOrSurrogateChars) {
            if (codePoint >= Character.MIN_SUPPLEMENTARY_CODE_POINT || newCodePointDisplayWidth != 1) {
                mHasNonOneWidthOrSurrogateChars = true
            } else {
                mText[columnToSet] = codePoint.toChar()
                return
            }
        }

        val newIsCombining = newCodePointDisplayWidth <= 0
        val wasExtraColForWideChar =
            columnToSet > 0 && wideDisplayCharacterStartingAt(columnToSet - 1)

        if (newIsCombining) {
            if (wasExtraColForWideChar) columnToSet--
        } else {
            if (wasExtraColForWideChar) setChar(columnToSet - 1, ' '.code, style)
            val overwritingWideCharInNextColumn =
                newCodePointDisplayWidth == 2 && wideDisplayCharacterStartingAt(columnToSet + 1)
            if (overwritingWideCharInNextColumn) setChar(columnToSet + 1, ' '.code, style)
        }

        var text = mText
        val oldStartOfColumnIndex = findStartOfColumn(columnToSet)
        val oldCodePointDisplayWidth = WcWidth.width(text, oldStartOfColumnIndex)
        val oldCharactersUsedForColumn =
            if (columnToSet + oldCodePointDisplayWidth < mColumns) {
                findStartOfColumn(columnToSet + oldCodePointDisplayWidth) - oldStartOfColumnIndex
            } else {
                mSpaceUsed - oldStartOfColumnIndex
            }

        if (newIsCombining) {
            val combiningCharsCount =
                WcWidth.zeroWidthCharsCount(
                    mText,
                    oldStartOfColumnIndex,
                    oldStartOfColumnIndex + oldCharactersUsedForColumn,
                )
            if (combiningCharsCount >= MAX_COMBINING_CHARACTERS_PER_COLUMN) return
        }

        var newCharactersUsedForColumn = Character.charCount(codePoint)
        if (newIsCombining) {
            newCharactersUsedForColumn += oldCharactersUsedForColumn
        }

        val oldNextColumnIndex = oldStartOfColumnIndex + oldCharactersUsedForColumn
        val newNextColumnIndex = oldStartOfColumnIndex + newCharactersUsedForColumn
        val javaCharDifference = newCharactersUsedForColumn - oldCharactersUsedForColumn

        if (javaCharDifference > 0) {
            val oldCharactersAfterColumn = mSpaceUsed - oldNextColumnIndex
            if (mSpaceUsed + javaCharDifference > text.size) {
                val newText = CharArray(text.size + mColumns)
                System.arraycopy(text, 0, newText, 0, oldNextColumnIndex)
                System.arraycopy(text, oldNextColumnIndex, newText, newNextColumnIndex, oldCharactersAfterColumn)
                mText = newText
                text = newText
            } else {
                System.arraycopy(text, oldNextColumnIndex, text, newNextColumnIndex, oldCharactersAfterColumn)
            }
        } else if (javaCharDifference < 0) {
            System.arraycopy(text, oldNextColumnIndex, text, newNextColumnIndex, mSpaceUsed - oldNextColumnIndex)
        }
        mSpaceUsed = (mSpaceUsed + javaCharDifference).toShort()

        Character.toChars(
            codePoint,
            text,
            oldStartOfColumnIndex + if (newIsCombining) oldCharactersUsedForColumn else 0,
        )

        if (oldCodePointDisplayWidth == 2 && newCodePointDisplayWidth == 1) {
            if (mSpaceUsed + 1 > text.size) {
                val newText = CharArray(text.size + mColumns)
                System.arraycopy(text, 0, newText, 0, newNextColumnIndex)
                System.arraycopy(text, newNextColumnIndex, newText, newNextColumnIndex + 1, mSpaceUsed - newNextColumnIndex)
                mText = newText
                text = newText
            } else {
                System.arraycopy(text, newNextColumnIndex, text, newNextColumnIndex + 1, mSpaceUsed - newNextColumnIndex)
            }
            text[newNextColumnIndex] = ' '
            mSpaceUsed++
        } else if (oldCodePointDisplayWidth == 1 && newCodePointDisplayWidth == 2) {
            if (columnToSet == mColumns - 1) {
                throw IllegalArgumentException("Cannot put wide character in last column")
            } else if (columnToSet == mColumns - 2) {
                mSpaceUsed = newNextColumnIndex.toShort()
            } else {
                val newNextNextColumnIndex =
                    newNextColumnIndex +
                        if (Character.isHighSurrogate(mText[newNextColumnIndex])) 2 else 1
                val nextLen = newNextNextColumnIndex - newNextColumnIndex
                System.arraycopy(text, newNextNextColumnIndex, text, newNextColumnIndex, mSpaceUsed - newNextNextColumnIndex)
                mSpaceUsed = (mSpaceUsed - nextLen).toShort()
            }
        }
    }

    fun isBlank(): Boolean {
        for (charIndex in 0 until getSpaceUsed()) {
            if (mText[charIndex] != ' ') return false
        }
        return true
    }

    fun getStyle(column: Int): Long = mStyle[column]

    companion object {
        private const val SPARE_CAPACITY_FACTOR = 1.5f
        private const val MAX_COMBINING_CHARACTERS_PER_COLUMN = 15
    }
}
