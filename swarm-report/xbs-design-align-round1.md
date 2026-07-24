# XBS round: align to design (Collapsed 60% · DragHandleStyle · AdditionalTopPeek)

Status: EXECUTE. Living source of truth. Three coupled changes. #2 (overshoot) is NOT in this round.

Authority: the design bundle `/Users/Victor/Downloads/figma/project/БШ - Кодовая база.dc.html`
(XBottomSheetDefaults tokens + reference Compose). Values below come from it.

## Change 1 — Collapsed height = fixed 60%, remove peekFraction entirely

Design token: `const val CollapsedFraction = 0.60f` ; `collapsedPx = screenHeightPx * 0.60`.
Current code uses a settable `peekFraction` default `2f/3f`. Make it a FIXED 60% and DELETE the peekFraction parameter (it is no longer configurable).

- [x] `SheetMetrics.kt`: remove `peekFraction: Float` constructor param. Rename `peekPx` -> `collapsedPx`, compute `(screenHeightPx * COLLAPSED_FRACTION).roundToInt().coerceIn(0, maxHeightPx)` with `private const val COLLAPSED_FRACTION = 0.60f` (companion). Update `customAnchorPx` `else -> peekPx` -> `collapsedPx`.
- [x] `SheetAnchorTargets.kt` / `SheetAnchorTableFactory.kt`: every `peekPx` -> `collapsedPx`; `contentHeightPx <= peekPx` -> `<= collapsedPx`.
- [x] `state/builder/StructureBuilder.kt`: remove `var peekFraction` and `DEFAULT_PEEK_FRACTION`.
- [x] `XBottomSheetState.kt`: remove `var peekFraction` (field + constructor param); remove it from `onLiveConfigChanged` (`current.copy(peekFraction = …)` -> drop that arg) and from any `SheetMetrics(...)` construction.
- [x] `XBottomSheetStateBuilder.kt`: drop `peekFraction = …` from `buildState()`.
- [x] `observe/ObserveSheetState.kt`: the live-reconfig watch `Triple(isSkipCollapsed, peekFraction, anchors)` -> `Pair(isSkipCollapsed, anchors)` (peekFraction can no longer change).
- [x] `XBottomSheetStateSaver.kt`: remove peekFraction from the saved `List<Any>` and from restore (drop the `readFloat` for it + the DEFAULT_PEEK_FRACTION const if unused). Keep list order consistent save<->restore.
- [x] `demo/XbsDemoScreen.kt`: remove `state.peekFraction = …` usages (case W "live peekFraction + anchors" -> keep only the anchors live-tuning; drop the peek toggle + its constants if now unused).

NOTE anchors builder default fallback in Saver `anchorFromTag` used `DEFAULT_PEEK_FRACTION` — replace that fallback with a literal `0.60f`-based default or `AnchorState.FullScreen` (keep behavior sane); don't reintroduce a peek concept.

## Change 2 — DragHandleStyle: Static fixed, Theme color-configurable

Invert the current shape.
```kotlin
sealed interface DragHandleStyle {
    data class Theme(val color: Color = Color.Unspecified) : DragHandleStyle  // Unspecified -> theme separator; overridable
    data object Static : DragHandleStyle                                      // baked White @ 0.40, NOT configurable
}
```
- [x] `handle/DragHandleStyle.kt`: as above.
- [x] `handle/DragHandle.kt`: Theme -> `style.color.takeOrElse { <theme separator color, current resolveHandleTheme> }`; Static -> fixed `Color.White.copy(alpha = 0.40f)` (named const, no param).
- [x] `style/XBottomSheetStyle.kt`: `defaultXBottomSheetStyle()` `dragHandleStyle = DragHandleStyle.Theme` -> `DragHandleStyle.Theme()`.
- [x] `XBottomSheetStateSaver.kt`: `dragHandleTag` / `dragHandleFromTag` — encode Theme with its color (`"theme:<colorLong>"`, and plain `"theme"` when Unspecified), `Static` -> `"static"` (no color), `null` -> `"none"`. Parse back.
- [x] `demo/XbsDemoScreen.kt`: `DragHandleStyle.Static(…)` -> `DragHandleStyle.Static`; `DragHandleStyle.Theme` -> `DragHandleStyle.Theme()` where used.

## Change 3 — AdditionalTopPeek: configurable strip in Collapsed (default 20.dp, clamp 0..20)

Design token `AdditionalTopPeek = 20.dp`. In Collapsed the Additional Top layer stays partly visible by `peek`; content fades to alpha 0. `0.dp` -> fully hidden in Collapsed; `> 20.dp` -> clamp to 20.

- [x] `style/AdditionalTopStyle.kt`: add `val peek: Dp` to `AdditionalTopStyle`; `AdditionalTopStyleBuilder` gets `var peek: Dp = current.peek`, and `build()` clamps `peek = peek.coerceIn(0.dp, MAX_ADDITIONAL_TOP_PEEK)` with `MAX_ADDITIONAL_TOP_PEEK = 20.dp`. Default in `defaultXBottomSheetStyle` additionalTop: `peek = 20.dp`.
- [x] `layout/SheetContainer.kt`: it currently computes
  `cardVisibleHeight = fraction * (cardNatural - overlapPx)` (Collapsed fraction=0 -> 0 -> fully hidden).
  Change so Collapsed leaves `peekPx` visible:
  ```
  val peekPx = additionalTopPeek.roundToPx()
  val expandedVisible = (cardNatural - overlapPx).coerceAtLeast(peekPx)
  val cardVisibleHeight = (peekPx + additionalTopFraction.value.coerceIn(0f,1f) * (expandedVisible - peekPx)).roundToInt()
  ```
  Card still measured at `cardVisibleHeight + overlapPx`. Pass a new `additionalTopPeek: Dp` param into SheetContainer.
- [x] `XBottomSheet.kt`: pass `additionalTopPeek = state.style.additionalTop.peek` into `SheetContainer`. The content-fade: wrap the user's additionalTop card content so it fades with the fraction (`Modifier.graphicsLayer { alpha = additionalTopFraction.value }` on the inner content), keeping the background/shape box opaque so the `peek` strip shows the background. Do NOT fade the background box itself.
- [x] With `peek = 0.dp`, Collapsed visible height = 0 (fully hidden) — verify the formula yields that.

## Invariants (hard)
- No `!!`, no `Any` in new code (Saver `List<Any>` save-type is the existing framework contract — allowed). No magic numbers (name constants: COLLAPSED_FRACTION, MAX_ADDITIONAL_TOP_PEEK, STATIC_HANDLE_ALPHA, etc.). Named lambda params only.
- No comments/KDoc unless already present; don't touch existing (even typo'd) comments — EXCEPT update any comment my change makes factually wrong.
- Do NOT delete files. Do NOT git commit/push. `internal` by default.
- ast-index for search. MUST end compile-green: `./gradlew :app:compileDebugKotlin`.
- Preserve all gesture/settle/IME/reconciliation logic. Do NOT touch overshoot (change #2 is a separate future round).

## Checklist
- [x] Collapsed fixed 60%, peekFraction fully removed (build/runtime/saver/observe/demo)
- [x] DragHandleStyle: Theme(color) configurable, Static fixed object
- [x] AdditionalTopPeek configurable (default 20, clamp 0..20), Collapsed strip + content alpha fade
- [x] compileDebugKotlin GREEN
- [x] Report orphans (do not delete)
