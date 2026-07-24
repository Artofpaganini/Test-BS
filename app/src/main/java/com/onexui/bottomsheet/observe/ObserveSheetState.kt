package com.onexui.bottomsheet.observe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SnapshotFlowManager
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.onexui.bottomsheet.state.XBottomSheetState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState

@OptIn(ExperimentalComposeRuntimeApi::class)
@Composable
internal fun ObserveSheetState(
    state: XBottomSheetState,
    keyboardState: State<KeyboardLiftState>,
    onSheetHidden: () -> Unit,
) {
    val manager = remember { SnapshotFlowManager() }

    DisposableEffect(manager) {
        onDispose { manager.dispose() }
    }

    LaunchedEffect(manager, state, keyboardState) {
        launch {
            snapshotFlow(manager) { state.metrics?.contentHeightPx }
                .filterNotNull()
                .collect { launch { state.onContentRemeasured() } }
        }
        launch {
            snapshotFlow(manager) { keyboardState.value to state.isLoading }
                .distinctUntilChanged()
                .collect { (lift, _) ->
                    if (lift.isKeyboardVisible) state.onImeShown() else state.onImeHidden()
                }
        }
        launch {
            snapshotFlow(manager) { state.isVisible }
                .drop(1)
                .collect { isSheetVisible -> if (!isSheetVisible) onSheetHidden() }
        }
        launch {
            snapshotFlow(manager) { state.isSkipCollapsed to state.anchors }
                .drop(1)
                .distinctUntilChanged()
                .collect { launch { state.onLiveConfigChanged() } }
        }
    }
}
