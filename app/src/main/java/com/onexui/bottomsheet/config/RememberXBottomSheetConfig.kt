package com.onexui.bottomsheet.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal inline fun rememberXBottomSheetConfig(
    crossinline configure: XBottomSheetConfigBuilder.() -> Unit = {},
): XBottomSheetConfig = remember { XBottomSheetConfigBuilder().apply(configure).build() }
