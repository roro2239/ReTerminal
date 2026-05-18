package com.rk.terminal.ui.screens.terminal.virtualkeys

import android.widget.Button

class SpecialButtonState(
    private val mVirtualKeysView: VirtualKeysView,
) {
    @JvmField
    var isCreated = false

    @JvmField
    var isActive = false

    @JvmField
    var isLocked = false

    @JvmField
    var buttons: MutableList<Button> = ArrayList()

    fun setIsCreated(value: Boolean) {
        isCreated = value
    }

    fun setIsActive(value: Boolean) {
        isActive = value
        for (button in buttons) {
            button.setTextColor(
                if (value) {
                    mVirtualKeysView.buttonActiveTextColor
                } else {
                    mVirtualKeysView.buttonTextColor
                },
            )
        }
    }

    fun setIsLocked(value: Boolean) {
        isLocked = value
    }
}
