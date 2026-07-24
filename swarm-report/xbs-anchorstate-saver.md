# XBS round: typed AnchorState + full rememberSaveable

Status: EXECUTE. Living source of truth.

## Goal (two coupled changes, one round)

Q2. Replace the string-keyed custom anchors (`Map<String, Float>`, `"half" at 0.5f`) with a
typed `sealed interface AnchorState`. Anchor identity becomes intrinsic (value/marker), no magic strings.

Q3. Expand the Saver so the ENTIRE mutable holder state survives process-death natively
(not only currentValue/isLoading/additionalTop). After this, a runtime `sheetState.x = ...`
survives death without needing a VM backing.

peekFraction STAYS (drives the auto-derived Collapsed anchor; not folded into anchors).

## AnchorState

```kotlin
sealed interface AnchorState {
    val fraction: Float?                                  // null => resolved at measure time, not a screen fraction
    data class Fraction(val value: Float) : AnchorState { // % of screen height (replaces `"x" at value`)
        override val fraction: Float = value
    }
    data object FullScreen : AnchorState {                // resolves to maxHeightPx
        override val fraction: Float? = null
    }
    data object WrapContent : AnchorState {               // resolves to min(contentHeightPx, maxHeightPx)
        override val fraction: Float? = null
    }
}
```

