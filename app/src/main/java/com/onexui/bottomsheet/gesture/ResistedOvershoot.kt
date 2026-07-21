package com.onexui.bottomsheet.gesture

import kotlin.math.exp

internal fun resistedOvershoot(accumulatedOvershootPx: Float, maxOvershootPx: Float): Float =
    maxOvershootPx * (1f - exp(-accumulatedOvershootPx / maxOvershootPx))
