package com.termux.view.textselection

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.text.TextUtils
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.WcWidth
import com.termux.view.R
import com.termux.view.TerminalView
import kotlin.math.max

class TextSelectionCursorController(private val terminalView: TerminalView) : CursorController {
    private val mStartHandle = TextSelectionHandleView(terminalView, this, TextSelectionHandleView.LEFT)
    private val mEndHandle = TextSelectionHandleView(terminalView, this, TextSelectionHandleView.RIGHT)
    private var mStoredSelectedText: String? = null
    private var mIsSelectingText = false
    private var mShowStartTime = System.currentTimeMillis()
    private val mHandleHeight = max(mStartHandle.getHandleHeight(), mEndHandle.getHandleHeight())
    private var mSelX1 = -1
    private var mSelX2 = -1
    private var mSelY1 = -1
    private var mSelY2 = -1
    private var mActionMode: ActionMode? = null

    @JvmField
    val ACTION_COPY = 1
    @JvmField
    val ACTION_PASTE = 2
    @JvmField
    val ACTION_MORE = 3

    override fun show(event: MotionEvent) {
        setInitialTextSelectionPosition(event)
        mStartHandle.positionAtCursor(mSelX1, mSelY1, true)
        mEndHandle.positionAtCursor(mSelX2 + 1, mSelY2, true)
        setActionModeCallBacks()
        mShowStartTime = System.currentTimeMillis()
        mIsSelectingText = true
    }

    override fun hide(): Boolean {
        if (!isActive()) return false
        if (System.currentTimeMillis() - mShowStartTime < 300) return false

        mStartHandle.hide()
        mEndHandle.hide()
        mActionMode?.finish()
        mSelY2 = -1
        mSelX2 = mSelY2
        mSelY1 = mSelX2
        mSelX1 = mSelY1
        mIsSelectingText = false
        return true
    }

    override fun render() {
        if (!isActive()) return
        mStartHandle.positionAtCursor(mSelX1, mSelY1, false)
        mEndHandle.positionAtCursor(mSelX2 + 1, mSelY2, false)
        mActionMode?.invalidate()
    }

    fun setInitialTextSelectionPosition(event: MotionEvent) {
        val columnAndRow = terminalView.getColumnAndRow(event, true)
        mSelX2 = columnAndRow[0]
        mSelX1 = mSelX2
        mSelY2 = columnAndRow[1]
        mSelY1 = mSelY2

        val screen = terminalView.emulator.screen
        if (" " != screen.getSelectedText(mSelX1, mSelY1, mSelX1, mSelY1)) {
            while (mSelX1 > 0 && "" != screen.getSelectedText(mSelX1 - 1, mSelY1, mSelX1 - 1, mSelY1)) {
                mSelX1--
            }
            while (mSelX2 < terminalView.emulator.mColumns - 1 && "" != screen.getSelectedText(mSelX2 + 1, mSelY1, mSelX2 + 1, mSelY1)) {
                mSelX2++
            }
        }
    }

