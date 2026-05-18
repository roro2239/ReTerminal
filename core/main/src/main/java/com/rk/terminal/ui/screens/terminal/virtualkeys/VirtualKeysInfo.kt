package com.rk.terminal.ui.screens.terminal.virtualkeys

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class VirtualKeysInfo private constructor(
    private val mButtons: Array<Array<VirtualKeyButton>>,
) {
    @Throws(JSONException::class)
    constructor(
        propertiesInfo: String,
        style: String?,
        extraKeyAliasMap: VirtualKeysConstants.VirtualKeyDisplayMap,
    ) : this(
        initVirtualKeysInfo(
            propertiesInfo,
            getCharDisplayMapForStyle(style),
            extraKeyAliasMap,
        ),
    )

    @Throws(JSONException::class)
    constructor(
        propertiesInfo: String,
        extraKeyDisplayMap: VirtualKeysConstants.VirtualKeyDisplayMap,
        extraKeyAliasMap: VirtualKeysConstants.VirtualKeyDisplayMap,
    ) : this(initVirtualKeysInfo(propertiesInfo, extraKeyDisplayMap, extraKeyAliasMap))

    fun getMatrix(): Array<Array<VirtualKeyButton>> = mButtons

    companion object {
        @JvmStatic
        @Throws(JSONException::class)
        private fun initVirtualKeysInfo(
            propertiesInfo: String,
            extraKeyDisplayMap: VirtualKeysConstants.VirtualKeyDisplayMap,
            extraKeyAliasMap: VirtualKeysConstants.VirtualKeyDisplayMap,
        ): Array<Array<VirtualKeyButton>> {
            val arr = JSONArray(propertiesInfo)
            val matrix = Array(arr.length()) { index ->
                val line = arr.getJSONArray(index)
                Array<Any>(line.length()) { lineIndex -> line.get(lineIndex) }
            }

            return Array(matrix.size) { row ->
                Array(matrix[row].size) { column ->
                    val jobject = normalizeKeyConfig(matrix[row][column])
                    if (!jobject.has(VirtualKeyButton.KEY_POPUP)) {
                        VirtualKeyButton(jobject, extraKeyDisplayMap, extraKeyAliasMap)
                    } else {
                        val popupJobject = normalizeKeyConfig(jobject.get(VirtualKeyButton.KEY_POPUP))
                        val popup = VirtualKeyButton(popupJobject, extraKeyDisplayMap, extraKeyAliasMap)
                        VirtualKeyButton(jobject, popup, extraKeyDisplayMap, extraKeyAliasMap)
                    }
                }
            }
        }

        @JvmStatic
        @Throws(JSONException::class)
        private fun normalizeKeyConfig(key: Any): JSONObject {
            return when (key) {
                is String -> JSONObject().put(VirtualKeyButton.KEY_KEY_NAME, key)
                is JSONObject -> key
                else -> throw JSONException("An key in the extra-key matrix must be a string or an object")
            }
        }

        @JvmStatic
        fun getCharDisplayMapForStyle(style: String?): VirtualKeysConstants.VirtualKeyDisplayMap {
            return when (style) {
                "arrows-only" -> VirtualKeysConstants.EXTRA_KEY_DISPLAY_MAPS.ARROWS_ONLY_CHAR_DISPLAY
                "arrows-all" -> VirtualKeysConstants.EXTRA_KEY_DISPLAY_MAPS.LOTS_OF_ARROWS_CHAR_DISPLAY
                "all" -> VirtualKeysConstants.EXTRA_KEY_DISPLAY_MAPS.FULL_ISO_CHAR_DISPLAY
                "none" -> VirtualKeysConstants.VirtualKeyDisplayMap()
                else -> VirtualKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY
            }
        }
    }
}
