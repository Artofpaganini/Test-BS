package com.onexui.bottomsheet.anchor

sealed interface AnchorState {
    val fraction: Float?

    data class Fraction(val value: Float) : AnchorState {
        override val fraction: Float = value
    }

    data object FullScreen : AnchorState {
        override val fraction: Float? = null
    }

    data object WrapContent : AnchorState {
        override val fraction: Float? = null
    }
}
