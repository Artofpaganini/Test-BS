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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

private const val ZORDER_TAG = "ZORDER"
private const val LAYER_NAME = "BSD-FRAGMENT"
private val PANEL_BACKGROUND = Color.parseColor("#00897B")

// Классический мир View: BottomSheetDialogFragment создаёт свой Dialog → отдельное top-level Window
// поверх окна Activity. Крупная метка «BSD-FRAGMENT (View)» + контрастный фон, чтобы отличить слой на
// скриншоте наложения. Показ/скрытие слоя логируется в ZORDER.
class SampleBottomSheetDialogFragment : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = requireContext()
        return FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(320),
            )
            setBackgroundColor(PANEL_BACKGROUND)
            addView(
                TextView(context).apply {
                    text = "BSD-FRAGMENT (View)"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
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
