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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import com.onexui.bottomsheet.XBottomSheetDefaults
import com.onexui.bottomsheet.additionaltop.AdditionalTopConfig
import com.onexui.bottomsheet.config.BottomKeyboardBehavior
import com.onexui.bottomsheet.gesture.SheetNestedScrollConnection
import com.onexui.bottomsheet.gesture.sheetDrag
import com.onexui.bottomsheet.handle.DragHandleStyle
import com.onexui.bottomsheet.state.XBottomSheetState
import org.xplatform.uikit.compose.modifier.keyboard.adjustment.withAdjustmentForKeyboard
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState
import kotlin.math.roundToInt

// Контейнер листа: SubcomposeLayout мерит тело ДВАЖДЫ (detect/place, см. ниже). withAdjustmentForKeyboard
// (подъём над IME) — только вне FullScreen: в FullScreen лист у потолка, там Middle сжимается вместо подъёма.
@Composable
internal fun SheetContainer(
    state: XBottomSheetState,
    insets: SheetInsets,
    overlayBackground: Boolean,
    dragHandle: DragHandleStyle?,
    interactionsEnabled: Boolean,
    nestedScrollConnection: SheetNestedScrollConnection,
    keyboardState: State<KeyboardLiftState>,
    isFullScreen: Boolean,
    bottomKeyboardBehavior: BottomKeyboardBehavior,
    additionalTopFraction: Animatable<Float, AnimationVector1D>,
    additionalTopConfig: AdditionalTopConfig,
    additionalTop: (@Composable () -> Unit)?,
    top: (@Composable () -> Unit)?,
    bottom: (@Composable () -> Unit)?,
    middle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ceilingPx = (insets.screenHeightPx - insets.statusBarPx).coerceAtLeast(0)
    val shadowModifier = if (!overlayBackground) Modifier.softSheetShadow(XBottomSheetDefaults.Shape) else Modifier
    val keyboardAdjustmentModifier = if (!isFullScreen) {
        Modifier.withAdjustmentForKeyboard(keyboardState = keyboardState)
    } else {
        Modifier
    }
    // fillHeight: detect wrap'ит тело (замер контента); place заполняет offset (фон на всю высоту листа).
    @Composable
    fun sheetBodySlot(fillHeight: Boolean) {
        SheetBody(
            state = state,
            dragHandle = dragHandle,
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
    // Слот-лямбды detect/place — в композиции (не в measure): стабильная идентичность контента между
    // layout-пассами → SubcomposeLayout не пере-сетит контент и не аллоцирует лямбды на кадр драга.
    val detectBody: @Composable () -> Unit = { sheetBodySlot(fillHeight = false) }
    val placeBody: @Composable () -> Unit = { sheetBodySlot(fillHeight = true) }
    // Карточка Additional Top — отдельный слой НАД листом (свой слот), клип верхних углов + натуральная высота.
    // Отдельно, чтобы её протрузия не входила в детект/высоту тела и анимация фракции не дёргала контент листа.
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
    // Тело композируется один раз, меряется дважды: (1) detect при ограниченной maxHeight → contentHeightPx
    // (короткий wrap < max, ленивый fill == max → режим wrap/fill); (2) place при fixed(offset) → реальная
    // высота. Карточка Additional Top — третий слот, вне detect (её протрузия не гонит offset за фракцией).
    SubcomposeLayout(
        modifier = modifier
            .then(shadowModifier)
            .then(keyboardAdjustmentModifier)
            .nestedScroll(nestedScrollConnection)
            .sheetDrag(state = state, enabled = interactionsEnabled),
    ) { constraints ->
        val width = constraints.maxWidth
        // Один Measurable нельзя мерить дважды за пасс → два слота (detect невидимый при maxHeight, place при fixed(offset)).
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
        // Карточка — протрузия НАД листом. surface всегда высотой sheetHeight → контент неподвижен; видимая
        // высота карточки = fraction × (natural − overlap), fraction читается ЗДЕСЬ, в measure (deferred,
        // синхронно с sheetHeight, без догоняющей пружины). Карточка кладётся раньше surface → surface поверх
        // (её низ overlap утоплен). Лист bottom-aligned → карточка растёт вверх.
        val overlapPx = XBottomSheetDefaults.AdditionalTopOverlap.roundToPx()
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
