package com.termux.view

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.text.TextUtils
import android.util.AttributeSet
import android.view.ActionMode
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Scroller
import androidx.annotation.RequiresApi
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.view.textselection.TextSelectionCursorController
import kotlin.math.max
import kotlin.math.min

/** View displaying and interacting with a [TerminalSession]. */
@Suppress("SENSELESS_COMPARISON")
class TerminalView(context: Context, attributes: AttributeSet?) : View(context, attributes) {

    @JvmField
    var mTermSession: TerminalSession? = null

    @JvmField
    var mEmulator: TerminalEmulator? = null

    @JvmField
    var mRenderer: TerminalRenderer? = null

    lateinit var mClient: TerminalViewClient

    private var mTextSelectionCursorController: TextSelectionCursorController? = null
    private var mTerminalCursorBlinkerHandler: Handler? = null
    private var mTerminalCursorBlinkerRunnable: TerminalCursorBlinkerRunnable? = null
    private var mTerminalCursorBlinkerRate = 0

    var topRow: Int
        get() = mTopRow
        set(value) {
            mTopRow = value
        }

    var mTopRow = 0
    var mDefaultSelectors = intArrayOf(-1, -1, -1, -1)
    var mScaleFactor = 1f
    internal val mGestureRecognizer: GestureAndScaleRecognizer
    private var mMouseScrollStartX = -1
    private var mMouseScrollStartY = -1
    private var mMouseStartDownTime = -1L
    val mScroller: Scroller
    var mScrollRemainder = 0f
    var mCombiningAccent = 0

    @RequiresApi(api = Build.VERSION_CODES.O)
    private var mAutoFillType = AUTOFILL_TYPE_NONE

    @RequiresApi(api = Build.VERSION_CODES.O)
    private var mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_NO

    private var mAutoFillHints = emptyArray<String>()
    private val mAccessibilityEnabled: Boolean

    private val mShowFloatingToolbar = object : Runnable {
        @RequiresApi(api = Build.VERSION_CODES.M)
        override fun run() {
            getTextSelectionActionMode()?.hide(0)
        }
    }

    init {
        mGestureRecognizer = GestureAndScaleRecognizer(
            context,
            object : GestureAndScaleRecognizer.Listener {
                private var scrolledWithFinger = false

                override fun onUp(e: MotionEvent): Boolean {
                    mScrollRemainder = 0.0f
                    val emulator = emulatorOrNull()
                    if (emulator != null && emulator.isMouseTrackingActive && !e.isFromSource(InputDevice.SOURCE_MOUSE) && !isSelectingText() && !scrolledWithFinger) {
                        sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON, true)
                        sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON, false)
                        return true
                    }
                    scrolledWithFinger = false
                    return false
                }

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    if (emulatorOrNull() == null) return true

                    if (isSelectingText()) {
                        stopTextSelectionMode()
                        return true
                    }
                    requestFocus()
                    mClient.onSingleTapUp(e)
                    return true
                }

                override fun onScroll(e2: MotionEvent, dx: Float, dy: Float): Boolean {
                    val emulator = emulatorOrNull() ?: return true
                    if (emulator.isMouseTrackingActive && e2.isFromSource(InputDevice.SOURCE_MOUSE)) {
                        sendMouseEventCode(e2, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true)
                    } else {
                        scrolledWithFinger = true
                        val distanceY = dy + mScrollRemainder
                        val deltaRows = (distanceY / renderer.mFontLineSpacing).toInt()
                        mScrollRemainder = distanceY - deltaRows * renderer.mFontLineSpacing
                        doScroll(e2, deltaRows)
                    }
                    return true
                }

                override fun onScale(focusX: Float, focusY: Float, scale: Float): Boolean {
                    if (emulatorOrNull() == null || isSelectingText()) return true
                    mScaleFactor *= scale
                    mScaleFactor = mClient.onScale(mScaleFactor)
                    return true
                }

