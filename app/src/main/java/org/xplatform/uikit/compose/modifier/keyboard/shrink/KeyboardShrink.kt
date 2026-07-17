package org.xplatform.uikit.compose.modifier.keyboard.shrink

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.State
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState

private const val DEFAULT_DURATION_MILLIS = 300

/**
 * Модификатор позволяющийх сжимать контент (к примеру скроллящийся список) по высоте на основании открытия/закрытия
 * клавиатуры  + additionalShrink(доп параметр сжатия) если нужно сжать еще чуть больше чем высота клавиатуры
 *
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.withKeyboardShrink(
    softInputMode: Int? = null,
    easing: Easing = FastOutSlowInEasing,
    durationMillis: Int = DEFAULT_DURATION_MILLIS,
    additionalShrink: Density.() -> Float = { 0f },
    keyboardState: State<KeyboardLiftState>? = null,
): Modifier = this then KeyboardShrinkNodeElement(
    softInputMode = softInputMode,
    easing = easing,
    durationMillis = durationMillis,
    additionalShrink = additionalShrink,
    keyboardState = keyboardState,
)
