package com.alva.core.device.model

import androidx.compose.runtime.Stable

@Suppress("MagicNumber")
@Stable
enum class WindowSizeClass {
    Compact,
    Medium,
    Expanded;

    companion object {
        fun fromWidthDp(widthDp: Int): WindowSizeClass = when {
            widthDp < 600 -> Compact
            widthDp < 840 -> Medium
            else -> Expanded
        }

        fun fromHeightDp(heightDp: Int): WindowSizeClass = when {
            heightDp < 480 -> Compact
            heightDp < 900 -> Medium
            else -> Expanded
        }
    }
}
