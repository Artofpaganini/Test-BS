package com.zorderlab

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView

private const val ZORDER_TAG = "ZORDER"
private const val BUTTON3_DELAY_MS = 5000L

class MainActivity : AppCompatActivity(), ZOrderHost {

    // Какой лаб-лист показан прямо сейчас (управляется напрямую, без nav3): ACTIVITY — фиолетовый, WINDOW —
    // красный. Поле поднято в Activity — переживает рекомпозицию и доступно из ZOrderHost-методов (зовутся из
    // HostFragment в не-composable контексте). mutableStateOf → Compose-слой реагирует на смену.
    private var activeSheet by mutableStateOf<LabSheetKind?>(null)

    // Таймер сценария кнопки 3 живёт в Activity (owner = тот, кто заводит отложенный пинок фрагмента).
    private val button3Handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<ComposeView>(R.id.compose_container).setContent {
            LabTheme {
                LabRoot(activeSheet = activeSheet, onSheetClosed = { activeSheet = null })
            }
        }
    }

    override fun openActivitySheet() {
        if (activeSheet == null) {
            activeSheet = LabSheetKind.ACTIVITY
            Log.d(ZORDER_TAG, "XBS activity sheet requested")
        }
    }

    override fun openSheetThenRaiseDialog() {
        openActivitySheet()
        button3Handler.postDelayed({ raiseDialogOverSheetViaFragment() }, BUTTON3_DELAY_MS)
    }

    override fun openWindowSheet() {
        if (activeSheet == null) {
            activeSheet = LabSheetKind.WINDOW
            Log.d(ZORDER_TAG, "XBS window sheet requested")
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
