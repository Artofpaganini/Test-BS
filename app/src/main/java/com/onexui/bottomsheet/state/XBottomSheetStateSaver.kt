package com.onexui.bottomsheet.state

import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onexui.bottomsheet.additionaltop.AdditionalTopState
import com.onexui.bottomsheet.anchor.AnchorState
import com.onexui.bottomsheet.behavior.XBottomSheetBehavior
import com.onexui.bottomsheet.config.BottomKeyboardBehavior
import com.onexui.bottomsheet.config.DismissConfig
import com.onexui.bottomsheet.config.XBottomSheetColors
import com.onexui.bottomsheet.handle.DragHandleStyle
import com.onexui.bottomsheet.style.AdditionalTopStyle
import com.onexui.bottomsheet.style.XBottomSheetStyle

internal fun xBottomSheetStateSaver(): Saver<XBottomSheetState, List<Any>> = Saver(
    save = { state -> saveState(state) },
    restore = { saved -> restoreState(saved) },
)

private fun saveState(state: XBottomSheetState): List<Any> {
    val dismiss = state.behavior.dismiss
    val style = state.style
    return listOf(
        state.isSkipCollapsed,
        state.peekFraction,
        state.anchors.map { anchor -> anchorTag(anchor) },
        dismiss.isOutsideTapEnabled,
        dismiss.isSwipeDownEnabled,
        dismiss.isBackPressEnabled,
        state.behavior.bottomBehaviorWithKeyboard.name,
        style.isOverlayBackground,
        dragHandleTag(style.dragHandleStyle),
        style.additionalTop.cornerRadius.value,
        colorToLong(style.additionalTop.backgroundColor),
        colorToLong(style.colors.scrim),
        colorToLong(style.colors.sheetBackground),
        sheetValueTag(state.currentValue),
        state.isLoading,
        state.additionalTopState.name,
    )
}

private fun restoreState(saved: List<Any>): XBottomSheetState {
    val reader = SavedStateReader(saved)
    val isSkipCollapsed = reader.readBoolean(DEFAULT_SKIP_COLLAPSED)
    val peekFraction = reader.readFloat(DEFAULT_PEEK_FRACTION)
    val anchors = reader.readStringList().map { tag -> anchorFromTag(tag) }.toSet()
    val behavior = XBottomSheetBehavior(
        dismiss = DismissConfig(
            isOutsideTapEnabled = reader.readBoolean(DEFAULT_OUTSIDE_TAP_ENABLED),
            isSwipeDownEnabled = reader.readBoolean(DEFAULT_SWIPE_DOWN_ENABLED),
            isBackPressEnabled = reader.readBoolean(DEFAULT_BACK_PRESS_ENABLED),
        ),
        bottomBehaviorWithKeyboard = keyboardBehaviorFromName(reader.readString(BottomKeyboardBehavior.Lift.name)),
    )
    val style = XBottomSheetStyle(
        isOverlayBackground = reader.readBoolean(DEFAULT_OVERLAY_BACKGROUND),
        dragHandleStyle = dragHandleFromTag(reader.readString(DRAG_HANDLE_THEME_TAG)),
        additionalTop = AdditionalTopStyle(
            cornerRadius = reader.readFloat(DEFAULT_CORNER_RADIUS_DP).dp,
            backgroundColor = colorFromLong(reader.readLong(colorToLong(Color.Unspecified))),
        ),
        colors = XBottomSheetColors(
            scrim = colorFromLong(reader.readLong(colorToLong(Color.Unspecified))),
            sheetBackground = colorFromLong(reader.readLong(colorToLong(Color.Unspecified))),
        ),
    )
    val currentValue = sheetValueFromTag(reader.readString(SHEET_VALUE_HIDDEN_TAG))
    val isLoading = reader.readBoolean(DEFAULT_LOADING)
    val additionalTopState = additionalTopFromName(reader.readString(AdditionalTopState.Expanded.name))
    return XBottomSheetState(
        isSkipCollapsed = isSkipCollapsed,
        isInitialLoading = isLoading,
        peekFraction = peekFraction,
        anchors = anchors,
        behavior = behavior,
        style = style,
    ).apply {
        restore(value = currentValue, isLoadingSaved = isLoading, additionalTop = additionalTopState)
    }
}

private class SavedStateReader(private val values: List<*>) {
    private var index = 0

    fun readBoolean(default: Boolean): Boolean = (values.getOrNull(index++) as? Boolean) ?: default

    fun readFloat(default: Float): Float = (values.getOrNull(index++) as? Float) ?: default

    fun readLong(default: Long): Long = (values.getOrNull(index++) as? Long) ?: default

    fun readString(default: String): String = (values.getOrNull(index++) as? String) ?: default

    fun readStringList(): List<String> =
        (values.getOrNull(index++) as? List<*>)?.mapNotNull { item -> item as? String } ?: emptyList()
}

