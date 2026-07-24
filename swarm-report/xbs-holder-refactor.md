# XBS holder refactor — unify state+config into one 3-category DSL holder

Status: EXECUTE. Living source of truth. Update checkboxes as work lands.

## Goal

Collapse the two entities (`rememberXBottomSheetState` + `rememberXBottomSheetConfig`)
into ONE stateful holder `XBottomSheetState`, created by a single DSL builder with three
namespaced categories: `structure { }`, `behavior { }`, `style { }`.

- Created once at start via 1 DSL builder.
- A defined subset of params is mutable at runtime (live-reactive).
- Holder is `rememberSaveable` (persists ONLY runtime position; rest rebuilt from lambda).
- Exposes runtime props (`progress`, `currentValue`, `isVisible`, `isFullScreen`, `isLoading`)
  and methods (`show/hide/expand/markContentReady`).
- ALL current working logic is preserved (gesture loop, settle, overshoot, IME promote,
  live re-resolve, predictive back, additionalTop, dismiss, remeasure). No behavior loss.

## CORRECTION 1 (supersedes conflicting parts below)

Two more requirements, and they OVERRIDE anything below that conflicts:

A) `behavior` becomes an immutable snapshot object symmetric with `style`:
   - `var behavior: XBottomSheetBehavior` (private set) + `fun behavior(configure: XBottomSheetBehaviorBuilder.() -> Unit)` partial-patch (seed from current).
   - Read via `sheetState.behavior.<field>`.

B) These 12 params are FIXED read-only — publicly READABLE but NOT settable (no builder setter, no runtime setter). Implement them as class-body `val`s inside the immutable snapshot they belong to (exactly like the current `XBottomSheetConfig` holds them as body vals). They are carried through partial-patch automatically because the builder never touches them.

   Inside `XBottomSheetStyle` (read via `sheetState.style.<field>`):
   - `cornerRadius = 20.dp`, `maxWidth = 512.dp`, `wideScreenThreshold = 600.dp`,
     `dragHandleSize = DpSize(36.dp, 4.dp)`, `dragHandleTopPadding = 8.dp`,
     `additionalTopOverlap = 32.dp`, `loadingSheetHeight = 192.dp`, `scrimFadeDistance = 120.dp`,
     `shape: Shape get() = RoundedCornerShape(cornerRadius, cornerRadius, 0.dp, 0.dp)` (computed from cornerRadius)

   Inside `XBottomSheetBehavior` (read via `sheetState.behavior.<field>`):
   - `flingVelocityThresholdPxPerSec = 400f`, `resistanceMaxPx = 240f`, `predictiveBackMaxShift = 48.dp`

   => `flingVelocityThresholdPxPerSec` and `resistanceMaxPx` are NO LONGER mutable builder params. Remove them from the behavior DSL. The gesture loop reads them from `behavior.<field>`.

C) DROP the `XBottomSheetDimensions` / `XBottomSheetDefaults` idea and the composable `dimensions` param entirely. The composable reads fixed visual constants from `state.style.*` and fixed motion constants from `state.behavior.*`. `rememberSheetDimensions` keeps doing Dp->px conversion but sources the Dp values from `state.style`/`state.behavior` instead of a param.

XBottomSheetBehavior snapshot shape:
```kotlin
@Immutable data class XBottomSheetBehavior(
    val dismiss: DismissConfig,                             // mutable via behavior DSL
    val bottomBehaviorWithKeyboard: BottomKeyboardBehavior, // mutable via behavior DSL
) {
    val flingVelocityThresholdPxPerSec: Float = 400f        // fixed read-only
    val resistanceMaxPx: Float = 240f                       // fixed read-only
    val predictiveBackMaxShift: Dp = 48.dp                  // fixed read-only
}
class XBottomSheetBehaviorBuilder(current: XBottomSheetBehavior) {   // seed from current
    var bottomBehaviorWithKeyboard = current.bottomBehaviorWithKeyboard
    private var dismiss = current.dismiss
    fun dismiss(configure: DismissBuilder.() -> Unit) { dismiss = DismissBuilder(dismiss).apply(configure).build() }
    internal fun build() = XBottomSheetBehavior(dismiss, bottomBehaviorWithKeyboard)
}
```

## Target call-site

