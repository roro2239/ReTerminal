package com.rk.components.compose.edges

import android.content.Context
import android.view.View
import android.widget.EdgeEffect
import com.rk.components.compose.utils.Utilities

open class EdgeEffectCompat(context: Context) : EdgeEffect(context) {
    override fun getDistance(): Float =
        if (Utilities.ATLEAST_S) super.getDistance() else 0f

    override fun onPullDistance(deltaDistance: Float, displacement: Float): Float =
        if (Utilities.ATLEAST_S) {
            super.onPullDistance(deltaDistance, displacement)
        } else {
            onPull(deltaDistance, displacement)
            deltaDistance
        }

    companion object {
        @JvmStatic
        fun create(context: Context, view: View): EdgeEffectCompat =
            if (Utilities.ATLEAST_S) {
                EdgeEffectCompat(context)
            } else {
                StretchEdgeEffect(context).apply {
                    setPostInvalidateOnAnimation(view::postInvalidateOnAnimation)
                }
            }
    }
}