anchors DSL uses `unaryPlus` (bare references can't self-register in Kotlin):
```kotlin
anchors {
    +Fraction(0.3f)
    +Fraction(0.8f)
    +FullScreen
}
```

XSheetAnchorsBuilder rewritten:
```kotlin
internal class XSheetAnchorsBuilder {
    private val anchors = linkedSetOf<AnchorState>()
    operator fun AnchorState.unaryPlus() { anchors.add(this) }
    internal fun build(): Set<AnchorState> = anchors.toSet()   // LinkedHashSet, dedup by identity
}
```

## Storage / type changes

- `XBottomSheetState.anchors: Map<String, Float>` -> `Set<AnchorState>` (private set + `fun anchors { }` keeps the `==` guard).
- `XBottomSheetStateBuilder` / `StructureBuilder.buildAnchors(): Set<AnchorState>`.
- `SheetMetrics.customAnchors: Set<AnchorState>`.
- `SheetValue.Custom(val key: String)` -> `SheetValue.Custom(val anchor: AnchorState)`.

## Resolution — SheetMetrics

```kotlin
fun customAnchorPx(anchor: AnchorState): Int {
    val fraction = anchor.fraction
    val px = when {
        fraction != null -> (screenHeightPx * fraction).roundToInt()
        anchor is AnchorState.FullScreen -> maxHeightPx
        anchor is AnchorState.WrapContent -> minOf(contentHeightPx, maxHeightPx)
        else -> peekPx
    }
    return px.coerceIn(0, maxHeightPx)
}
```
NOTE: the old `?: peekFraction` set-lookup fallback disappears — `SheetValue.Custom` carries its own
`AnchorState`, so `anchorPx(Custom(anchor))` resolves directly from the anchor, no set membership needed.

- `SheetAnchorTableFactory`: iterate `customAnchors.forEach { anchor -> add(AnchorEntry(SheetValue.Custom(anchor), customAnchorPx(anchor))) }`.
- `SheetAnchorTargets.anchorPx`: `is SheetValue.Custom -> customAnchorPx(value.anchor)`.
- `XBottomSheetState.resolveRestTargetAfterConfigChange`: `value is SheetValue.Custom && !anchors.contains(value.anchor)`.
- `onLiveConfigChanged`: `current.copy(customAnchors = anchors)` (type now Set<AnchorState>).

## Saver — persist FULL mutable state

`xBottomSheetStateSaver` saves a `List<Any>` covering EVERY mutable field; restore reconstructs
the whole holder (builder no longer needed for the mutable parts — pass saved values into a new
XBottomSheetState). Fixed read-only vals are constants, rebuilt by the snapshot classes.

Save set:
- structure: `isSkipCollapsed: Boolean`, `peekFraction: Float`, `anchors: List<String>` (anchor tags)
- behavior: `dismiss.isOutsideTapEnabled/isSwipeDownEnabled/isBackPressEnabled: Boolean`, `bottomBehaviorWithKeyboard: String` (enum name)
- style: `isOverlayBackground: Boolean`, `dragHandleStyle: String` ("theme" | "static:<colorLong>" | "none"),
  `additionalTop.cornerRadius: Float` (dp value), `additionalTop.backgroundColor: Long`,
  `colors.scrim: Long`, `colors.sheetBackground: Long`
- runtime (as today): `currentValue` tag, `isLoading: Boolean`, `additionalTopState.name: String`

Encodings:
- Color <-> Long: save `color.value.toLong()`, restore `Color(saved.toULong())`. `Color.Unspecified` round-trips (still means "from theme").
- Dp <-> Float: save `dp.value`, restore `value.dp`.
- AnchorState tag: `Fraction(v)` -> `"f:$v"`, `FullScreen` -> `"fs"`, `WrapContent` -> `"wc"`.
- SheetValue.Custom tag: `"cu:" + anchorTag(anchor)`; parse back into `SheetValue.Custom(anchorFromTag)`.
- All elements must be Bundle-saveable primitives (String/Boolean/Float/Long) or a nested `List<String>` (anchor tags). No custom Parcelable.

Keep a single ordered `List<Any>` with documented positions, or nest sub-lists per category — choose
whatever stays readable and round-trips. Restore must tolerate a short/old list (getOrNull + defaults).

## File plan

- [x] anchor/AnchorState.kt (new) — sealed type (public: mirrors already-public SheetValue.Custom)
- [x] state/XSheetAnchorsBuilder.kt — unaryPlus DSL, Set<AnchorState>
- [x] state/SheetValue.kt — Custom(anchor: AnchorState)
- [x] state/SheetMetrics.kt — customAnchors: Set<AnchorState>, customAnchorPx(anchor)
- [x] state/SheetAnchorTableFactory.kt — iterate set
- [x] state/SheetAnchorTargets.kt — Custom -> customAnchorPx(value.anchor)
- [x] state/XBottomSheetState.kt — anchors: Set<AnchorState>, resolveRestTargetAfterConfigChange (!anchors.contains(value.anchor)), onLiveConfigChanged
- [x] state/builder/StructureBuilder.kt — buildAnchors(): Set<AnchorState>
- [x] state/XBottomSheetStateBuilder.kt — buildState anchors type (no edit needed; propagates via buildAnchors)
- [x] state/XBottomSheetStateSaver.kt — full persist + AnchorState/Color/Dp/DragHandleStyle encoding (dropped unused builder param; RememberXBottomSheetState.kt call updated)
- [x] observe/ObserveSheetState.kt — triplet unchanged; Set equals structural, distinctUntilChanged works (no edit needed)
- [x] demo/XbsDemoScreen.kt — migrated `"x" at v` -> `+Fraction(v)` (cases R, W); describeSheetValue uses value.anchor

## Invariants (hard)

- No `!!`, no `Any` in NEW code (the Saver's `List<Any>` save-type is the framework contract — allowed, it already existed). No magic numbers (constants). Named lambda params only.
- No comments/KDoc unless already present; don't touch existing (even typo'd) comments.
- Do NOT delete files. Do NOT git commit/push.
- ast-index for search. MUST end compile-green: `./gradlew :app:compileDebugKotlin`.
- Preserve ALL gesture/settle/IME/reconciliation/predictive-back logic.

## Checklist

- [x] AnchorState type + DSL
- [x] anchors storage Set<AnchorState> end-to-end
- [x] SheetValue.Custom carries AnchorState; px resolution correct (Fraction/FullScreen/WrapContent)
- [x] Saver persists full mutable state + restores it
- [x] demo migrated
- [x] compileDebugKotlin GREEN
