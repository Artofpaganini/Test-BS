package com.onexui.bottomsheet.state

/** Команда FIFO-канала жестов: Drag (живой сдвиг высоы) или Settle (довод к якорю по скорости отпускания). */
internal sealed interface GestureCommand {
    data class Drag(val deltaHeightPx: Float) : GestureCommand
    data class Settle(val velocity: Float) : GestureCommand
}
