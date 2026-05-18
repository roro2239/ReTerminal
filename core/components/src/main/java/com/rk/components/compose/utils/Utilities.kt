package com.rk.components.compose.utils

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

object Utilities {
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    @JvmField
    val ATLEAST_S: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}
