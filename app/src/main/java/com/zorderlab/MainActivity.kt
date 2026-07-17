package com.zorderlab

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.ComposeView
import androidx.navigation3.runtime.NavKey

private const val ZORDER_TAG = "ZORDER"
private const val BUTTON3_DELAY_MS = 5000L

class MainActivity : AppCompatActivity(), ZOrderHost {

    // Бэкстек nav3 поднят в поле Activity: переживает рекомпозицию и доступен из openAlvaBottomSheet()
    // (вызывается из HostFragment в не-composable контексте). mutableStateListOf → NavDisplay реагирует на add/remove.
    private val backStack = mutableStateListOf<NavKey>(BaseRoute)

    // Таймер сценария кнопки 3 живёт в Activity (owner = тот, кто заводит отложенный пинок фрагмента).
    private val button3Handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<ComposeView>(R.id.compose_container).setContent {
            LabTheme {
                LabRoot(backStack = backStack)
            }
        }
    }

    override fun openAlvaBottomSheet() {
        if (backStack.lastOrNull() !is Nav3SheetRoute) {
            backStack.add(Nav3SheetRoute)
            Log.d(ZORDER_TAG, "NAV3 sheet requested (backStack.add)")
        }
    }

    override fun openSheetThenRaiseDialog() {
        openAlvaBottomSheet()
        button3Handler.postDelayed({ raiseDialogOverSheetViaFragment() }, BUTTON3_DELAY_MS)
    }

    override fun openWindowHostedSheet() {
        if (backStack.lastOrNull() !is Nav3WindowSheetRoute) {
            backStack.add(Nav3WindowSheetRoute)
            Log.d(ZORDER_TAG, "NAV3 window-hosted sheet requested (backStack.add)")
        }
    }

    private fun raiseDialogOverSheetViaFragment() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        (fragment as? SheetOverlayDialogRaiser)?.raiseDialogOverSheet()
    }

    override fun onDestroy() {
        button3Handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