Create:
```kotlin
val sheetState = rememberXBottomSheetState {
    structure {
        isSkipCollapsed = true
        isInitialLoading = false
        peekFraction = 2f / 3f
        anchors { "half" at 0.5f; "mid" at 0.66f }
    }
    behavior {
        dismiss { isOutsideTapEnabled = true; isSwipeDownEnabled = true; isBackPressEnabled = false }
        bottomBehaviorWithKeyboard = BottomKeyboardBehavior.Lift
    }
    style {
        isOverlayBackground = true
        additionalTop { cornerRadius = 0.dp; backgroundColor = Color.Unspecified }
        dragHandleStyle = DragHandleStyle.Theme
        colors { scrim = Color.Unspecified; sheetBackground = Color.Unspecified }
    }
}
```

Runtime mutation (partial-patch, seeds from current snapshot):
```kotlin
sheetState.peekFraction = 1 / 2f
sheetState.anchors { "half" at 0.3f; "mid" at 0.7f }
sheetState.style {
    isOverlayBackground = false
    additionalTop { cornerRadius = if (isValue) 0.dp else 20.dp; backgroundColor = Color.Unspecified }
    dragHandleStyle = DragHandleStyle.Static(color)
    colors { scrim = Color.Unspecified; sheetBackground = Color.Unspecified }
}
```

Composable (no more `config`):
```kotlin
XBottomSheet(
    state = sheetState,
    onDismissRequest = { sheetState.hide() },
    middle = { /* content */ },
)
// fixed cosmetics are read from state.style.* / state.behavior.* — no dimensions param
```

## Holder public surface — XBottomSheetState (@Stable)

STRUCTURE (mutable, live-reactive via onLiveConfigChanged):
- `var isSkipCollapsed: Boolean`
- `var peekFraction: Float`
- `var anchors: Map<String, Float>` (private set) + `fun anchors(configure: XSheetAnchorsBuilder.() -> Unit)`
- `isInitialLoading` — seed only, NOT a mutable prop (seeds `isLoading` at t=0)

BEHAVIOR (mutable):
- `var dismiss: DismissConfig` (private set) + `fun dismiss(configure: DismissBuilder.() -> Unit)` partial-patch
- `var bottomBehaviorWithKeyboard: BottomKeyboardBehavior`
- `var flingVelocityThresholdPxPerSec: Float`
- `var resistanceMaxPx: Float`

STYLE (mutable, immutable snapshot inside):
- `var style: XBottomSheetStyle` (private set) + `fun style(configure: XBottomSheetStyleBuilder.() -> Unit)` partial-patch

RUNTIME (unchanged behavior):
- `val currentValue`, `val isVisible`, `val isFullScreen`, `val isLoading`
- `val progress: Float` — NEW: `offset.value / metrics.maxHeightPx` coerced 0..1
- `suspend fun show() / hide() / expand() / markContentReady()`

## Immutable style snapshot + partial-patch

```kotlin
@Immutable data class XBottomSheetStyle(
    val isOverlayBackground: Boolean,
    val dragHandleStyle: DragHandleStyle?,
    val additionalTop: AdditionalTopStyle,
    val colors: XBottomSheetColors,
)
@Immutable data class AdditionalTopStyle(val cornerRadius: Dp, val backgroundColor: Color)
```
Every mutating DSL builder is seeded FROM CURRENT (so partial patch keeps untouched fields):
`XBottomSheetStyleBuilder(current)`, `AdditionalTopStyleBuilder(current)`, `XBottomSheetColorsBuilder(current)`, `DismissBuilder(current)`.

## DragHandleStyle carries color (user cut)

Remove `handleTheme` / `handleStatic` from colors — handle color lives inside the style:
```kotlin
sealed interface DragHandleStyle {
    data object Theme : DragHandleStyle           // color resolved from theme internally
    data class Static(val color: Color) : DragHandleStyle
}
```
`XBottomSheetColors` slimmed to `scrim`, `sheetBackground` only.
`DragHandle.kt` rewired to read color from the `DragHandleStyle` itself (was themeColor/staticColor params).

## Composable signature

