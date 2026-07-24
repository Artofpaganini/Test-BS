package com.onexui.bottomsheet

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import com.onexui.bottomsheet.additionaltop.AdditionalTopState
import com.onexui.bottomsheet.config.BottomKeyboardBehavior
import com.onexui.bottomsheet.config.XBottomSheetConfig
import com.onexui.bottomsheet.config.XBottomSheetConfigDefault
import com.onexui.bottomsheet.config.resolveHandleStatic
import com.onexui.bottomsheet.config.resolveHandleTheme
import com.onexui.bottomsheet.config.resolveScrim
import com.onexui.bottomsheet.config.resolveSheetBackground
import com.onexui.bottomsheet.gesture.SheetNestedScrollConnection
import com.onexui.bottomsheet.layout.SheetBody
import com.onexui.bottomsheet.layout.SheetContainer
import com.onexui.bottomsheet.layout.SheetScrim
import com.onexui.bottomsheet.layout.rememberSheetDimensions
import com.onexui.bottomsheet.observe.ObserveSheetState
import com.onexui.bottomsheet.presets.PresetLoader
import com.onexui.bottomsheet.state.XBottomSheetState
import kotlin.coroutines.cancellation.CancellationException
import org.xplatform.uikit.compose.modifier.keyboard.lift.rememberKeyboardLiftState

