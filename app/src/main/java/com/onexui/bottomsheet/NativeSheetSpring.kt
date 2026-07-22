package com.onexui.bottomsheet

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/** Единая пружина всех анимаций высоы листа: без отскока, средне-мягкая жёсткость (нативное ощущение). */
internal val NativeSheetSpring: AnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
