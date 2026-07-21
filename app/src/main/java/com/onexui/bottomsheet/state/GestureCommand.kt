package com.onexui.bottomsheet.state

internal sealed interface GestureCommand {
    data class Drag(val deltaHeightPx: Float) : GestureCommand
    data class Settle(val velocity: Float) : GestureCommand
}
