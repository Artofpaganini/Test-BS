package com.onexui.bottomsheet.observe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import com.onexui.bottomsheet.state.XBottomSheetState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState

@Composable
internal fun ObserveSheetState(
    state: XBottomSheetState,
    keyboardState: State<KeyboardLiftState>,
    onSheetHidden: () -> Unit,
) {
    LaunchedEffect(state, keyboardState) {
        launch {
            snapshotFlow { state.metrics?.contentHeightPx }
                .filterNotNull()
                .collect { launch { state.onContentRemeasured() } }
        }
        launch {
            snapshotFlow { keyboardState.value to state.isLoading }
                .distinctUntilChanged()
                .collect { (lift, _) ->
                    if (lift.isKeyboardVisible) state.onImeShown() else state.onImeHidden()
                }
        }
        launch {
            snapshotFlow { state.isVisible }
                .drop(1)
                .collect { visible -> if (!visible) onSheetHidden() }
        }
        launch {
            snapshotFlow { Triple(state.skipCollapsed, state.peekFraction, state.anchors) }
                .drop(1)
                .distinctUntilChanged()
                .collect { launch { state.onLiveConfigChanged() } }
        }
    }
}
