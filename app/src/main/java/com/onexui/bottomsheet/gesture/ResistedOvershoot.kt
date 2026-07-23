package com.onexui.bottomsheet.gesture

import kotlin.math.exp

internal fun resistedOvershoot(accumulatedOvershootPx: Float, maxOvershootPx: Float): Float = when {
    maxOvershootPx <= 0f -> 0f
    else -> maxOvershootPx * (1f - exp(-accumulatedOvershootPx / maxOvershootPx))
}
