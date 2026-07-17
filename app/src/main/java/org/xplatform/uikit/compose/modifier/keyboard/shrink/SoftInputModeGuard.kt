package org.xplatform.uikit.compose.modifier.keyboard.shrink

import android.view.Window
import android.view.WindowManager

internal object SoftInputModeGuard {
    private var activeCount = 0

    fun acquire(window: Window, mode: Int) {
        activeCount++
        window.setSoftInputMode(mode)
    }

    fun release(window: Window) {
        if (activeCount == 0) return
        activeCount--
        if (activeCount == 0) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        }
    }
}
