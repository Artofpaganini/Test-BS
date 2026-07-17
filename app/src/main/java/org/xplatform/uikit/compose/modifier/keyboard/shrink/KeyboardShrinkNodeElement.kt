package org.xplatform.uikit.compose.modifier.keyboard.shrink

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.State
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState

@ExperimentalComposeUiApi
internal data class KeyboardShrinkNodeElement(
    private val softInputMode: Int?,
    private val easing: Easing,
    private val durationMillis: Int,
    private val additionalShrink: Density.() -> Float,
    private val keyboardState: State<KeyboardLiftState>?,
) : ModifierNodeElement<KeyboardShrinkNode>() {

    override fun create(): KeyboardShrinkNode = KeyboardShrinkNode(
        softInputMode = softInputMode,
        easing = easing,
        durationMillis = durationMillis,
        additionalShrink = additionalShrink,
        keyboardState = keyboardState,
    )

    override fun update(node: KeyboardShrinkNode) {
        node.update(
            newSoftInputMode = softInputMode,
            newEasing = easing,
            newDurationMillis = durationMillis,
            newAdditionalShrink = additionalShrink,
            newKeyboardState = keyboardState,
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "withKeyboardShrink"
        properties["softInputMode"] = softInputMode
        properties["easing"] = easing
        properties["durationMillis"] = durationMillis
    }
}
