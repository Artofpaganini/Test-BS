package com.alva.core.uikit.utils

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Modifier.noRippleEffectClick(isEnable: Boolean = true, onClick: () -> Unit): Modifier =
    this.then(
        if (isEnable) {
            Modifier.clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick::invoke,
            )
        } else {
            Modifier
        },
    )