                override fun onFling(e: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    val emulator = emulatorOrNull() ?: return true
                    if (!mScroller.isFinished) return true

                    val mouseTrackingAtStartOfFling = emulator.isMouseTrackingActive
                    val scale = 0.25f
                    if (mouseTrackingAtStartOfFling) {
                        mScroller.fling(0, 0, 0, -(velocityY * scale).toInt(), 0, 0, -emulator.mRows / 2, emulator.mRows / 2)
                    } else {
                        mScroller.fling(0, mTopRow, 0, -(velocityY * scale).toInt(), 0, 0, -emulator.screen.getActiveTranscriptRows(), 0)
                    }

                    post(
                        object : Runnable {
                            private var mLastY = 0

                            override fun run() {
                                val currentEmulator = emulatorOrNull() ?: return
                                if (mouseTrackingAtStartOfFling != currentEmulator.isMouseTrackingActive) {
                                    mScroller.abortAnimation()
                                    return
                                }
                                if (mScroller.isFinished) return
                                val more = mScroller.computeScrollOffset()
                                val newY = mScroller.currY
                                val diff = if (mouseTrackingAtStartOfFling) newY - mLastY else newY - mTopRow
                                doScroll(e, diff)
                                mLastY = newY
                                if (more) post(this)
                            }
                        },
                    )

                    return true
                }

                override fun onDown(x: Float, y: Float): Boolean = false

                override fun onDoubleTap(e: MotionEvent): Boolean = false

