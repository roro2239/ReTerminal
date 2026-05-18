package com.rk.terminal.ui.screens.terminal.virtualkeys

class SpecialButton(val key: String) {
    init {
        map[key] = this
    }

    override fun toString(): String = key

    companion object {
        private val map = HashMap<String, SpecialButton>()

        @JvmField
        val CTRL = SpecialButton("CTRL")

        @JvmField
        val ALT = SpecialButton("ALT")

        @JvmField
        val SHIFT = SpecialButton("SHIFT")

        @JvmField
        val FN = SpecialButton("FN")

        @JvmStatic
        fun valueOf(key: String): SpecialButton? = map[key]
    }
}