`XBottomSheet(state, onDismissRequest, modifier, additionalTop?, top?, bottom?, middle)`.
- Drop `config: XBottomSheetConfig`. Drop `dimensions` param (CORRECTION 1.C).
- Render reads `state.style.*` (visual) and `state.behavior.*` (motion, incl. fixed vals).
- `SideEffect` keeps only composition-owned wiring: `keyboardState`, `dismissScope`, `dismissRequest`,
  and `isAlwaysFullScreenOnIme = state.bottomBehaviorWithKeyboard == StayUnderKeyboard && bottom != null`.
- `XBottomSheetDimensions` (Defaults) holds fixed cosmetics: maxWidth, cornerRadius, dragHandleSize,
  dragHandleTopPadding, additionalTopOverlap, loadingSheetHeight, scrimFadeDistance, predictiveBackMaxShift, shape.

## Saver — UNCHANGED mechanism

Persist ONLY `currentValue` / `isLoading` / `additionalTopState`. structure/behavior/style rebuild from
the builder lambda on recreation; runtime mutations re-apply via recomposition (must be backed by
recomposition-observable state to survive process-death — documented property, not a bug).

## File plan

NEW:
- [x] state/builder/StructureBuilder.kt
- [x] state/builder/BehaviorBuilder.kt  (typealias -> XBottomSheetBehaviorBuilder; single DSL source, no dup)
- [x] state/builder/StyleBuilder.kt     (typealias -> XBottomSheetStyleBuilder; single DSL source, no dup)
- [x] behavior/XBottomSheetBehavior.kt  (+ XBottomSheetBehaviorBuilder + DismissBuilder + defaultXBottomSheetBehavior)
- [x] style/XBottomSheetStyle.kt        (+ XBottomSheetStyleBuilder + defaultXBottomSheetStyle — fixed visual vals in body)
- [x] style/AdditionalTopStyle.kt       (+ AdditionalTopStyleBuilder)
- [x] NOTE: NO XBottomSheetDimensions / XBottomSheetDefaults (dropped per CORRECTION 1.C)

REWRITE:
- [x] state/XBottomSheetStateBuilder.kt — 3 nested DSL (structure/behavior/style)
- [x] state/XBottomSheetState.kt — behavior+style snapshot props, `progress`, mutating DSL fns, ALL logic kept
- [x] state/RememberXBottomSheetState.kt — unchanged (verified: buildState covers all)
- [x] bottomsheet/XBottomSheet.kt — dropped `config`+`dimensions`, reads state.style/state.behavior, slim SideEffect
- [x] handle/DragHandleStyle.kt — sealed + color
- [x] handle/DragHandle.kt — read color from style
- [x] config/XBottomSheetColors.kt (+ Builder) — slim to scrim/sheetBackground, builder seeds from current
- [x] state/XBottomSheetStateSaver.kt — verified: tags unchanged, buildState-based restore still valid

ORPHANED — DO NOT DELETE (leave, report; team-lead asks user before rm):
- [ ] config/XBottomSheetConfig.kt, XBottomSheetConfigBuilder.kt, RememberXBottomSheetConfig.kt
- [ ] config/KeyboardConfig.kt (+Builder), AdditionalTopConfig.kt (+Builder)
- [ ] DismissConfig.kt — KEEP (behavior reuses); DismissConfigBuilder — replace with in-holder DismissBuilder

CALL-SITES:
- [x] demo/XbsDemoScreen.kt — all cases migrated (structure/behavior/style); broken CaseGrid pseudocode rewritten
- [x] zorderlab/WindowedXBottomSheet.kt — no config wired, no change needed (verified)

## Invariants (hard)

- No `!!`, no `Any` (generics), no magic numbers (constants), no smart-cast abuse.
- Lambdas: named params always (no implicit `it`).
- No comments / KDoc unless already present; don't touch existing typo'd comments.
- Do NOT delete any file. Do NOT run git commit/push.
- ast-index for code search. Preserve the Map<String,Float> anchors (already refactored).
- MUST end compile-green: `./gradlew :app:compileDebugKotlin`.

## Checklist

- [x] New type files created
- [x] Holder + builder rewritten, all logic preserved
- [x] Composable rewired, SideEffect slimmed
- [x] DragHandleStyle color + colors slimmed
- [x] Saver verified
- [x] Demo + Windowed call-sites migrated
- [x] compileDebugKotlin GREEN
- [x] Orphan list reported to team-lead
