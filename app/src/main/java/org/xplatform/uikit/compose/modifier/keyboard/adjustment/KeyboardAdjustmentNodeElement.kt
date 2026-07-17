package org.xplatform.uikit.compose.modifier.keyboard.adjustment

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.State
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState

@ExperimentalComposeUiApi
internal data class KeyboardAdjustmentNodeElement(
    private val shouldChangeSoftInputMode: Boolean,
    private val easing: Easing,
    private val durationMillis: Int,
    private val additionalOffsetPx: Float,
    private val keyboardState: State<KeyboardLiftState>?,
) : ModifierNodeElement<KeyboardAdjustmentNode>() {

    override fun create(): KeyboardAdjustmentNode = KeyboardAdjustmentNode(
        shouldChangeSoftInputMode = shouldChangeSoftInputMode,
        easing = easing,
        durationMillis = durationMillis,
        additionalOffsetPx = additionalOffsetPx,
        keyboardState = keyboardState,
    )

    override fun update(node: KeyboardAdjustmentNode) {
        node.update(
            newShouldChangeSoftInputMode = shouldChangeSoftInputMode,
            newEasing = easing,
            newDurationMillis = durationMillis,
            newAdditionalOffsetPx = additionalOffsetPx,
            newKeyboardState = keyboardState,
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "withAdjustmentForKeyboard"
        properties["shouldChangeSoftInputMode"] = shouldChangeSoftInputMode
        properties["easing"] = easing
        properties["durationMillis"] = durationMillis
        properties["additionalOffsetPx"] = additionalOffsetPx
    }
}
