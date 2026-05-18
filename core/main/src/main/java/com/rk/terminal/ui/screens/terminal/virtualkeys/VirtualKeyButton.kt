package com.rk.terminal.ui.screens.terminal.virtualkeys

import android.text.TextUtils
import org.json.JSONException
import org.json.JSONObject

class VirtualKeyButton @Throws(JSONException::class) constructor(
    config: JSONObject,
    private val popup: VirtualKeyButton?,
    extraKeyDisplayMap: VirtualKeysConstants.VirtualKeyDisplayMap,
    extraKeyAliasMap: VirtualKeysConstants.VirtualKeyDisplayMap,
) {
    val key: String
    private val macro: Boolean
    private val display: String

    @Throws(JSONException::class)
    constructor(
        config: JSONObject,
        extraKeyDisplayMap: VirtualKeysConstants.VirtualKeyDisplayMap,
        extraKeyAliasMap: VirtualKeysConstants.VirtualKeyDisplayMap,
    ) : this(config, null, extraKeyDisplayMap, extraKeyAliasMap)

    init {
        val keyFromConfig = getStringFromJson(config, KEY_KEY_NAME)
        val macroFromConfig = getStringFromJson(config, KEY_MACRO)
        val keys: Array<String>
        if (keyFromConfig != null && macroFromConfig != null) {
            throw JSONException("Both key and macro can't be set for the same key. key: \"$keyFromConfig\", macro: \"$macroFromConfig\"")
        } else if (keyFromConfig != null) {
            keys = arrayOf(keyFromConfig)
            macro = false
        } else if (macroFromConfig != null) {
            keys = macroFromConfig.split(" ").toTypedArray()
            macro = true
        } else {
            throw JSONException("All keys have to specify either key or macro")
        }

        for (index in keys.indices) {
            keys[index] = replaceAlias(extraKeyAliasMap, keys[index])
        }

        key = TextUtils.join(" ", keys)
        display =
            getStringFromJson(config, KEY_DISPLAY_NAME)
                ?: keys.joinToString(" ") { item -> extraKeyDisplayMap.get(item, item) }
    }

    fun getStringFromJson(config: JSONObject, key: String): String? =
        try {
            config.getString(key)
        } catch (_: JSONException) {
            null
        }

    fun isMacro(): Boolean = macro

    fun getDisplay(): String = display

    fun getPopup(): VirtualKeyButton? = popup

    companion object {
        const val KEY_KEY_NAME: String = "key"
        const val KEY_MACRO: String = "macro"
        const val KEY_DISPLAY_NAME: String = "display"
        const val KEY_POPUP: String = "popup"

        @JvmStatic
        fun replaceAlias(
            extraKeyAliasMap: VirtualKeysConstants.VirtualKeyDisplayMap,
            key: String,
        ): String = extraKeyAliasMap.get(key, key)
    }
}
