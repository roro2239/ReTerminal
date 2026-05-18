package com.rk.terminal.ui.screens.terminal.virtualkeys

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.PopupWindow
import com.rk.settings.Settings as AppSettings
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class VirtualKeysView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GridLayout(context, attrs) {
    private var mSpecialButtons: Map<SpecialButton, SpecialButtonState> = emptyMap()
    private var mSpecialButtonsKeys: Set<String> = emptySet()
    private var mRepetitiveKeys: List<String> = emptyList()
    private var mButtonTextAllCaps = true
    private var mPopupWindow: PopupWindow? = null
    private var mScheduledExecutor: ScheduledExecutorService? = null
    private val mHandler = Handler(Looper.getMainLooper())
    private var mSpecialButtonsLongHoldRunnable: SpecialButtonsLongHoldRunnable? = null
    private var mLongPressCount = 0

    var virtualKeysViewClient: IVirtualKeysView? = null

    var buttonTextColor: Int = 0

    var buttonActiveTextColor: Int = 0

    var buttonBackgroundColor: Int = 0

    var buttonActiveBackgroundColor: Int = 0

    var longPressTimeout: Int = 0
        set(value) {
            field =
                if (value >= MIN_LONG_PRESS_DURATION && value <= MAX_LONG_PRESS_DURATION) {
                    value
                } else {
                    FALLBACK_LONG_PRESS_DURATION
                }
        }

    var longPressRepeatDelay: Int = 0
        set(value) {
            field =
                if (value >= MIN_LONG_PRESS__REPEAT_DELAY && value <= MAX_LONG_PRESS__REPEAT_DELAY) {
                    value
                } else {
                    DEFAULT_LONG_PRESS_REPEAT_DELAY
                }
        }

    init {
        setRepetitiveKeys(VirtualKeysConstants.PRIMARY_REPETITIVE_KEYS)
        setSpecialButtons(getDefaultSpecialButtons(this))
        setButtonColors(
            DEFAULT_BUTTON_TEXT_COLOR,
            DEFAULT_BUTTON_ACTIVE_TEXT_COLOR,
            DEFAULT_BUTTON_BACKGROUND_COLOR,
            DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR,
        )
        longPressTimeout = ViewConfiguration.getLongPressTimeout()
        longPressRepeatDelay = DEFAULT_LONG_PRESS_REPEAT_DELAY
    }

    fun setButtonColors(
        buttonTextColor: Int,
        buttonActiveTextColor: Int,
        buttonBackgroundColor: Int,
        buttonActiveBackgroundColor: Int,
    ) {
        this.buttonTextColor = buttonTextColor
        this.buttonActiveTextColor = buttonActiveTextColor
        this.buttonBackgroundColor = buttonBackgroundColor
        this.buttonActiveBackgroundColor = buttonActiveBackgroundColor
    }

    fun getDefaultSpecialButtons(
        extraKeysView: VirtualKeysView,
    ): Map<SpecialButton, SpecialButtonState> =
        HashMap<SpecialButton, SpecialButtonState>().apply {
            put(SpecialButton.CTRL, SpecialButtonState(extraKeysView))
            put(SpecialButton.ALT, SpecialButtonState(extraKeysView))
            put(SpecialButton.SHIFT, SpecialButtonState(extraKeysView))
            put(SpecialButton.FN, SpecialButtonState(extraKeysView))
        }

    fun getRepetitiveKeys(): List<String> = mRepetitiveKeys.map { String(it.toCharArray()) }

    fun setRepetitiveKeys(repetitiveKeys: List<String>) {
        mRepetitiveKeys = repetitiveKeys
    }

    fun getSpecialButtons(): Map<SpecialButton, SpecialButtonState> =
        mSpecialButtons.entries.associate { it.key to it.value }

    fun setSpecialButtons(specialButtons: Map<SpecialButton, SpecialButtonState>) {
        mSpecialButtons = specialButtons
        mSpecialButtonsKeys = specialButtons.keys.map { it.key }.toSet()
    }

    fun getSpecialButtonsKeys(): Set<String> = mSpecialButtonsKeys.map { String(it.toCharArray()) }.toSet()

    fun setButtonTextAllCaps(buttonTextAllCaps: Boolean) {
        mButtonTextAllCaps = buttonTextAllCaps
    }

    @SuppressLint("ClickableViewAccessibility")
    fun reload(extraKeysInfo: VirtualKeysInfo?) {
        if (extraKeysInfo == null) return

        for (state in mSpecialButtons.values) {
            state.buttons = ArrayList()
        }

        removeAllViews()

        val buttons = extraKeysInfo.getMatrix()

        rowCount = buttons.size
        columnCount = maximumLength(buttons)

        for (row in buttons.indices) {
            for (col in buttons[row].indices) {
                val buttonInfo = buttons[row][col]

                val button =
                    if (isSpecialButton(buttonInfo)) {
                        createSpecialButton(buttonInfo.key, true) ?: return
                    } else {
                        Button(context, null, android.R.attr.buttonBarButtonStyle)
                    }

                button.text = buttonInfo.getDisplay()
                button.setTextColor(buttonTextColor)
                button.isAllCaps = mButtonTextAllCaps
                button.setPadding(0, 0, 0, 0)

                button.setOnClickListener { view ->
                    performVirtualKeyButtonHapticFeedback(view, buttonInfo, button)
                    onAnyVirtualKeyButtonClick(view, buttonInfo, button)
                }

                button.setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            view.setBackgroundColor(buttonActiveBackgroundColor)
                            startScheduledExecutors(view, buttonInfo, button)
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (buttonInfo.getPopup() != null) {
                                if (mPopupWindow == null && event.y < 0) {
                                    stopScheduledExecutors()
                                    view.setBackgroundColor(buttonBackgroundColor)
                                    showPopup(view, requireNotNull(buttonInfo.getPopup()))
                                }
                                if (mPopupWindow != null && event.y > 0) {
                                    view.setBackgroundColor(buttonActiveBackgroundColor)
                                    dismissPopup()
                                }
                            }
                            true
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            view.setBackgroundColor(buttonBackgroundColor)
                            stopScheduledExecutors()
                            true
                        }

                        MotionEvent.ACTION_UP -> {
                            view.setBackgroundColor(buttonBackgroundColor)
                            stopScheduledExecutors()
                            if (mLongPressCount == 0 || mPopupWindow != null) {
                                if (mPopupWindow != null) {
                                    dismissPopup()
                                    if (buttonInfo.getPopup() != null) {
                                        onAnyVirtualKeyButtonClick(view, requireNotNull(buttonInfo.getPopup()), button)
                                    }
                                } else {
                                    view.performClick()
                                }
                            }
                            true
                        }

                        else -> true
                    }
                }

                val param = LayoutParams()
                param.width = 0
                param.height = 0
                param.setMargins(0, 0, 0, 0)
                param.columnSpec = spec(col, FILL, 1f)
                param.rowSpec = spec(row, FILL, 1f)
                button.layoutParams = param

                addView(button)
            }
        }
    }

    private fun performVirtualKeyButtonHapticFeedback(
        view: View,
        buttonInfo: VirtualKeyButton,
        button: Button,
    ) {
        if (virtualKeysViewClient?.performVirtualKeyButtonHapticFeedback(view, buttonInfo, button) == true) {
            return
        }

        if (AppSettings.vibrate && button.isHapticFeedbackEnabled) {
            if (Build.VERSION.SDK_INT >= 28) {
                button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } else {
                if (Settings.Global.getInt(context.contentResolver, "zen_mode", 0) != 2) {
                    button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
        }
    }

    private fun onAnyVirtualKeyButtonClick(
        view: View,
        buttonInfo: VirtualKeyButton,
        button: Button,
    ) {
        if (isSpecialButton(buttonInfo)) {
            if (mLongPressCount > 0) return
            val specialButton = SpecialButton.valueOf(buttonInfo.key) ?: return
            val state = mSpecialButtons[specialButton] ?: return

            state.setIsActive(!state.isActive)
            if (!state.isActive) {
                state.setIsLocked(false)
            }
        } else {
            onVirtualKeyButtonClick(view, buttonInfo, button)
        }
    }

    private fun onVirtualKeyButtonClick(
        view: View,
        buttonInfo: VirtualKeyButton,
        button: Button,
    ) {
        virtualKeysViewClient?.onVirtualKeyButtonClick(view, buttonInfo, button)
    }

    private fun startScheduledExecutors(
        view: View,
        buttonInfo: VirtualKeyButton,
        button: Button,
    ) {
        stopScheduledExecutors()
        mLongPressCount = 0
        if (mRepetitiveKeys.contains(buttonInfo.key)) {
            mScheduledExecutor = Executors.newSingleThreadScheduledExecutor()
            requireNotNull(mScheduledExecutor).scheduleWithFixedDelay(
                {
                    mLongPressCount++
                    onVirtualKeyButtonClick(view, buttonInfo, button)
                },
                longPressTimeout.toLong(),
                longPressRepeatDelay.toLong(),
                TimeUnit.MILLISECONDS,
            )
        } else if (isSpecialButton(buttonInfo)) {
            val specialButton = SpecialButton.valueOf(buttonInfo.key) ?: return
            val state = mSpecialButtons[specialButton] ?: return
            mSpecialButtonsLongHoldRunnable = SpecialButtonsLongHoldRunnable(state)
            mHandler.postDelayed(requireNotNull(mSpecialButtonsLongHoldRunnable), longPressTimeout.toLong())
        }
    }

    private fun stopScheduledExecutors() {
        mScheduledExecutor?.shutdownNow()
        mScheduledExecutor = null

        mSpecialButtonsLongHoldRunnable?.let(mHandler::removeCallbacks)
        mSpecialButtonsLongHoldRunnable = null
    }

    fun showPopup(view: View, extraButton: VirtualKeyButton) {
        val width = view.measuredWidth
        val height = view.measuredHeight
        val button =
            if (isSpecialButton(extraButton)) {
                createSpecialButton(extraButton.key, false) ?: return
            } else {
                Button(context, null, android.R.attr.buttonBarButtonStyle).apply {
                    setTextColor(buttonTextColor)
                }
            }
        button.text = extraButton.getDisplay()
        button.isAllCaps = mButtonTextAllCaps
        button.setPadding(0, 0, 0, 0)
        button.minHeight = 0
        button.minWidth = 0
        button.minimumWidth = 0
        button.minimumHeight = 0
        button.width = width
        button.height = height
        button.setBackgroundColor(buttonActiveBackgroundColor)
        PopupWindow(this).also { popupWindow ->
            popupWindow.width = ViewGroup.LayoutParams.WRAP_CONTENT
            popupWindow.height = ViewGroup.LayoutParams.WRAP_CONTENT
            popupWindow.contentView = button
            popupWindow.isOutsideTouchable = true
            popupWindow.isFocusable = false
            popupWindow.showAsDropDown(view, 0, -2 * height)
            mPopupWindow = popupWindow
        }
    }

    private fun dismissPopup() {
        val popupWindow = requireNotNull(mPopupWindow)
        popupWindow.contentView = null
        popupWindow.dismiss()
        mPopupWindow = null
    }

    fun isSpecialButton(button: VirtualKeyButton): Boolean = mSpecialButtonsKeys.contains(button.key)

    private fun createSpecialButton(
        buttonKey: String,
        needUpdate: Boolean,
    ): Button? {
        val specialButton = SpecialButton.valueOf(buttonKey) ?: return null
        val state = mSpecialButtons[specialButton] ?: return null
        state.setIsCreated(true)
        val button = Button(context, null, android.R.attr.buttonBarButtonStyle)
        button.setTextColor(if (state.isActive) buttonActiveTextColor else buttonTextColor)
        if (needUpdate) {
            state.buttons.add(button)
        }
        return button
    }

    fun readSpecialButton(
        specialButton: SpecialButton,
        autoSetInActive: Boolean,
    ): Boolean? {
        val state = mSpecialButtons[specialButton] ?: return null

        if (!state.isCreated || !state.isActive) return false

        if (autoSetInActive && !state.isLocked) {
            state.setIsActive(false)
        }

        return true
    }

    private inner class SpecialButtonsLongHoldRunnable(
        private val mState: SpecialButtonState,
    ) : Runnable {
        override fun run() {
            mState.setIsLocked(!mState.isActive)
            mState.setIsActive(!mState.isActive)
            mLongPressCount++
        }
    }

    interface IVirtualKeysView {
        fun onVirtualKeyButtonClick(
            view: View?,
            buttonInfo: VirtualKeyButton?,
            button: Button?,
        )

        fun performVirtualKeyButtonHapticFeedback(
            view: View?,
            buttonInfo: VirtualKeyButton?,
            button: Button?,
        ): Boolean
    }

    companion object {
        @JvmField
        val DEFAULT_BUTTON_TEXT_COLOR: Int = 0xFFFFFFFF.toInt()

        @JvmField
        val DEFAULT_BUTTON_ACTIVE_TEXT_COLOR: Int = 0xFFf44336.toInt()

        @JvmField
        val DEFAULT_BUTTON_BACKGROUND_COLOR: Int = 0x00000000

        @JvmField
        val DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR: Int = 0xFF7F7F7F.toInt()

        const val MIN_LONG_PRESS_DURATION: Int = 200
        const val MAX_LONG_PRESS_DURATION: Int = 3000
        const val FALLBACK_LONG_PRESS_DURATION: Int = 400
        const val MIN_LONG_PRESS__REPEAT_DELAY: Int = 5
        const val MAX_LONG_PRESS__REPEAT_DELAY: Int = 2000
        const val DEFAULT_LONG_PRESS_REPEAT_DELAY: Int = 80

        @JvmStatic
        fun maximumLength(matrix: Array<out Array<out Any?>>): Int {
            var m = 0
            for (row in matrix) {
                m = Math.max(m, row.size)
            }
            return m
        }
    }
}