    fun setActionModeCallBacks() {
        val callback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val show = MenuItem.SHOW_AS_ACTION_IF_ROOM or MenuItem.SHOW_AS_ACTION_WITH_TEXT
                val clipboard = terminalView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                menu.add(Menu.NONE, ACTION_COPY, Menu.NONE, R.string.copy_text).setShowAsAction(show)
                menu.add(Menu.NONE, ACTION_PASTE, Menu.NONE, R.string.paste_text)
                    .setEnabled(clipboard != null && clipboard.hasPrimaryClip())
                    .setShowAsAction(show)
                menu.add(Menu.NONE, ACTION_MORE, Menu.NONE, R.string.text_selection_more)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (!isActive()) return true
                when (item.itemId) {
                    ACTION_COPY -> {
                        val selectedText = getSelectedText()
                        terminalView.termSession.onCopyTextToClipboard(selectedText)
                        terminalView.stopTextSelectionMode()
                    }
                    ACTION_PASTE -> {
                        terminalView.stopTextSelectionMode()
                        terminalView.termSession.onPasteTextFromClipboard()
                    }
                    ACTION_MORE -> {
                        mStoredSelectedText = getSelectedText()
                        terminalView.stopTextSelectionMode()
                        terminalView.showContextMenu()
                    }
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            mActionMode = terminalView.startActionMode(callback)
            return
        }

        mActionMode = terminalView.startActionMode(
            object : ActionMode.Callback2() {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    return callback.onCreateActionMode(mode, menu)
                }

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    return callback.onActionItemClicked(mode, item)
                }

                override fun onDestroyActionMode(mode: ActionMode) {
                }

                override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                    var x1 = Math.round(mSelX1 * terminalView.renderer.getFontWidth())
                    var x2 = Math.round(mSelX2 * terminalView.renderer.getFontWidth())
                    val y1 = Math.round((mSelY1 - 1 - terminalView.topRow) * terminalView.renderer.getFontLineSpacing().toFloat())
                    val y2 = Math.round((mSelY2 + 1 - terminalView.topRow) * terminalView.renderer.getFontLineSpacing().toFloat())
                    if (x1 > x2) {
                        val tmp = x1
                        x1 = x2
                        x2 = tmp
                    }
                    val terminalBottom = terminalView.bottom
                    var top = y1 + mHandleHeight
                    var bottom = y2 + mHandleHeight
                    if (top > terminalBottom) top = terminalBottom
                    if (bottom > terminalBottom) bottom = terminalBottom
                    outRect.set(x1, top, x2, bottom)
                }
            },
            ActionMode.TYPE_FLOATING,
        )
    }

    override fun updatePosition(handle: TextSelectionHandleView, x: Int, y: Int) {
        val screen = terminalView.emulator.screen
        val scrollRows = screen.getActiveRows() - terminalView.emulator.mRows
        if (handle == mStartHandle) {
            mSelX1 = terminalView.getCursorX(x.toFloat())
            mSelY1 = terminalView.getCursorY(y.toFloat())
            if (mSelX1 < 0) mSelX1 = 0
            if (mSelY1 < -scrollRows) {
                mSelY1 = -scrollRows
            } else if (mSelY1 > terminalView.emulator.mRows - 1) {
                mSelY1 = terminalView.emulator.mRows - 1
            }
            if (mSelY1 > mSelY2) mSelY1 = mSelY2
            if (mSelY1 == mSelY2 && mSelX1 > mSelX2) mSelX1 = mSelX2

            if (!terminalView.emulator.isAlternateBufferActive) {
                var topRow = terminalView.topRow
                if (mSelY1 <= topRow) {
                    topRow--
                    if (topRow < -scrollRows) topRow = -scrollRows
                } else if (mSelY1 >= topRow + terminalView.emulator.mRows) {
                    topRow++
                    if (topRow > 0) topRow = 0
                }
                terminalView.topRow = topRow
            }
            mSelX1 = getValidCurX(screen, mSelY1, mSelX1)
        } else {
            mSelX2 = terminalView.getCursorX(x.toFloat())
            mSelY2 = terminalView.getCursorY(y.toFloat())
            if (mSelX2 < 0) mSelX2 = 0
            if (mSelY2 < -scrollRows) {
                mSelY2 = -scrollRows
            } else if (mSelY2 > terminalView.emulator.mRows - 1) {
                mSelY2 = terminalView.emulator.mRows - 1
            }
            if (mSelY1 > mSelY2) mSelY2 = mSelY1
            if (mSelY1 == mSelY2 && mSelX1 > mSelX2) mSelX2 = mSelX1

            if (!terminalView.emulator.isAlternateBufferActive) {
                var topRow = terminalView.topRow
                if (mSelY2 <= topRow) {
                    topRow--
                    if (topRow < -scrollRows) topRow = -scrollRows
                } else if (mSelY2 >= topRow + terminalView.emulator.mRows) {
                    topRow++
                    if (topRow > 0) topRow = 0
                }
                terminalView.topRow = topRow
            }
            mSelX2 = getValidCurX(screen, mSelY2, mSelX2)
        }
        terminalView.invalidate()
    }

    private fun getValidCurX(screen: TerminalBuffer, cy: Int, cx: Int): Int {
        val line = screen.getSelectedText(0, cy, cx, cy)
        if (!TextUtils.isEmpty(line)) {
            var col = 0
            var i = 0
            val len = line.length
            while (i < len) {
                val ch1 = line[i]
                if (ch1.code == 0) break
                val wc = if (Character.isHighSurrogate(ch1) && i + 1 < len) {
                    val ch2 = line[++i]
                    WcWidth.width(Character.toCodePoint(ch1, ch2))
                } else {
                    WcWidth.width(ch1.code)
                }
                val cend = col + wc
                if (cx > col && cx < cend) return cend
                if (cend == col) return col
                col = cend
                i++
            }
        }
        return cx
    }

    fun decrementYTextSelectionCursors(decrement: Int) {
        mSelY1 -= decrement
        mSelY2 -= decrement
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun onTouchModeChanged(isInTouchMode: Boolean) {
        if (!isInTouchMode) {
            terminalView.stopTextSelectionMode()
        }
    }

    override fun onDetached() {
    }

    override fun isActive(): Boolean = mIsSelectingText

    fun getSelectors(sel: IntArray?) {
        if (sel == null || sel.size != 4) return
        sel[0] = mSelY1
        sel[1] = mSelY2
        sel[2] = mSelX1
        sel[3] = mSelX2
    }

    fun getSelectedText(): String = terminalView.emulator.getSelectedText(mSelX1, mSelY1, mSelX2, mSelY2)

    fun getStoredSelectedText(): String? = mStoredSelectedText

    fun unsetStoredSelectedText() {
        mStoredSelectedText = null
    }

    fun getActionMode(): ActionMode? = mActionMode

    fun isSelectionStartDragged(): Boolean = mStartHandle.isDragging()

    fun isSelectionEndDragged(): Boolean = mEndHandle.isDragging()
}
