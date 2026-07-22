package com.onexui.bottomsheet.gesture

import kotlin.math.exp

/** Экспоненциальное сопративление overshoot: чем дальше тянут за maxOvershoot, тем меньше реальный ход (rubber-band). */
internal fun resistedOvershoot(accumulatedOvershootPx: Float, maxOvershootPx: Float): Float =
    maxOvershootPx * (1f - exp(-accumulatedOvershootPx / maxOvershootPx))