private fun colorToLong(color: Color): Long = color.value.toLong()

private fun colorFromLong(value: Long): Color = Color(value.toULong())

private fun keyboardBehaviorFromName(name: String): BottomKeyboardBehavior =
    BottomKeyboardBehavior.entries.firstOrNull { entry -> entry.name == name } ?: BottomKeyboardBehavior.Lift

private fun additionalTopFromName(name: String): AdditionalTopState =
    AdditionalTopState.entries.firstOrNull { entry -> entry.name == name } ?: AdditionalTopState.Expanded

private fun anchorTag(anchor: AnchorState): String = when (anchor) {
    is AnchorState.Fraction -> "$ANCHOR_FRACTION_PREFIX${anchor.value}"
    AnchorState.FullScreen -> ANCHOR_FULL_SCREEN_TAG
    AnchorState.WrapContent -> ANCHOR_WRAP_CONTENT_TAG
}

private fun anchorFromTag(tag: String): AnchorState = when {
    tag.startsWith(ANCHOR_FRACTION_PREFIX) ->
        AnchorState.Fraction(tag.removePrefix(ANCHOR_FRACTION_PREFIX).toFloatOrNull() ?: DEFAULT_PEEK_FRACTION)
    tag == ANCHOR_FULL_SCREEN_TAG -> AnchorState.FullScreen
    tag == ANCHOR_WRAP_CONTENT_TAG -> AnchorState.WrapContent
    else -> AnchorState.FullScreen
}

private fun dragHandleTag(style: DragHandleStyle?): String = when (style) {
    DragHandleStyle.Theme -> DRAG_HANDLE_THEME_TAG
    is DragHandleStyle.Static -> "$DRAG_HANDLE_STATIC_PREFIX${colorToLong(style.color)}"
    null -> DRAG_HANDLE_NONE_TAG
}

private fun dragHandleFromTag(tag: String): DragHandleStyle? = when {
    tag == DRAG_HANDLE_THEME_TAG -> DragHandleStyle.Theme
    tag.startsWith(DRAG_HANDLE_STATIC_PREFIX) ->
        DragHandleStyle.Static(
            colorFromLong(tag.removePrefix(DRAG_HANDLE_STATIC_PREFIX).toLongOrNull() ?: colorToLong(Color.Unspecified)),
        )
    tag == DRAG_HANDLE_NONE_TAG -> null
    else -> DragHandleStyle.Theme
}

private fun sheetValueTag(value: SheetValue): String = when (value) {
    SheetValue.Hidden -> SHEET_VALUE_HIDDEN_TAG
    SheetValue.Content -> "c"
    SheetValue.Collapsed -> "col"
    SheetValue.ExpandedContent -> "ec"
    SheetValue.ExpandedFullScreen -> "efs"
    SheetValue.Loading -> "l"
    is SheetValue.Custom -> "$SHEET_VALUE_CUSTOM_PREFIX${anchorTag(value.anchor)}"
}

private fun sheetValueFromTag(tag: String): SheetValue = when {
    tag == SHEET_VALUE_HIDDEN_TAG -> SheetValue.Hidden
    tag == "c" -> SheetValue.Content
    tag == "col" -> SheetValue.Collapsed
    tag == "ec" -> SheetValue.ExpandedContent
    tag == "efs" -> SheetValue.ExpandedFullScreen
    tag == "l" -> SheetValue.Loading
    tag.startsWith(SHEET_VALUE_CUSTOM_PREFIX) -> SheetValue.Custom(anchorFromTag(tag.removePrefix(SHEET_VALUE_CUSTOM_PREFIX)))
    else -> SheetValue.Hidden
}

private const val DEFAULT_SKIP_COLLAPSED = false
private const val DEFAULT_OUTSIDE_TAP_ENABLED = true
private const val DEFAULT_SWIPE_DOWN_ENABLED = true
private const val DEFAULT_BACK_PRESS_ENABLED = false
private const val DEFAULT_OVERLAY_BACKGROUND = true
private const val DEFAULT_LOADING = false
private const val DEFAULT_PEEK_FRACTION = 2f / 3f
private const val DEFAULT_CORNER_RADIUS_DP = 0f

private const val ANCHOR_FRACTION_PREFIX = "f:"
private const val ANCHOR_FULL_SCREEN_TAG = "fs"
private const val ANCHOR_WRAP_CONTENT_TAG = "wc"
private const val DRAG_HANDLE_THEME_TAG = "theme"
private const val DRAG_HANDLE_STATIC_PREFIX = "static:"
private const val DRAG_HANDLE_NONE_TAG = "none"
private const val SHEET_VALUE_HIDDEN_TAG = "h"
private const val SHEET_VALUE_CUSTOM_PREFIX = "cu:"
