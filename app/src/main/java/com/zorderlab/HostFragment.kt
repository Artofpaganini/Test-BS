package com.zorderlab

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentDialog
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment

private const val BSD_TAG = "sample_bsd"
private const val DIALOG_TAG = "sample_dialog"
private const val FLOW_DELAY_MS = 5000L

// View-мир z-order-теста. Кнопки запускают cross-open флоу. Связь fragment→activity — каст requireActivity()
// к ZOrderHost (без event bus). Таймеры кнопок 1, 2 и 4 живут здесь (Handler на main looper), снимаются в
// onDestroyView. Таймер кнопки 3 — на стороне Activity (см. openSheetThenRaiseDialog). Кнопка 4 поднимает
// XBS-лист (в собственном окне) из СОБСТВЕННОГО ComposeView фрагмента, без обращения к Activity.
class HostFragment : Fragment(), SheetOverlayDialogRaiser {

    private val flowHandler = Handler(Looper.getMainLooper())

    // ComposeView c fragment-hosted листом добавляется в иерархию ТОЛЬКО на время флоу кнопки 4 и снимается
    // при закрытии листа. Так решается pass-through: пока лист закрыт, поверх кнопок нет ComposeView и клики
    // по кнопкам не перехватываются прозрачной базой.
    private var sheetComposeView: ComposeView? = null
    private lateinit var rootContainer: FrameLayout