                override fun onLongPress(e: MotionEvent) {
                    if (mGestureRecognizer.isInProgress()) return
                    if (mClient.onLongPress(e)) return
                    if (!isSelectingText()) {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        startTextSelectionMode(e)
                    }
                }
            },
        )
        mScroller = Scroller(context)
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        mAccessibilityEnabled = am.isEnabled
    }

    val emulator: TerminalEmulator
        get() = requireNotNull(mEmulator)

    val termSession: TerminalSession
        get() = requireNotNull(mTermSession)

    val renderer: TerminalRenderer
        get() = requireNotNull(mRenderer)

    private fun emulatorOrNull(): TerminalEmulator? = mEmulator

    private fun sessionOrNull(): TerminalSession? = mTermSession

    private fun rendererOrNull(): TerminalRenderer? = mRenderer

    fun setTerminalViewClient(client: TerminalViewClient) {
        mClient = client
    }

    fun attachSession(session: TerminalSession): Boolean {
        if (session === sessionOrNull()) return false
        mTopRow = 0
        mTermSession = session
        mEmulator = null
        mCombiningAccent = 0
        updateSize()
        isVerticalScrollBarEnabled = true
        return true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        if (mClient.isTerminalViewSelected()) {
            when (mClient.getInputMode()) {
                1 -> outAttrs.inputType = InputType.TYPE_NULL
                else -> outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
        } else {
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        }

        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN

        return object : BaseInputConnection(this, true) {
            override fun finishComposingText(): Boolean {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) mClient.logInfo(LOG_TAG, "IME: finishComposingText()")
                super.finishComposingText()
                val content = requireNotNull(editable)
                sendTextToTerminal(content)
                content.clear()
                return true
            }

            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
                    mClient.logInfo(LOG_TAG, "IME: commitText(\"$text\", $newCursorPosition)")
                }
                super.commitText(text, newCursorPosition)

                if (emulatorOrNull() == null) return true

                val content = requireNotNull(editable)
                sendTextToTerminal(content)
                content.clear()
                return true
            }

            override fun deleteSurroundingText(leftLength: Int, rightLength: Int): Boolean {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
                    mClient.logInfo(LOG_TAG, "IME: deleteSurroundingText($leftLength, $rightLength)")
                }
                val deleteKey = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                for (i in 0 until leftLength) sendKeyEvent(deleteKey)
                return super.deleteSurroundingText(leftLength, rightLength)
            }

            private fun sendTextToTerminal(text: CharSequence) {
                stopTextSelectionMode()
                val textLengthInChars = text.length
                var i = 0
                while (i < textLengthInChars) {
                    val firstChar = text[i]
                    var codePoint: Int
                    if (Character.isHighSurrogate(firstChar)) {
                        codePoint = if (++i < textLengthInChars) {
                            Character.toCodePoint(firstChar, text[i])
                        } else {
                            TerminalEmulator.UNICODE_REPLACEMENT_CHAR
                        }
                    } else {
                        codePoint = firstChar.code
                    }

                    if (mClient.readShiftKey()) codePoint = Character.toUpperCase(codePoint)

                    var ctrlHeld = false
                    if (codePoint <= 31 && codePoint != 27) {
                        if (codePoint == '\n'.code) codePoint = '\r'.code

                        ctrlHeld = true
                        codePoint = when (codePoint) {
                            31 -> '_'.code
                            30 -> '^'.code
                            29 -> ']'.code
                            28 -> '\\'.code
                            else -> codePoint + 96
                        }
                    }

                    inputCodePoint(KEY_EVENT_SOURCE_SOFT_KEYBOARD, codePoint, ctrlHeld, false)
                    i++
                }
            }
        }
    }

    override fun computeVerticalScrollRange(): Int {
        val emulator = emulatorOrNull()
        return emulator?.screen?.getActiveRows() ?: 1
    }

    override fun computeVerticalScrollExtent(): Int {
        return emulatorOrNull()?.mRows ?: 1
    }

    override fun computeVerticalScrollOffset(): Int {
        val emulator = emulatorOrNull()
        return if (emulator == null) 1 else emulator.screen.getActiveRows() + mTopRow - emulator.mRows
    }

    fun onScreenUpdated() {
        onScreenUpdated(false)
    }

    fun onScreenUpdated(skipScrolling: Boolean) {
        val emulator = emulatorOrNull() ?: return
        var skip = skipScrolling
        val rowsInHistory = emulator.screen.getActiveTranscriptRows()
        if (mTopRow < -rowsInHistory) mTopRow = -rowsInHistory

        if (isSelectingText() || emulator.isAutoScrollDisabled) {
            val rowShift = emulator.scrollCounter
            if (-mTopRow + rowShift > rowsInHistory) {
                if (isSelectingText()) stopTextSelectionMode()

                if (emulator.isAutoScrollDisabled) {
                    mTopRow = -rowsInHistory
                    skip = true
                }
            } else {
                skip = true
                mTopRow -= rowShift
                decrementYTextSelectionCursors(rowShift)
            }
        }

        if (!skip && mTopRow != 0) {
            if (mTopRow < -3) awakenScrollBars()
            mTopRow = 0
        }

        emulator.clearScrollCounter()

        invalidate()
        if (mAccessibilityEnabled) contentDescription = text
    }

    fun setTextSize(textSize: Int) {
        mRenderer = TerminalRenderer(textSize, rendererOrNull()?.mTypeface ?: Typeface.MONOSPACE)
        updateSize()
    }

    fun setTypeface(newTypeface: Typeface) {
        mRenderer = TerminalRenderer(renderer.mTextSize, newTypeface)
        updateSize()
        invalidate()
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun isOpaque(): Boolean = true

    fun getColumnAndRow(event: MotionEvent, relativeToScroll: Boolean): IntArray {
        val column = (event.x / renderer.mFontWidth).toInt()
        var row = ((event.y - renderer.mFontLineSpacingAndAscent) / renderer.mFontLineSpacing).toInt()
        if (relativeToScroll) row += mTopRow
        return intArrayOf(column, row)
    }

    fun sendMouseEventCode(e: MotionEvent, button: Int, pressed: Boolean) {
        val columnAndRow = getColumnAndRow(e, false)
        var x = columnAndRow[0] + 1
        var y = columnAndRow[1] + 1
        if (pressed && (button == TerminalEmulator.MOUSE_WHEELDOWN_BUTTON || button == TerminalEmulator.MOUSE_WHEELUP_BUTTON)) {
            if (mMouseStartDownTime == e.downTime) {
                x = mMouseScrollStartX
                y = mMouseScrollStartY
            } else {
                mMouseStartDownTime = e.downTime
                mMouseScrollStartX = x
                mMouseScrollStartY = y
            }
        }
        emulator.sendMouseEvent(button, x, y, pressed)
    }

    fun doScroll(event: MotionEvent, rowsDown: Int) {
        val up = rowsDown < 0
        val amount = kotlin.math.abs(rowsDown)
        for (i in 0 until amount) {
            if (emulator.isMouseTrackingActive) {
                sendMouseEventCode(event, if (up) TerminalEmulator.MOUSE_WHEELUP_BUTTON else TerminalEmulator.MOUSE_WHEELDOWN_BUTTON, true)
            } else if (emulator.isAlternateBufferActive) {
                handleKeyCode(if (up) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN, 0)
            } else {
                mTopRow = min(0, max(-emulator.screen.getActiveTranscriptRows(), mTopRow + if (up) -1 else 1))
                if (!awakenScrollBars()) invalidate()
            }
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (emulatorOrNull() != null && event.isFromSource(InputDevice.SOURCE_MOUSE) && event.action == MotionEvent.ACTION_SCROLL) {
            val up = event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0.0f
            doScroll(event, if (up) -3 else 3)
            return true
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (emulatorOrNull() == null) return true
        val action = event.action

        if (isSelectingText()) {
            updateFloatingToolbarVisibility(event)
            mGestureRecognizer.onTouchEvent(event)
            return true
        } else if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                if (action == MotionEvent.ACTION_DOWN) showContextMenu()
                return true
            } else if (event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) {
                val clipboardManager = getContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = clipboardManager.primaryClip
                if (clipData != null) {
                    val clipItem: ClipData.Item? = clipData.getItemAt(0)
                    if (clipItem != null) {
                        val text = clipItem.coerceToText(getContext())
                        if (!TextUtils.isEmpty(text)) emulator.paste(text.toString())
                    }
                }
            } else if (emulator.isMouseTrackingActive) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, event.action == MotionEvent.ACTION_DOWN)
                    MotionEvent.ACTION_MOVE -> sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true)
                }
            }
        }

        mGestureRecognizer.onTouchEvent(event)
        return true
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
            mClient.logInfo(LOG_TAG, "onKeyPreIme(keyCode=$keyCode, event=$event)")
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            cancelRequestAutoFill()
            if (isSelectingText()) {
                stopTextSelectionMode()
                return true
            } else if (mClient.shouldBackButtonBeMappedToEscape()) {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> return onKeyDown(keyCode, event)
                    KeyEvent.ACTION_UP -> return onKeyUp(keyCode, event)
                }
            }
        } else if (mClient.shouldUseCtrlSpaceWorkaround() && keyCode == KeyEvent.KEYCODE_SPACE && event.isCtrlPressed) {
            return onKeyDown(keyCode, event)
        }
        return super.onKeyPreIme(keyCode, event)
    }

    @Suppress("DEPRECATION")
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
            mClient.logInfo(LOG_TAG, "onKeyDown(keyCode=$keyCode, isSystem()=${event.isSystem}, event=$event)")
        }
        if (emulatorOrNull() == null) return true
        if (isSelectingText()) stopTextSelectionMode()

        if (mClient.onKeyDown(keyCode, event, termSession)) {
            invalidate()
            return true
        } else if (event.isSystem && (!mClient.shouldBackButtonBeMappedToEscape() || keyCode != KeyEvent.KEYCODE_BACK)) {
            return super.onKeyDown(keyCode, event)
        } else if (event.action == KeyEvent.ACTION_MULTIPLE && keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            termSession.write(event.characters)
            return true
        }

        val metaState = event.metaState
        val controlDown = event.isCtrlPressed || mClient.readControlKey()
        val leftAltDown = metaState and KeyEvent.META_ALT_LEFT_ON != 0 || mClient.readAltKey()
        val shiftDown = event.isShiftPressed || mClient.readShiftKey()
        val rightAltDownFromEvent = metaState and KeyEvent.META_ALT_RIGHT_ON != 0

        var keyMod = 0
        if (controlDown) keyMod = keyMod or KeyHandler.KEYMOD_CTRL
        if (event.isAltPressed || leftAltDown) keyMod = keyMod or KeyHandler.KEYMOD_ALT
        if (shiftDown) keyMod = keyMod or KeyHandler.KEYMOD_SHIFT
        if (event.isNumLockOn) keyMod = keyMod or KeyHandler.KEYMOD_NUM_LOCK
        if (!event.isFunctionPressed && handleKeyCode(keyCode, keyMod)) {
            if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) mClient.logInfo(LOG_TAG, "handleKeyCode() took key event")
            return true
        }

        var bitsToClear = KeyEvent.META_CTRL_MASK
        if (!rightAltDownFromEvent) bitsToClear = bitsToClear or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        var effectiveMetaState = event.metaState and bitsToClear.inv()

        if (shiftDown) effectiveMetaState = effectiveMetaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (mClient.readFnKey()) effectiveMetaState = effectiveMetaState or KeyEvent.META_FUNCTION_ON

        var result = event.getUnicodeChar(effectiveMetaState)
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
            mClient.logInfo(LOG_TAG, "KeyEvent#getUnicodeChar($effectiveMetaState) returned: $result")
        }
        if (result == 0) return false

        val oldCombiningAccent = mCombiningAccent
        if (result and KeyCharacterMap.COMBINING_ACCENT != 0) {
            if (mCombiningAccent != 0) inputCodePoint(event.deviceId, mCombiningAccent, controlDown, leftAltDown)
            mCombiningAccent = result and KeyCharacterMap.COMBINING_ACCENT_MASK
        } else {
            if (mCombiningAccent != 0) {
                val combinedChar = KeyCharacterMap.getDeadChar(mCombiningAccent, result)
                if (combinedChar > 0) result = combinedChar
                mCombiningAccent = 0
            }
            inputCodePoint(event.deviceId, result, controlDown, leftAltDown)
        }

        if (mCombiningAccent != oldCombiningAccent) invalidate()
        return true
    }

    fun inputCodePoint(eventSource: Int, codePointValue: Int, controlDownFromEvent: Boolean, leftAltDownFromEvent: Boolean) {
        var codePoint = codePointValue
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
            mClient.logInfo(
                LOG_TAG,
                "inputCodePoint(eventSource=$eventSource, codePoint=$codePoint, controlDownFromEvent=$controlDownFromEvent, leftAltDownFromEvent=$leftAltDownFromEvent)",
            )
        }

        if (sessionOrNull() == null) return

        emulatorOrNull()?.setCursorBlinkState(true)

        val controlDown = controlDownFromEvent || mClient.readControlKey()
        val altDown = leftAltDownFromEvent || mClient.readAltKey()

        if (mClient.onCodePoint(codePoint, controlDown, termSession)) return

        if (controlDown) {
            codePoint = when {
                codePoint >= 'a'.code && codePoint <= 'z'.code -> codePoint - 'a'.code + 1
                codePoint >= 'A'.code && codePoint <= 'Z'.code -> codePoint - 'A'.code + 1
                codePoint == ' '.code || codePoint == '2'.code -> 0
                codePoint == '['.code || codePoint == '3'.code -> 27
                codePoint == '\\'.code || codePoint == '4'.code -> 28
                codePoint == ']'.code || codePoint == '5'.code -> 29
                codePoint == '^'.code || codePoint == '6'.code -> 30
                codePoint == '_'.code || codePoint == '7'.code || codePoint == '/'.code -> 31
                codePoint == '8'.code -> 127
                else -> codePoint
            }
        }

        if (codePoint > -1) {
            if (eventSource > KEY_EVENT_SOURCE_SOFT_KEYBOARD) {
                codePoint = when (codePoint) {
                    0x02DC -> 0x007E
                    0x02CB -> 0x0060
                    0x02C6 -> 0x005E
                    else -> codePoint
                }
            }
            termSession.writeCodePoint(altDown, codePoint)
        }
    }

    fun handleKeyCode(keyCode: Int, keyMod: Int): Boolean {
        emulatorOrNull()?.setCursorBlinkState(true)

        if (handleKeyCodeAction(keyCode, keyMod)) return true

        val term = requireNotNull(termSession.emulator)
        val code = KeyHandler.getCode(keyCode, keyMod, term.isCursorKeysApplicationMode, term.isKeypadApplicationMode)
        if (code == null) return false
        termSession.write(code)
        return true
    }

    fun handleKeyCodeAction(keyCode: Int, keyMod: Int): Boolean {
        val shiftDown = keyMod and KeyHandler.KEYMOD_SHIFT != 0

        when (keyCode) {
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN -> if (shiftDown) {
                val time = SystemClock.uptimeMillis()
                val motionEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
                doScroll(motionEvent, if (keyCode == KeyEvent.KEYCODE_PAGE_UP) -emulator.mRows else emulator.mRows)
                motionEvent.recycle()
                return true
            }
        }

        return false
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
            mClient.logInfo(LOG_TAG, "onKeyUp(keyCode=$keyCode, event=$event)")
        }

        if (emulatorOrNull() == null && keyCode != KeyEvent.KEYCODE_BACK) return true

        if (mClient.onKeyUp(keyCode, event)) {
            invalidate()
            return true
        } else if (event.isSystem) {
            return super.onKeyUp(keyCode, event)
        }

        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateSize()
    }

    fun updateSize() {
        val viewWidth = width
        val viewHeight = height
        if (viewWidth == 0 || viewHeight == 0 || sessionOrNull() == null) return

        val newColumns = max(4, (viewWidth / renderer.mFontWidth).toInt())
        val newRows = max(4, (viewHeight - renderer.mFontLineSpacingAndAscent) / renderer.mFontLineSpacing)
        val emulator = emulatorOrNull()

        if (emulator == null || newColumns != emulator.mColumns || newRows != emulator.mRows) {
            termSession.updateSize(newColumns, newRows, renderer.getFontWidth().toInt(), renderer.getFontLineSpacing())
            mEmulator = requireNotNull(termSession.emulator)
            mClient.onEmulatorSet()

            mTerminalCursorBlinkerRunnable?.setEmulator(this.emulator)

            mTopRow = 0
            scrollTo(0, 0)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (emulatorOrNull() == null) {
            canvas.drawColor(0XFF000000.toInt())
        } else {
            val sel = mDefaultSelectors
            mTextSelectionCursorController?.getSelectors(sel)
            renderer.render(emulator, canvas, mTopRow, sel[0], sel[1], sel[2], sel[3])
            renderTextSelection()
        }
    }

    val currentSession: TerminalSession
        get() = termSession

    private val text: CharSequence
        get() = emulator.screen.getSelectedText(0, mTopRow, emulator.mColumns, mTopRow + emulator.mRows)

    fun getCursorX(x: Float): Int = (x / renderer.mFontWidth).toInt()

    fun getCursorY(y: Float): Int = ((y - 40) / renderer.mFontLineSpacing + mTopRow).toInt()

    fun getPointX(cxValue: Int): Int {
        var cx = cxValue
        if (cx > emulator.mColumns) cx = emulator.mColumns
        return Math.round(cx * renderer.mFontWidth)
    }

    fun getPointY(cy: Int): Int = Math.round((cy - mTopRow) * renderer.mFontLineSpacing.toFloat())

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun autofill(value: AutofillValue) {
        if (value.isText) {
            termSession.write(value.textValue.toString())
        }

        resetAutoFill()
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun getAutofillType(): Int = mAutoFillType

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun getAutofillHints(): Array<String> = mAutoFillHints

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun getAutofillValue(): AutofillValue = AutofillValue.forText("")

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun getImportantForAutofill(): Int = mAutoFillImportance

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Synchronized
    private fun resetAutoFill() {
        mAutoFillType = AUTOFILL_TYPE_NONE
        mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_NO
        mAutoFillHints = emptyArray()
    }

    fun getAutoFillManagerService(): AutofillManager? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        return try {
            getContext().getSystemService(AutofillManager::class.java)
        } catch (e: Exception) {
            mClient.logStackTraceWithMessage(LOG_TAG, "Failed to get AutofillManager service", e)
            null
        }
    }

    fun isAutoFillEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false

        return try {
            val autofillManager = getAutoFillManagerService()
            autofillManager != null && autofillManager.isEnabled
        } catch (e: Exception) {
            mClient.logStackTraceWithMessage(LOG_TAG, "Failed to check if Autofill is enabled", e)
            false
        }
    }

    @Synchronized
    fun requestAutoFillUsername() {
        requestAutoFill(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) arrayOf(AUTOFILL_HINT_USERNAME) else null)
    }

    @Synchronized
    fun requestAutoFillPassword() {
        requestAutoFill(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) arrayOf(AUTOFILL_HINT_PASSWORD) else null)
    }

    @Synchronized
    fun requestAutoFill(autoFillHints: Array<String>?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (autoFillHints == null || autoFillHints.isEmpty()) return

        try {
            val autofillManager = getAutoFillManagerService()
            if (autofillManager != null && autofillManager.isEnabled) {
                mAutoFillType = AUTOFILL_TYPE_TEXT
                mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_YES
                mAutoFillHints = autoFillHints
                autofillManager.requestAutofill(this)
            }
        } catch (e: Exception) {
            mClient.logStackTraceWithMessage(LOG_TAG, "Failed to request Autofill", e)
        }
    }

    @Synchronized
    fun cancelRequestAutoFill() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (mAutoFillType == AUTOFILL_TYPE_NONE) return

        try {
            val autofillManager = getAutoFillManagerService()
            if (autofillManager != null && autofillManager.isEnabled) {
                resetAutoFill()
                autofillManager.cancel()
            }
        } catch (e: Exception) {
            mClient.logStackTraceWithMessage(LOG_TAG, "Failed to cancel Autofill request", e)
        }
    }

    @Synchronized
    fun setTerminalCursorBlinkerRate(blinkRate: Int): Boolean {
        val result: Boolean

        if (blinkRate != 0 && (blinkRate < TERMINAL_CURSOR_BLINK_RATE_MIN || blinkRate > TERMINAL_CURSOR_BLINK_RATE_MAX)) {
            mClient.logError(LOG_TAG, "The cursor blink rate must be in between $TERMINAL_CURSOR_BLINK_RATE_MIN-$TERMINAL_CURSOR_BLINK_RATE_MAX: $blinkRate")
            mTerminalCursorBlinkerRate = 0
            result = false
        } else {
            mClient.logVerbose(LOG_TAG, "Setting cursor blinker rate to $blinkRate")
            mTerminalCursorBlinkerRate = blinkRate
            result = true
        }

        if (mTerminalCursorBlinkerRate == 0) {
            mClient.logVerbose(LOG_TAG, "Cursor blinker disabled")
            stopTerminalCursorBlinker()
        }

        return result
    }

    @Synchronized
    fun setTerminalCursorBlinkerState(start: Boolean, startOnlyIfCursorEnabled: Boolean) {
        stopTerminalCursorBlinker()

        val emulator = emulatorOrNull() ?: return

        emulator.setCursorBlinkingEnabled(false)

        if (start) {
            if (mTerminalCursorBlinkerRate < TERMINAL_CURSOR_BLINK_RATE_MIN || mTerminalCursorBlinkerRate > TERMINAL_CURSOR_BLINK_RATE_MAX) {
                return
            } else if (startOnlyIfCursorEnabled && !emulator.isCursorEnabled) {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
                    mClient.logVerbose(LOG_TAG, "Ignoring call to start cursor blinker since cursor is not enabled")
                }
                return
            }

            if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
                mClient.logVerbose(LOG_TAG, "Starting cursor blinker with the blink rate $mTerminalCursorBlinkerRate")
            }
            if (mTerminalCursorBlinkerHandler == null) {
                mTerminalCursorBlinkerHandler = Handler(Looper.getMainLooper())
            }
            mTerminalCursorBlinkerRunnable = TerminalCursorBlinkerRunnable(emulator, mTerminalCursorBlinkerRate)
            emulator.setCursorBlinkingEnabled(true)
            mTerminalCursorBlinkerRunnable?.run()
        }
    }

    private fun stopTerminalCursorBlinker() {
        val handler = mTerminalCursorBlinkerHandler
        val runnable = mTerminalCursorBlinkerRunnable
        if (handler != null && runnable != null) {
            if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) mClient.logVerbose(LOG_TAG, "Stopping cursor blinker")
            handler.removeCallbacks(runnable)
        }
    }

    private inner class TerminalCursorBlinkerRunnable(
        private var mEmulator: TerminalEmulator?,
        private val mBlinkRate: Int,
    ) : Runnable {
        private var mCursorVisible = false

        fun setEmulator(emulator: TerminalEmulator?) {
            mEmulator = emulator
        }

        override fun run() {
            try {
                val emulator = mEmulator
                if (emulator != null) {
                    mCursorVisible = !mCursorVisible
                    emulator.setCursorBlinkState(mCursorVisible)
                    invalidate()
                }
            } finally {
                mTerminalCursorBlinkerHandler?.postDelayed(this, mBlinkRate.toLong())
            }
        }
    }

    fun getTextSelectionCursorController(): TextSelectionCursorController {
        var controller = mTextSelectionCursorController
        if (controller == null) {
            controller = TextSelectionCursorController(this)
            mTextSelectionCursorController = controller

            viewTreeObserver?.addOnTouchModeChangeListener(controller)
        }

        return controller
    }

    private fun showTextSelectionCursors(event: MotionEvent) {
        getTextSelectionCursorController().show(event)
    }

    private fun hideTextSelectionCursors(): Boolean = getTextSelectionCursorController().hide()

    private fun renderTextSelection() {
        mTextSelectionCursorController?.render()
    }

    fun isSelectingText(): Boolean = mTextSelectionCursorController?.isActive() ?: false

    fun getSelectedText(): String? {
        return if (isSelectingText() && mTextSelectionCursorController != null) {
            mTextSelectionCursorController?.getSelectedText()
        } else {
            null
        }
    }

    fun getStoredSelectedText(): String? = mTextSelectionCursorController?.getStoredSelectedText()

    fun unsetStoredSelectedText() {
        mTextSelectionCursorController?.unsetStoredSelectedText()
    }

    private fun getTextSelectionActionMode(): ActionMode? = mTextSelectionCursorController?.getActionMode()

    fun startTextSelectionMode(event: MotionEvent) {
        if (!requestFocus()) return

        showTextSelectionCursors(event)
        mClient.copyModeChanged(isSelectingText())

        invalidate()
    }

    fun stopTextSelectionMode() {
        if (hideTextSelectionCursors()) {
            mClient.copyModeChanged(isSelectingText())
            invalidate()
        }
    }

    private fun decrementYTextSelectionCursors(decrement: Int) {
        mTextSelectionCursorController?.decrementYTextSelectionCursors(decrement)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        mTextSelectionCursorController?.let {
            viewTreeObserver.addOnTouchModeChangeListener(it)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        mTextSelectionCursorController?.let {
            stopTextSelectionMode()
            viewTreeObserver.removeOnTouchModeChangeListener(it)
            it.onDetached()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun showFloatingToolbar() {
        if (getTextSelectionActionMode() != null) {
            val delay = ViewConfiguration.getDoubleTapTimeout()
            postDelayed(mShowFloatingToolbar, delay.toLong())
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    fun hideFloatingToolbar() {
        if (getTextSelectionActionMode() != null) {
            removeCallbacks(mShowFloatingToolbar)
            getTextSelectionActionMode()?.hide(-1)
        }
    }

    fun updateFloatingToolbarVisibility(event: MotionEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && getTextSelectionActionMode() != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> hideFloatingToolbar()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> showFloatingToolbar()
            }
        }
    }

    companion object {
        private var TERMINAL_VIEW_KEY_LOGGING_ENABLED = false
        const val TERMINAL_CURSOR_BLINK_RATE_MIN = 100
        const val TERMINAL_CURSOR_BLINK_RATE_MAX = 2000
        const val KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD = KeyCharacterMap.VIRTUAL_KEYBOARD
        const val KEY_EVENT_SOURCE_SOFT_KEYBOARD = 0
        private const val LOG_TAG = "TerminalView"
    }
}
