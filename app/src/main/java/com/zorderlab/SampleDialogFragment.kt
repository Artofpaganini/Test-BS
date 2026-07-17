package com.zorderlab

import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment

private const val ZORDER_TAG = "ZORDER"
private const val LAYER_NAME = "DIALOG-FRAGMENT"
private val PANEL_BACKGROUND = Color.parseColor("#EF6C00")

// Классический мир View: DialogFragment создаёт свой Dialog → отдельное top-level Window поверх окна
// Activity. Крупная метка «DIALOG-FRAGMENT (View)» + контрастный фон для различения слоя на скриншоте.
// Показ/скрытие слоя логируется в ZORDER.
class SampleDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = requireContext()
        return FrameLayout(context).apply {
            setBackgroundColor(PANEL_BACKGROUND)
            val pad = dp(48)
            setPadding(pad, pad, pad, pad)
            addView(
                TextView(context).apply {
                    text = "DIALOG-FRAGMENT (View)"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { gravity = Gravity.CENTER }
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(ZORDER_TAG, "$LAYER_NAME shown")
    }

    override fun onDismiss(dialog: DialogInterface) {
        Log.d(ZORDER_TAG, "$LAYER_NAME hidden")
        super.onDismiss(dialog)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
