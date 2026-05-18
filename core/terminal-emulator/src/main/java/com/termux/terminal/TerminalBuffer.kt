package com.termux.terminal

import java.util.Arrays
import kotlin.math.max

class TerminalBuffer(
    columns: Int,
    totalRows: Int,
    screenRows: Int,
) {
    @JvmField
    var mLines: Array<TerminalRow?> = arrayOfNulls(totalRows)
    @JvmField
    var mTotalRows: Int = totalRows
    @JvmField
    var mScreenRows: Int = screenRows
    @JvmField
    var mColumns: Int = columns
    private var mActiveTranscriptRows = 0
    private var mScreenFirstRow = 0

    init {
        blockSet(0, 0, columns, screenRows, ' '.code, TextStyle.NORMAL)
    }

    fun getTranscriptText(): String =
        getSelectedText(0, -getActiveTranscriptRows(), mColumns, mScreenRows).trim()

    fun getTranscriptTextWithoutJoinedLines(): String =
        getSelectedText(0, -getActiveTranscriptRows(), mColumns, mScreenRows, false).trim()

    fun getTranscriptTextWithFullLinesJoined(): String =
        getSelectedText(0, -getActiveTranscriptRows(), mColumns, mScreenRows, true, true).trim()

    fun getSelectedText(selX1: Int, selY1: Int, selX2: Int, selY2: Int): String =
        getSelectedText(selX1, selY1, selX2, selY2, true)

    fun getSelectedText(
        selX1: Int,
        selY1Start: Int,
        selX2: Int,
        selY2Start: Int,
        joinBackLines: Boolean,
    ): String = getSelectedText(selX1, selY1Start, selX2, selY2Start, joinBackLines, false)

    fun getSelectedText(
        selX1: Int,
        selY1Start: Int,
        selX2: Int,
        selY2Start: Int,
        joinBackLines: Boolean,
        joinFullLines: Boolean,
    ): String {
        val builder = StringBuilder()
        val columns = mColumns
        var selY1 = selY1Start
        var selY2 = selY2Start

        if (selY1 < -getActiveTranscriptRows()) selY1 = -getActiveTranscriptRows()
        if (selY2 >= mScreenRows) selY2 = mScreenRows - 1

        for (row in selY1..selY2) {
            val x1 = if (row == selY1) selX1 else 0
            var x2 = if (row == selY2) selX2 + 1 else columns
            if (x2 > columns) x2 = columns
            val lineObject = allocateFullLineIfNecessary(externalToInternalRow(row))
            val x1Index = lineObject.findStartOfColumn(x1)
            var x2Index = if (x2 < mColumns) lineObject.findStartOfColumn(x2) else lineObject.getSpaceUsed()
            if (x2Index == x1Index) {
                x2Index = lineObject.findStartOfColumn(x2 + 1)
            }
            val line = lineObject.mText
            var lastPrintingCharIndex = -1
            val rowLineWrap = getLineWrap(row)
            if (rowLineWrap && x2 == columns) {
                lastPrintingCharIndex = x2Index - 1
            } else {
                for (index in x1Index until x2Index) {
                    if (line[index] != ' ') lastPrintingCharIndex = index
                }
            }

            val len = lastPrintingCharIndex - x1Index + 1
            if (lastPrintingCharIndex != -1 && len > 0) {
                builder.append(line, x1Index, len)
            }

            val lineFillsWidth = lastPrintingCharIndex == x2Index - 1
            if ((!joinBackLines || !rowLineWrap) &&
                (!joinFullLines || !lineFillsWidth) &&
                row < selY2 &&
                row < mScreenRows - 1
            ) {
                builder.append('\n')
            }
        }
        return builder.toString()
    }

    fun getWordAtLocation(x: Int, y: Int): String {
        var y1 = y
        var y2 = y
        while (y1 > 0 && !getSelectedText(0, y1 - 1, mColumns, y, true, true).contains("\n")) {
            y1--
        }
        while (y2 < mScreenRows && !getSelectedText(0, y, mColumns, y2 + 1, true, true).contains("\n")) {
            y2++
        }

        val text = getSelectedText(0, y1, mColumns, y2, true, true)
        val textOffset = (y - y1) * mColumns + x
        if (textOffset >= text.length) return ""

        val x1 = text.lastIndexOf(' ', textOffset)
        var x2 = text.indexOf(' ', textOffset)
        if (x2 == -1) x2 = text.length
        if (x1 == x2) return ""
        return text.substring(x1 + 1, x2)
    }

    fun getActiveTranscriptRows(): Int = mActiveTranscriptRows

    fun getActiveRows(): Int = mActiveTranscriptRows + mScreenRows

    fun externalToInternalRow(externalRow: Int): Int {
        require(externalRow >= -mActiveTranscriptRows && externalRow <= mScreenRows) {
            "extRow=$externalRow, mScreenRows=$mScreenRows, mActiveTranscriptRows=$mActiveTranscriptRows"
        }
        val internalRow = mScreenFirstRow + externalRow
        return if (internalRow < 0) mTotalRows + internalRow else internalRow % mTotalRows
    }

    fun setLineWrap(row: Int) {
        allocateFullLineIfNecessary(externalToInternalRow(row)).mLineWrap = true
    }

    fun getLineWrap(row: Int): Boolean = allocateFullLineIfNecessary(externalToInternalRow(row)).mLineWrap

    fun clearLineWrap(row: Int) {
        allocateFullLineIfNecessary(externalToInternalRow(row)).mLineWrap = false
    }

    fun resize(
        newColumns: Int,
        newRows: Int,
        newTotalRows: Int,
        cursor: IntArray,
        currentStyle: Long,
        altScreen: Boolean,
    ) {
        if (newColumns == mColumns && newRows <= mTotalRows) {
            var shiftDownOfTopRow = mScreenRows - newRows
            if (shiftDownOfTopRow > 0 && shiftDownOfTopRow < mScreenRows) {
                for (index in mScreenRows - 1 downTo 1) {
                    if (cursor[1] >= index) break
                    val row = externalToInternalRow(index)
                    val line = mLines[row]
                    if (line == null || line.isBlank()) {
                        if (--shiftDownOfTopRow == 0) break
                    }
                }
            } else if (shiftDownOfTopRow < 0) {
                val actualShift = max(shiftDownOfTopRow, -mActiveTranscriptRows)
                if (shiftDownOfTopRow != actualShift) {
                    for (index in 0 until actualShift - shiftDownOfTopRow) {
                        allocateFullLineIfNecessary((mScreenFirstRow + mScreenRows + index) % mTotalRows).clear(currentStyle)
                    }
                    shiftDownOfTopRow = actualShift
                }
            }
            mScreenFirstRow += shiftDownOfTopRow
            mScreenFirstRow = if (mScreenFirstRow < 0) mScreenFirstRow + mTotalRows else mScreenFirstRow % mTotalRows
            mTotalRows = newTotalRows
            mActiveTranscriptRows = if (altScreen) 0 else max(0, mActiveTranscriptRows + shiftDownOfTopRow)
            cursor[1] -= shiftDownOfTopRow
            mScreenRows = newRows
        } else {
            val oldLines = mLines
            val oldActiveTranscriptRows = mActiveTranscriptRows
            val oldScreenFirstRow = mScreenFirstRow
            val oldScreenRows = mScreenRows
            val oldTotalRows = mTotalRows

            mLines = Array(newTotalRows) { TerminalRow(newColumns, currentStyle) }
            mTotalRows = newTotalRows
            mScreenRows = newRows
            mActiveTranscriptRows = 0
            mScreenFirstRow = 0
            mColumns = newColumns

            var newCursorRow = -1
            var newCursorColumn = -1
            val oldCursorRow = cursor[1]
            val oldCursorColumn = cursor[0]
            var newCursorPlaced = false
            var currentOutputExternalRow = 0
            var currentOutputExternalColumn = 0
            var skippedBlankLines = 0

            for (externalOldRow in -oldActiveTranscriptRows until oldScreenRows) {
                var internalOldRow = oldScreenFirstRow + externalOldRow
                internalOldRow = if (internalOldRow < 0) oldTotalRows + internalOldRow else internalOldRow % oldTotalRows
                val oldLine = oldLines[internalOldRow]
                val cursorAtThisRow = externalOldRow == oldCursorRow
                if (oldLine == null || (!( !newCursorPlaced && cursorAtThisRow) && oldLine.isBlank())) {
                    skippedBlankLines++
                    continue
                } else if (skippedBlankLines > 0) {
                    repeat(skippedBlankLines) {
                        if (currentOutputExternalRow == mScreenRows - 1) {
                            scrollDownOneLine(0, mScreenRows, currentStyle)
                        } else {
                            currentOutputExternalRow++
                        }
                        currentOutputExternalColumn = 0
                    }
                    skippedBlankLines = 0
                }

                var lastNonSpaceIndex = 0
                var justToCursor = false
                if (cursorAtThisRow || oldLine.mLineWrap) {
                    lastNonSpaceIndex = oldLine.getSpaceUsed()
                    if (cursorAtThisRow) justToCursor = true
                } else {
                    for (index in 0 until oldLine.getSpaceUsed()) {
                        if (oldLine.mText[index] != ' ') lastNonSpaceIndex = index + 1
                    }
                }

                var currentOldCol = 0
                var styleAtCol = 0L
                var index = 0
                while (index < lastNonSpaceIndex) {
                    val char = oldLine.mText[index]
                    val codePoint =
                        if (Character.isHighSurrogate(char)) {
                            Character.toCodePoint(char, oldLine.mText[++index])
                        } else {
                            char.code
                        }
                    val displayWidth = WcWidth.width(codePoint)
                    if (displayWidth > 0) styleAtCol = oldLine.getStyle(currentOldCol)

                    if (currentOutputExternalColumn + displayWidth > mColumns) {
                        setLineWrap(currentOutputExternalRow)
                        if (currentOutputExternalRow == mScreenRows - 1) {
                            if (newCursorPlaced) newCursorRow--
                            scrollDownOneLine(0, mScreenRows, currentStyle)
                        } else {
                            currentOutputExternalRow++
                        }
                        currentOutputExternalColumn = 0
                    }

                    val outputColumn =
                        currentOutputExternalColumn -
                            if (displayWidth <= 0 && currentOutputExternalColumn > 0) 1 else 0
                    setChar(outputColumn, currentOutputExternalRow, codePoint, styleAtCol)

                    if (displayWidth > 0) {
                        if (oldCursorRow == externalOldRow && oldCursorColumn == currentOldCol) {
                            newCursorColumn = currentOutputExternalColumn
                            newCursorRow = currentOutputExternalRow
                            newCursorPlaced = true
                        }
                        currentOldCol += displayWidth
                        currentOutputExternalColumn += displayWidth
                        if (justToCursor && newCursorPlaced) break
                    }
                    index++
                }

                if (externalOldRow != oldScreenRows - 1 && !oldLine.mLineWrap) {
                    if (currentOutputExternalRow == mScreenRows - 1) {
                        if (newCursorPlaced) newCursorRow--
                        scrollDownOneLine(0, mScreenRows, currentStyle)
                    } else {
                        currentOutputExternalRow++
                    }
                    currentOutputExternalColumn = 0
                }
            }
            cursor[0] = newCursorColumn
            cursor[1] = newCursorRow
        }

        if (cursor[0] < 0 || cursor[1] < 0) {
            cursor[0] = 0
            cursor[1] = 0
        }
    }

    private fun blockCopyLinesDown(srcInternal: Int, len: Int) {
        if (len == 0) return
        val totalRows = mTotalRows
        val start = len - 1
        val overwritten = mLines[(srcInternal + start + 1) % totalRows]
        for (index in start downTo 0) {
            mLines[(srcInternal + index + 1) % totalRows] = mLines[(srcInternal + index) % totalRows]
        }
        mLines[srcInternal % totalRows] = overwritten
    }

    fun scrollDownOneLine(topMargin: Int, bottomMargin: Int, style: Long) {
        require(topMargin <= bottomMargin - 1 && topMargin >= 0 && bottomMargin <= mScreenRows) {
            "topMargin=$topMargin, bottomMargin=$bottomMargin, mScreenRows=$mScreenRows"
        }
        blockCopyLinesDown(mScreenFirstRow, topMargin)
        blockCopyLinesDown(externalToInternalRow(bottomMargin), mScreenRows - bottomMargin)
        mScreenFirstRow = (mScreenFirstRow + 1) % mTotalRows
        if (mActiveTranscriptRows < mTotalRows - mScreenRows) mActiveTranscriptRows++

        val blankRow = externalToInternalRow(bottomMargin - 1)
        val line = mLines[blankRow]
        if (line == null) {
            mLines[blankRow] = TerminalRow(mColumns, style)
        } else {
            line.clear(style)
        }
    }

    fun blockCopy(sx: Int, sy: Int, w: Int, h: Int, dx: Int, dy: Int) {
        if (w == 0) return
        require(!(sx < 0 || sx + w > mColumns || sy < 0 || sy + h > mScreenRows || dx < 0 || dx + w > mColumns || dy < 0 || dy + h > mScreenRows))
        val copyingUp = sy > dy
        for (y in 0 until h) {
            val y2 = if (copyingUp) y else h - (y + 1)
            val sourceRow = allocateFullLineIfNecessary(externalToInternalRow(sy + y2))
            allocateFullLineIfNecessary(externalToInternalRow(dy + y2)).copyInterval(sourceRow, sx, sx + w, dx)
        }
    }

    fun blockSet(sx: Int, sy: Int, w: Int, h: Int, value: Int, style: Long) {
        require(!(sx < 0 || sx + w > mColumns || sy < 0 || sy + h > mScreenRows)) {
            "Illegal arguments! blockSet($sx, $sy, $w, $h, $value, $mColumns, $mScreenRows)"
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                setChar(sx + x, sy + y, value, style)
            }
        }
    }

    fun allocateFullLineIfNecessary(row: Int): TerminalRow {
        val existing = mLines[row]
        if (existing != null) return existing
        return TerminalRow(mColumns, 0).also { mLines[row] = it }
    }

    fun setChar(column: Int, rowStart: Int, codePoint: Int, style: Long) {
        var row = rowStart
        require(!(row < 0 || row >= mScreenRows || column < 0 || column >= mColumns)) {
            "TerminalBuffer.setChar(): row=$row, column=$column, mScreenRows=$mScreenRows, mColumns=$mColumns"
        }
        row = externalToInternalRow(row)
        allocateFullLineIfNecessary(row).setChar(column, codePoint, style)
    }

    fun getStyleAt(externalRow: Int, column: Int): Long =
        allocateFullLineIfNecessary(externalToInternalRow(externalRow)).getStyle(column)

    fun setOrClearEffect(
        bits: Int,
        setOrClear: Boolean,
        reverse: Boolean,
        rectangular: Boolean,
        leftMargin: Int,
        rightMargin: Int,
        top: Int,
        left: Int,
        bottom: Int,
        right: Int,
    ) {
        for (y in top until bottom) {
            val line = allocateFullLineIfNecessary(externalToInternalRow(y))
            val startOfLine = if (rectangular || y == top) left else leftMargin
            val endOfLine = if (rectangular || y + 1 == bottom) right else rightMargin
            for (x in startOfLine until endOfLine) {
                val currentStyle = line.getStyle(x)
                val foreColor = TextStyle.decodeForeColor(currentStyle)
                val backColor = TextStyle.decodeBackColor(currentStyle)
                var effect = TextStyle.decodeEffect(currentStyle)
                effect =
                    if (reverse) {
                        (effect and bits.inv()) or (bits and effect.inv())
                    } else if (setOrClear) {
                        effect or bits
                    } else {
                        effect and bits.inv()
                    }
                line.mStyle[x] = TextStyle.encode(foreColor, backColor, effect)
            }
        }
    }

    fun clearTranscript() {
        if (mScreenFirstRow < mActiveTranscriptRows) {
            Arrays.fill(mLines, mTotalRows + mScreenFirstRow - mActiveTranscriptRows, mTotalRows, null)
            Arrays.fill(mLines, 0, mScreenFirstRow, null)
        } else {
            Arrays.fill(mLines, mScreenFirstRow - mActiveTranscriptRows, mScreenFirstRow, null)
        }
        mActiveTranscriptRows = 0
    }
}
