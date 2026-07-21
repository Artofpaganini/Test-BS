package com.onexui.bottomsheet.state

import androidx.compose.runtime.Immutable

// heightFraction — доля высоты экрана (0..1); работает в fill-режиме.
@Immutable
data class XSheetAnchor(val key: String, val heightFraction: Float)
