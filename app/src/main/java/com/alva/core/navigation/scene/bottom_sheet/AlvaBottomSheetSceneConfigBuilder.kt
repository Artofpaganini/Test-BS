package com.alva.core.navigation.scene.bottom_sheet

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.alva.core.uikit.components.bottom_sheet.AlvaBottomSheetScrim
import com.alva.core.uikit.components.bottom_sheet.AlvaBottomSheetValue

@DslMarker
annotation class AlvaBottomSheetSceneConfigDsl

@AlvaBottomSheetSceneConfigDsl
class AlvaBottomSheetSceneConfigBuilder {
    var initialValue: AlvaBottomSheetValue = AlvaBottomSheetValue.PartialExpanded(DEFAULT_PARTIAL_FRACTION)
    var anchors: List<AlvaBottomSheetValue> = listOf(
        AlvaBottomSheetValue.Hidden,
        AlvaBottomSheetValue.PartialExpanded(DEFAULT_PARTIAL_FRACTION),
        AlvaBottomSheetValue.Expanded,
    )
    var animationSpec: AnimationSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow)
    var scrim: AlvaBottomSheetScrim = AlvaBottomSheetScrim.Enabled()
    var draggable: Boolean = true
    var dismissOnScrimTap: Boolean = true
    var shape: Shape? = null
    var containerColor: Color = Color.Unspecified
    var hostInWindow: Boolean = false

    fun build(): AlvaBottomSheetSceneConfig = AlvaBottomSheetSceneConfig(
        initialValue = initialValue,
        anchors = anchors,
        animationSpec = animationSpec,
        scrim = scrim,
        draggable = draggable,
        dismissOnScrimTap = dismissOnScrimTap,
        shape = shape,
        containerColor = containerColor,
        hostInWindow = hostInWindow,
    )

    private companion object {
        const val DEFAULT_PARTIAL_FRACTION = 0.5f
    }
}

inline fun alvaBottomSheetSceneConfig(
    receiver: AlvaBottomSheetSceneConfigBuilder.() -> Unit = {},
): AlvaBottomSheetSceneConfig = AlvaBottomSheetSceneConfigBuilder().apply(receiver).build()
