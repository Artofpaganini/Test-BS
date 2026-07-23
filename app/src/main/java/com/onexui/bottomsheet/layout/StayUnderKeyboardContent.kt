package com.onexui.bottomsheet.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState

@Composable
internal fun StayUnderKeyboardContent(
    modifier: Modifier,
    keyboardState: State<KeyboardLiftState>,
    navBarPx: Int,
    middle: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    val navBarState = rememberUpdatedState(navBarPx)
    val measurePolicy = remember { StayUnderKeyboardMeasurePolicy(keyboardState, navBarState) }
    Layout(
        modifier = modifier,
        content = {
            Box(modifier = Modifier.fillMaxWidth()) { middle() }
            Box(modifier = Modifier.fillMaxWidth()) { bottom() }
        },
        measurePolicy = measurePolicy,
    )
}
