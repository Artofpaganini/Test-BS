package com.alva.core.navigation.scene.bottom_sheet

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.alva.core.uikit.components.bottom_sheet.AlvaBottomSheetScrim
import com.alva.core.uikit.components.bottom_sheet.AlvaBottomSheetValue

@Stable
class AlvaBottomSheetSceneConfig internal constructor(
    val initialValue: AlvaBottomSheetValue,
    val anchors: List<AlvaBottomSheetValue>,
    val animationSpec: AnimationSpec<Float>,
    val scrim: AlvaBottomSheetScrim,
    val draggable: Boolean,
    val dismissOnScrimTap: Boolean,
    val shape: Shape?,
    val containerColor: Color,
    // Хостить chrome листа в собственном платформенном окне (compose Dialog).
    // Осмысленно только для modal (scrim Enabled): фуллскрин-окно тач-модально.
    val hostInWindow: Boolean,
)
