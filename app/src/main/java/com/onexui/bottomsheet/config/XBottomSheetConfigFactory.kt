package com.onexui.bottomsheet.config

internal fun xBottomSheetConfig(configure: XBottomSheetConfigBuilder.() -> Unit = {}): XBottomSheetConfig =
    XBottomSheetConfigBuilder().apply(configure).build()
