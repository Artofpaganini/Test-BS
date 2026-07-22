package com.onexui.bottomsheet.config

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** DSL-билдер конфига карточик Additional Top. */
@XBottomSheetDsl
internal class AdditionalTopConfigBuilder {
    var cornerRadius: Dp = 0.dp
    internal fun build(): AdditionalTopConfig = AdditionalTopConfig(cornerRadius = cornerRadius)
}
