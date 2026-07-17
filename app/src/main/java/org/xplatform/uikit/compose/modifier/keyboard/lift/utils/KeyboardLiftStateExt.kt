package org.xplatform.uikit.compose.modifier.keyboard.lift.utils

import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState
import kotlin.math.roundToInt

fun KeyboardLiftState.effectiveHeight(navigationBarHeightPx: Int): Int {
    return when {
        isKeyboardVisible -> (keyboardHeight.roundToInt() - navigationBarHeightPx).coerceAtLeast(0)
        else -> 0
    }
}
