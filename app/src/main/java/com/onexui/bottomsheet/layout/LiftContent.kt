package com.onexui.bottomsheet.layout

import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState
import org.xplatform.uikit.compose.modifier.keyboard.shrink.withKeyboardShrink
import kotlin.math.roundToInt

@Composable
internal fun LiftContent(
    modifier: Modifier,
    keyboardState: State<KeyboardLiftState>,
    isFullScreen: Boolean,
    navBarPx: Int,
    fillHeight: Boolean,
    top: (@Composable () -> Unit)?,
    middle: @Composable () -> Unit,
    bottom: (@Composable () -> Unit)?,
) {
    val middleShrinkModifier = if (isFullScreen) {
        Modifier.withKeyboardShrink(
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
            keyboardState = keyboardState,
        )
    } else {
        Modifier
    }
    val bottomInsetModifier = Modifier.layout { measurable, constraints ->
        val ime = keyboardState.value
        val bottomInset = if (isFullScreen && ime.isKeyboardVisible) {
            ime.keyboardHeight.roundToInt()
        } else {
            navBarPx
        }
        val placeable = measurable.measure(
            constraints.copy(
                minHeight = (constraints.minHeight - bottomInset).coerceAtLeast(0),
                maxHeight = (constraints.maxHeight - bottomInset).coerceAtLeast(0),
            ),
        )
        layout(placeable.width, placeable.height + bottomInset) {
            placeable.place(0, 0)
        }
    }
    Column(modifier = modifier.then(bottomInsetModifier)) {
        top?.invoke()
        Box(
            modifier = (if (fillHeight) Modifier.weight(1f) else Modifier.weight(1f, fill = false))
                .fillMaxWidth()
                .then(middleShrinkModifier),
        ) {
            middle()
        }
        bottom?.invoke()
    }
}