@Composable
internal fun XBottomSheet(
    state: XBottomSheetState,
    onDismissRequest: suspend () -> Unit,
    modifier: Modifier = Modifier,
    config: XBottomSheetConfig = XBottomSheetConfigDefault,
    additionalTop: (@Composable XBottomSheetScope.() -> Unit)? = null,
    top: (@Composable XBottomSheetScope.() -> Unit)? = null,
    bottom: (@Composable XBottomSheetScope.() -> Unit)? = null,
    middle: @Composable XBottomSheetScope.() -> Unit,
) {
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val containerSize = LocalWindowInfo.current.containerSize
    val dismissScope = rememberCoroutineScope()
    val navBarPx = WindowInsets.navigationBars.getBottom(density)
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    val onDismiss = rememberUpdatedState(onDismissRequest)
    val keyboardState = rememberKeyboardLiftState()
    val sheetScope = remember(state) { XBottomSheetScopeImpl(state, config.loadingSheetHeight) }
    val dimensions = rememberSheetDimensions(
        density = density,
        containerSize = containerSize,
        statusBarPx = statusBarPx,
        navBarPx = navBarPx,
        loadingSheetHeight = config.loadingSheetHeight,
        scrimFadeDistance = config.scrimFadeDistance,
        predictiveBackMaxShift = config.predictiveBackMaxShift,
        wideScreenThreshold = config.wideScreenThreshold,
    )
    val isInteractionsEnable = remember(config.dragHandle) {
        derivedStateOf { config.dragHandle != null && !state.isLoading && !keyboardState.value.isKeyboardVisible }
    }
    val additionalTopFraction = remember(state) {
        Animatable(if (state.additionalTopState == AdditionalTopState.Expanded) 1f else 0f)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.additionalTopState }
            .collect { additionalTopState ->
                additionalTopFraction.animateTo(
                    targetValue = if (additionalTopState == AdditionalTopState.Expanded) 1f else 0f,
                    animationSpec = NativeSheetSpring,
                )
            }
    }

    ObserveSheetState(
        state = state,
        keyboardState = keyboardState,
        onSheetHidden = { sheetScope.hideKeyboard() },
    )
    val backShift = remember { Animatable(0f) }
    PredictiveBackHandler(enabled = state.isVisible && config.dismiss.isBackPressEnabled) { progress ->
        try {
            progress.collect { backEvent ->
                backShift.snapTo(backEvent.progress * dimensions.predictiveBackMaxShiftPx)
            }
            state.requestDismiss()
            backShift.snapTo(0f)
        } catch (exception: CancellationException) {
            backShift.animateTo(0f, NativeSheetSpring)
            throw exception
        }
    }
    LaunchedEffect(state,  containerSize.height, statusBarPx) { state.snapToCurrentAnchor() }

    SideEffect {
        state.updateDismissOnSwipeDown(config.dismiss.isSwipeDownEnabled)
        state.updateFlingVelocityThreshold(config.flingVelocityThresholdPxPerSec)
        state.updateResistanceMax(config.resistanceMaxPx)
        state.updateDismissScope(dismissScope)
        state.updateDismissRequest(onDismiss)
        state.updateKeyboardState(keyboardState)
        state.updateAlwaysFullScreenOnIme(
            config.keyboard.bottomBehavior == BottomKeyboardBehavior.StayUnderKeyboard && bottom != null,
        )
        sheetScope.updateKeyboardController(keyboardController)
        sheetScope.updateFocusManager(focusManager)
    }
    LaunchedEffect(state) { state.processGestures() }

    val nestedScrollConnection = remember(state) {
        SheetNestedScrollConnection(state = state, isEnabledState = isInteractionsEnable)
    }
    val sheetBody: @Composable (Boolean) -> Unit = { isFillHeight ->
        SheetBody(
            dragHandle = config.dragHandle,
            shape = config.shape,
            sheetBackgroundColor = config.colors.resolveSheetBackground(),
            handleThemeColor = config.colors.resolveHandleTheme(),
            handleStaticColor = config.colors.resolveHandleStatic(),
            dragHandleTopPadding = config.dragHandleTopPadding,
            dragHandleSize = config.dragHandleSize,
            keyboardState = keyboardState,
            isFullScreen = state.isFullScreen,
            bottomKeyboardBehavior = config.keyboard.bottomBehavior,
            navBarPx = navBarPx,
            isFillHeight = isFillHeight,
            top = top?.let { slot -> { sheetScope.slot() } },
            bottom = bottom?.let { slot -> { sheetScope.slot() } },
            middle = { if (state.isLoading) sheetScope.PresetLoader() else sheetScope.middle() },
        )
    }
    val widthModifier = remember(dimensions.isWideScreen, config.maxWidth) {
        if (dimensions.isWideScreen) Modifier.width(config.maxWidth) else Modifier.fillMaxWidth()
    }
    val detectBody: @Composable () -> Unit = { sheetBody(false) }
    val placeBody: @Composable () -> Unit = { sheetBody(true) }
    val additionalTopBody: (@Composable () -> Unit)? = additionalTop?.let { card ->
        {
            Box(
                modifier = Modifier.clip(
                    RoundedCornerShape(
                        topStart = config.additionalTop.cornerRadius,
                        topEnd = config.additionalTop.cornerRadius,
                    ),
                ),
            ) {
                Box(modifier = Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true)) {
                    sheetScope.card()
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
    ) {
        SheetScrim(
            state = state,
            isOverlayBackground = config.isOverlayBackground,
            scrimColor = config.colors.resolveScrim(),
            isDismissOnOutsideTap = config.dismiss.isOutsideTapEnabled,
            scrimFadeDistancePx = dimensions.scrimFadeDistancePx,
            onDismissRequest = { state.requestDismiss() },
        )

        SheetContainer(
            state = state,
            insets = dimensions.insets,
            isOverlayBackground = config.isOverlayBackground,
            shape = config.shape,
            keyboardState = keyboardState,
            isFullScreen = state.isFullScreen,
            isInteractionsEnabled = isInteractionsEnable.value,
            nestedScrollConnection = nestedScrollConnection,
            additionalTopFraction = additionalTopFraction,
            additionalTopOverlap = config.additionalTopOverlap,
            detectBody = detectBody,
            placeBody = placeBody,
            additionalTopBody = additionalTopBody,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(widthModifier)
                .graphicsLayer { translationY = backShift.value },
        )
    }
}
