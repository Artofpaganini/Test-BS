package com.onexui.bottomsheet.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.onexui.bottomsheet.config.AdditionalTopConfig
import com.onexui.bottomsheet.config.BottomKeyboardBehavior
import com.onexui.bottomsheet.gesture.SheetNestedScrollConnection
import com.onexui.bottomsheet.gesture.sheetDrag
import com.onexui.bottomsheet.handle.DragHandleStyle
import com.onexui.bottomsheet.state.XBottomSheetState
import org.xplatform.uikit.compose.modifier.keyboard.adjustment.withAdjustmentForKeyboard
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState
import kotlin.math.roundToInt

@Composable
internal fun SheetContainer(
    state: XBottomSheetState,
    insets: SheetInsets,
    overlayBackground: Boolean,
    dragHandle: DragHandleStyle?,
    shape: Shape,
    sheetBackgroundColor: Color,
    handleThemeColor: Color,
    handleStaticColor: Color,
    dragHandleTopPadding: Dp,
    dragHandleSize: DpSize,
    interactionsEnabled: Boolean,
    nestedScrollConnection: SheetNestedScrollConnection,
    keyboardState: State<KeyboardLiftState>,
    isFullScreen: Boolean,
    bottomKeyboardBehavior: BottomKeyboardBehavior,
    additionalTopFraction: Animatable<Float, AnimationVector1D>,
    additionalTopConfig: AdditionalTopConfig,
    additionalTopOverlap: Dp,
    additionalTop: (@Composable () -> Unit)?,
    top: (@Composable () -> Unit)?,
    bottom: (@Composable () -> Unit)?,
    middle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ceilingPx = (insets.screenHeightPx - insets.statusBarPx).coerceAtLeast(0)
    val shadowModifier = if (!overlayBackground) Modifier.softSheetShadow(shape) else Modifier
    val keyboardAdjustmentModifier = if (!isFullScreen) {
        Modifier.withAdjustmentForKeyboard(keyboardState = keyboardState)
    } else {
        Modifier
    }
    @Composable
    fun sheetBodySlot(fillHeight: Boolean) {
        SheetBody(
            dragHandle = dragHandle,
            shape = shape,
            sheetBackgroundColor = sheetBackgroundColor,
            handleThemeColor = handleThemeColor,
            handleStaticColor = handleStaticColor,
            dragHandleTopPadding = dragHandleTopPadding,
            dragHandleSize = dragHandleSize,
            keyboardState = keyboardState,
            isFullScreen = isFullScreen,
            bottomKeyboardBehavior = bottomKeyboardBehavior,
            navBarPx = insets.navBarPx,
            fillHeight = fillHeight,
            top = top,
            bottom = bottom,
            middle = middle,
        )
    }
    val detectBody: @Composable () -> Unit = { sheetBodySlot(fillHeight = false) }
    val placeBody: @Composable () -> Unit = { sheetBodySlot(fillHeight = true) }
    val additionalTopBody: (@Composable () -> Unit)? = additionalTop?.let { card ->
        {
            Box(
                modifier = Modifier.clip(
                    RoundedCornerShape(
                        topStart = additionalTopConfig.cornerRadius,
                        topEnd = additionalTopConfig.cornerRadius,
                    ),
                ),
            ) {
                Box(modifier = Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true)) { card() }
            }
        }
    }
    SubcomposeLayout(
        modifier = modifier
            .then(shadowModifier)
            .then(keyboardAdjustmentModifier)
            .nestedScroll(nestedScrollConnection)
            .sheetDrag(state = state, enabled = interactionsEnabled),
    ) { constraints ->
        val width = constraints.maxWidth
        val detectHeight = subcompose(ContentMeasureSlot, detectBody).first().measure(
            Constraints(maxWidth = width, minHeight = 0, maxHeight = ceilingPx),
        ).height
        state.updateMetrics(
            screenHeightPx = insets.screenHeightPx,
            statusBarPx = insets.statusBarPx,
            contentHeightPx = detectHeight,
            loadingSheetHeightPx = insets.loadingSheetHeightPx,
        )
        val sheetHeight = state.offset.value.roundToInt().coerceIn(0, insets.screenHeightPx)
        val surfacePlaceable = subcompose(VisibleContentSlot, placeBody).first().measure(
            Constraints.fixed(width = width, height = sheetHeight),
        )
        val overlapPx = additionalTopOverlap.roundToPx()
        val cardMeasurable = additionalTopBody?.let { subcompose(AdditionalTopCardSlot, it).firstOrNull() }
        val cardVisibleHeight = if (cardMeasurable != null) {
            val cardNatural = cardMeasurable.maxIntrinsicHeight(width)
            (additionalTopFraction.value.coerceIn(0f, 1f) * (cardNatural - overlapPx).coerceAtLeast(0)).roundToInt()
        } else {
            0
        }
        val cardPlaceable = cardMeasurable?.measure(
            Constraints.fixed(width = width, height = cardVisibleHeight + overlapPx),
        )
        val totalHeight = sheetHeight + cardVisibleHeight
        layout(width = width, height = totalHeight) {
            cardPlaceable?.place(x = 0, y = 0)
            surfacePlaceable.place(x = 0, y = cardVisibleHeight)
        }
    }
}

private object ContentMeasureSlot
private object VisibleContentSlot
private object AdditionalTopCardSlot