    // Голое ComponentDialog-окно с ComposeView внутри (кнопка 8). Переживает dismiss()/show() как ОДИН
    // инстанс — на этом строится динамический цикл «кто добавлен позже в WindowManager, тот сверху».
    private var composeSheetDialog: ComponentDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = requireContext()
        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#ECEFF1"))
            val pad = dp(24)
            setPadding(pad, pad, pad, pad)
            addView(
                TextView(context).apply {
                    text = "HOST FRAGMENT (View world)"
                    setTextColor(Color.parseColor("#37474F"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    gravity = Gravity.CENTER
                },
            )
            addView(buildButton("1 · BSD-fragment → +5s XBS sheet", ::startBsdThenSheetFlow))
            addView(buildButton("2 · Dialog-fragment → +5s XBS sheet", ::startDialogThenSheetFlow))
            addView(buildButton("3 · XBS sheet → +5s BSD-fragment", ::startSheetThenBsdFlow))
            addView(buildButton("4 · BSD-FRAGMENT → +5S XBS (fragment-hosted)", ::startBsdThenFragmentHostedSheetFlow))
            addView(buildButton("5 · BSD-FRAGMENT → +5S XBS IN OWN WINDOW", ::startBsdThenWindowHostedSheetFlow))
            addView(buildButton("8 · COMPOSE-ОКНО → +5S BSD → +5S RE-RAISE", ::startDynamicWindowCycleFlow))
        }
        rootContainer = FrameLayout(context).apply {
            addView(
                buttons,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        return rootContainer
    }

    // Кнопка 1: показываем BottomSheetDialogFragment (новое окно поверх Activity), через 5с просим Activity
    // поднять XBS-лист в собственном окне — окно листа добавлено в WindowManager позже → ляжет ПОВЕРХ BSD.
    private fun startBsdThenSheetFlow() {
        SampleBottomSheetDialogFragment().show(parentFragmentManager, BSD_TAG)
        flowHandler.postDelayed({ zOrderHost().openActivitySheet() }, FLOW_DELAY_MS)
    }

    // Кнопка 2: показываем DialogFragment, через 5с просим Activity поднять XBS-лист поверх.
    private fun startDialogThenSheetFlow() {
        SampleDialogFragment().show(parentFragmentManager, DIALOG_TAG)
        flowHandler.postDelayed({ zOrderHost().openActivitySheet() }, FLOW_DELAY_MS)
    }

    // Кнопка 3: просим Activity открыть XBS-лист и через 5с пнуть нас поднять BSD поверх листа. Таймер —
    // на стороне Activity; сюда прилетит raiseDialogOverSheet() (окно BSD добавлено позже → ляжет поверх листа).
    private fun startSheetThenBsdFlow() {
        zOrderHost().openSheetThenRaiseDialog()
    }

    // Кнопка 4: показываем BSD, через 5с САМ фрагмент поднимает XBS-лист (в своём окне) из своего ComposeView,
    // без Activity. Проверяем z-order, когда windowed-лист хостит фрагмент, а не Activity.
    private fun startBsdThenFragmentHostedSheetFlow() {
        SampleBottomSheetDialogFragment().show(parentFragmentManager, BSD_TAG)
        flowHandler.postDelayed({ openFragmentHostedSheet() }, FLOW_DELAY_MS)
    }

    // Кнопка 5: показываем BSD, через 5с просим Activity поднять XBS-лист (красный) в собственном окне —
    // тот же windowed-хелпер, другой цвет. Окно листа добавлено позже окна BSD → ляжет ПОВЕРХ BSD.
    private fun startBsdThenWindowHostedSheetFlow() {
        SampleBottomSheetDialogFragment().show(parentFragmentManager, BSD_TAG)
        flowHandler.postDelayed({ zOrderHost().openWindowSheet() }, FLOW_DELAY_MS)
    }

    // Кнопка 8: динамический цикл top-level окон «кто сверху — тот, кого добавили позже».
    private fun startDynamicWindowCycleFlow() {
        val sheetDialog = obtainComposeSheetDialog()
        sheetDialog.show()
        flowHandler.postDelayed(
            { SampleBottomSheetDialogFragment().show(parentFragmentManager, BSD_TAG) },
            FLOW_DELAY_MS,
        )
        flowHandler.postDelayed(
            {
                // dismiss() = wm.removeViewImmediate(decor); show() = wm.addView(тот же decor)
                // → окно листа снова ПОСЛЕДНЕЕ в слое = поверх BSD. View не пере-inflate-ится,
                // но композиция пересоздаётся (ComponentDialog в onStop шлёт ON_DESTROY).
                composeSheetDialog?.dismiss()
                composeSheetDialog?.show()
            },
            FLOW_DELAY_MS * 2,
        )
    }

    private fun obtainComposeSheetDialog(): ComponentDialog {
        composeSheetDialog?.let { existing -> return existing }
        // Theme_Translucent_NoTitleBar: полноэкранное прозрачное окно без dim —
        // просвечивает нижележащий BSD, видно, кто кого перекрывает.
        val dialog = ComponentDialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar)
        val composeView = ComposeView(dialog.context).apply {
            setContent {
                LabTheme {
                    ResearchSheetPanel(
                        background = ComposeColor(0xFF2E7D32), // зелёный — лист в Dialog-окне
                        panelHeight = 400.dp, // ВЫШЕ 320dp BSD: BSD сверху → торчит зелёная полоса 80dp
                        label = "COMPOSE SHEET IN DIALOG WINDOW",
                        logName = "COMPOSE dialog-sheet",
                    )
                }
            }
        }
        // setContentView сам вызывает initializeViewTreeOwners() (activity 1.13.0):
        // на decor встают Lifecycle/SavedStateRegistry owner-ы (ViewModelStore не нужен).
        dialog.setContentView(composeView)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        composeSheetDialog = dialog
        return dialog
    }

    override fun raiseDialogOverSheet() {
        SampleBottomSheetDialogFragment().show(parentFragmentManager, BSD_TAG)
    }

    // Лениво добавляем ComposeView поверх кнопок; он поднимает XBS-лист в собственном окне (WindowedXBottomSheet).
    // onSheetGone снимает ComposeView, когда лист закрыт (post — чтобы удалить вью не изнутри его же композиции).
    private fun openFragmentHostedSheet() {
        if (sheetComposeView != null) return
        val composeView = ComposeView(requireContext())
        composeView.setContent {
            LabTheme {
                FragmentHostedSheetRoot(
                    onSheetGone = { composeView.post { removeSheetComposeView(composeView) } },
                )
            }
        }
        sheetComposeView = composeView
        rootContainer.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun removeSheetComposeView(view: ComposeView) {
        rootContainer.removeView(view)
        if (sheetComposeView === view) {
            sheetComposeView = null
        }
    }

    override fun onDestroyView() {
        flowHandler.removeCallbacksAndMessages(null)
        sheetComposeView = null
        composeSheetDialog?.dismiss()
        composeSheetDialog = null
        super.onDestroyView()
    }

    private fun zOrderHost(): ZOrderHost = requireActivity() as ZOrderHost

    private fun buildButton(label: String, onClick: () -> Unit): Button {
        val context = requireContext()
        return Button(context).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(16)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
