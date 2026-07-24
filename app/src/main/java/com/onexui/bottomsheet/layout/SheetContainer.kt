package com.onexui.bottomsheet.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.onexui.bottomsheet.gesture.SheetNestedScrollConnection
import com.onexui.bottomsheet.gesture.sheetDrag
import com.onexui.bottomsheet.state.XBottomSheetState
import org.xplatform.uikit.compose.modifier.keyboard.adjustment.withAdjustmentForKeyboard
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState
import kotlin.math.roundToInt

private const val SLOT_DETECT = "SLOT_DETECT"
private const val SLOT_PLACE = "SLOT_PLACE"
private const val SLOT_ADDITIONAL_TOP = "SLOT_ADDITIONAL_TOP"

@Composable
internal fun SheetContainer(
    state: XBottomSheetState,
    insets: SheetInsets,
    isOverlayBackground: Boolean,
    shape: Shape,
    keyboardState: State<KeyboardLiftState>,
    isFullScreen: Boolean,
    isInteractionsEnabled: Boolean,
    nestedScrollConnection: SheetNestedScrollConnection,
    additionalTopFraction: Animatable<Float, AnimationVector1D>,
    additionalTopOverlap: Dp,
    additionalTopPeek: Dp,
    detectBody: @Composable () -> Unit,
    placeBody: @Composable () -> Unit,
    additionalTopBody: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shadowModifier = remember(isOverlayBackground, shape) {
        if (!isOverlayBackground) Modifier.softSheetShadow(shape) else Modifier
    }
    val keyboardAdjustmentModifier = remember(isFullScreen, keyboardState) {
        if (!isFullScreen) Modifier.withAdjustmentForKeyboard(keyboardState = keyboardState) else Modifier
    }
    SubcomposeLayout(
        modifier = modifier
            .then(shadowModifier)
            .then(keyboardAdjustmentModifier)
            .nestedScroll(nestedScrollConnection)
            .sheetDrag(state = state, isEnabled = isInteractionsEnabled),
    ) { constraints ->
        val width = constraints.maxWidth
        val ceilingPx = (insets.screenHeightPx - insets.statusBarPx).coerceAtLeast(0)
        val detectHeight = subcompose(SLOT_DETECT, detectBody).first().measure(
            Constraints(maxWidth = width, minHeight = 0, maxHeight = ceilingPx),
        ).height
        state.updateMetrics(
            screenHeightPx = insets.screenHeightPx,
            statusBarPx = insets.statusBarPx,
            contentHeightPx = detectHeight,
            loadingSheetHeightPx = insets.loadingSheetHeightPx,
        )
        val sheetHeight = state.offset.value.roundToInt().coerceIn(0, insets.screenHeightPx)
        val surfacePlaceable = subcompose(SLOT_PLACE, placeBody).first().measure(
            Constraints.fixed(width = width, height = sheetHeight),
        )
        val overlapPx = additionalTopOverlap.roundToPx()
        val additionalTopPeekPx = additionalTopPeek.roundToPx()
        val cardMeasurable = additionalTopBody?.let { subcompose(SLOT_ADDITIONAL_TOP, it).firstOrNull() }
        val cardVisibleHeight = if (cardMeasurable != null) {
            val cardNatural = cardMeasurable.maxIntrinsicHeight(width)
            val expandedVisible = (cardNatural - overlapPx).coerceAtLeast(additionalTopPeekPx)
            (additionalTopPeekPx + additionalTopFraction.value.coerceIn(0f, 1f) * (expandedVisible - additionalTopPeekPx)).roundToInt()
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

