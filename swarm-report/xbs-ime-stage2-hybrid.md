# XBS IME Stage 2 — smooth show/hide (hybrid). EXECUTE spec.

Goal: cases i/s (Lift) — keyboard SHOW/HIDE animate smoothly on API 31/36/37+.
No double-lift, no jump, no <=36 hide-snap, no 1s revert. Preserve drag/settle/predictive-back/additionalTop.
Gap already fixed (Stage 1, committed). Do NOT reintroduce it.

Background diagnosis: see `swarm-report/xbs-ime-animation-bugfix.md` (3-lens consilium). Read it first.

## Root causes (recap)
- SHOW jump: keyboardHeight comes from OnGlobalLayout (settled, single-step) → KeyboardAdjustmentNode.snapToTarget (>=30) snaps in 1 frame.
- DOUBLE-lift: two independent animators react to one keyboard event — (1) the keyboard-follow translation, (2) the promote-spring `onImeShown → animateTo(FullScreen, NativeSheetSpring)`. Desynced.
- <=36 hide-snap: KeyboardLift zeroes height on the requested boolean → boolean layout branch snaps.
- Invariant: button-bottom(abs) = max(keyboardHeight, navBarPx).

## The 4 changes

### 1. Single ANIMATED keyboard source — `org/xplatform/uikit/.../keyboard/lift/KeyboardLift.kt`
Switch `rememberKeyboardLiftState()` from the View OnGlobalLayout to Compose `WindowInsets.ime`:
- `keyboardHeight` = `WindowInsets.ime.getBottom(density).toFloat()` — this recomposes per-frame during the IME animation on API >= 30 (Compose installs its own IME WindowInsetsAnimation callback; works under ADJUST_NOTHING given the app is edge-to-edge + adjustResize, which the demo is).
- `isKeyboardVisible` = `WindowInsets.isImeVisible` (androidx.compose.foundation.layout) — settled/requested visibility, DECOUPLED from height.
- Do NOT zero keyboardHeight based on the boolean. Height is the animated value; visibility is separate.
- Keep the return type `State<KeyboardLiftState>`. The no-arg call sites stay valid.
- Remove the now-dead `WindowInsetsAnimationCompat.Callback` code in `keyboard/shrink/KeyboardShrinkNode.kt` (attachInsetsAnimationCallback ~:240-265) if it becomes unreachable — verify it's unused first; if unsure, leave it.
- API < 30: `WindowInsets.ime` does not animate. Keep the existing `<30 → ADJUST_RESIZE + animateToTarget(tween 300)` fallback in KeyboardAdjustmentNode untouched (that path smooths <30 via resize).

### 2. Continuous geometry — `layout/LiftContent.kt`
Replace the boolean-branch reserve with continuous functions of keyboardHeight (no snap when a flag flips):
- fullscreen: `bottomInset = max(keyboardHeight.roundToInt(), navBarPx)`
- non-fullscreen: `bottomInset = (navBarPx - keyboardHeight.roundToInt()).coerceAtLeast(0)`
Both express the invariant button-bottom(abs)=max(keyboardHeight,navBarPx) (non-fs also gets +keyboardHeight from the adjustment translation). This keeps the Stage-1 gap fix (non-fs+kb large → 0) AND removes the <=36 hide-snap (continuous).
Keep `withKeyboardShrink` for the fullscreen middle. `StayUnderKeyboardMeasurePolicy` (case L) is already correct — do NOT change it.

### 3. Promote XOR translate; drive promote OFFSET from keyboardHeight — `state/XBottomSheetState.kt`, `observe/ObserveSheetState.kt`, `layout/SheetContainer.kt`
Decide ONCE at show-start whether to promote (existing `canPromoteForIme`).
- FITS (no promote): sheet stays at its anchor; `withAdjustmentForKeyboard` translates it up (already the current behavior when canPromoteForIme=false). With the animated source (#1) this is now smooth. Nothing to change here except it must NOT also promote.
- DOESN'T FIT (promote): instead of `onImeShown → animateTo(ExpandedFullScreen, NativeSheetSpring)`, drive `offset` from the animated keyboardHeight so the sheet grows in lockstep with the keyboard:
  - On ime show while promotable: enter an "ime-driven" mode; set `currentValue = ExpandedFullScreen` (for bookkeeping) but drive `offset.snapTo(lerp(startAnchorPx, maxHeightPx, imeFraction))` where `imeFraction = (keyboardHeight / targetKeyboardHeightPx).coerceIn(0,1)`; `startAnchorPx` = anchorPx of the pre-ime state; `targetKeyboardHeightPx` = the settled ime height (captured when visibility becomes true).
  - On ime hide: drive `offset` back from maxHeightPx toward `startAnchorPx` as keyboardHeight descends (lerp on the descending imeFraction). Revert starts at hide-START (visibility=false), not at settle — kills the 1s lag.
  - When promoting, do NOT also run `withAdjustmentForKeyboard`. `SheetContainer` currently gates the adjustment on `!isFullScreen`; that stays consistent (promote → isFullScreen → no adjustment). Ensure only ONE of {adjustment translate, promote offset-drive} is active for a given sheet at a time.
  - `isFullScreen` must NOT drive geometry that snaps mid-animation; with continuous LiftContent (#2) and offset-driven growth this is satisfied.
- SAFETY (must preserve):
  - Cancel the ime-driven offset mode on drag start (`markDragStarted`/gesture) so the user can override; `settle()` already resets `imePromotedFrom` — keep that path working.
  - Do not collide with the predictive-back offset/translation (`XBottomSheet.kt` backShift) or the additionalTop fraction animator.
  - Keep `imePromotedFrom` semantics (which state to return to on hide).
  - `onContentRemeasured`, `onLiveConfigChanged`, show/expand/hide, Loading — must still work.

### 4. Wiring / visibility
- `isAlwaysFullScreenOnIme` (case L / StayUnder) path unchanged.
- `ObserveSheetState` ime watch drives onImeShown/onImeHidden; adapt so show/hide enter/exit the ime-driven offset mode. The per-frame keyboardHeight should NOT spam state transitions — gate transitions on `isKeyboardVisible` (settled boolean), drive geometry on `keyboardHeight` (animated).

## PERF / LEAK constraint (user, hard)
- NO per-frame recomposition: do not read WindowInsets.ime in the composition phase. Consume the animated
  inset in the LAYOUT phase (Modifier.layout) and/or a coroutine driving an animation — updates hit
  layout/animation, not recomposition.
- Use `Animatable` for the lift/offset motion (reuse `offset`, or a dedicated Animatable in a coroutine).
  Exactly ONE animator for the lift (no two springs/Animatables racing).
- NO leaks: dispose every listener/callback/coroutine (DisposableEffect.onDispose / Node.onDetach /
  scope cancellation). Model: KeyboardAdjustmentNode.onDetach. If KeyboardLift source changes, remove the
  old OnGlobalLayout listener; leave no callback attached to rootView.

## Invariants (hard)
- No `!!`, no `Any`, no magic numbers (name consts), named lambda params. `internal` default. No comments/KDoc unless present (fix ones my change makes wrong).
- Do NOT delete files (list orphans). Do NOT git commit/push.
- ast-index for search. MUST compile green: `./gradlew :app:compileDebugKotlin`.
- Preserve ALL non-IME logic (gestures, settle, overshoot, predictive-back, additionalTop, Loading, saver).
- StayUnder (L) and the Stage-1 gap fix must NOT regress.

## Checklist
- [x] KeyboardLift → animated per-frame source, decoupled visibility
      NOTE: per added constraint (no per-frame recomposition), the source is NOT a composition-phase
      `WindowInsets.ime.getBottom` read. Instead `rememberKeyboardLiftState` installs a
      `WindowInsetsAnimationCompat.Callback` on rootView (per-frame onProgress on API>=30) + OnGlobalLayout
      (settled / API<30), writes into `State<KeyboardLiftState>`. Readers consume in LAYOUT (LiftContent
      measure, StayUnder policy) or COROUTINE (adjustment/shrink snapshotFlow, ObserveSheetState) — never
      per-frame in composition. Added `targetKeyboardHeightPx` field (from animation bounds.upperBound).
      Callback + listener removed in onDispose (no leak). Height NOT zeroed on the boolean (decoupled).
- [x] LiftContent continuous reserve (keeps gap fix, no snap): fullscreen `max(kb,navBar)`, non-fs `(navBar-kb).coerceAtLeast(0)`
- [x] promote drives offset from keyboardHeight (lockstep), no NativeSheetSpring promote for i/s; XOR with translate
      (isAlwaysFullScreenOnIme / case L path kept on the existing spring promote — unchanged)
- [x] hide reverts at start (requested-visibility flip), lockstep down via the same animated height; no 1s, no snap
- [x] drag/settle/predictive-back/additionalTop preserved (offset-drive cancelled on drag/settle/hide/expand;
      backShift + additionalTopFraction are separate animators, untouched)
- [x] compile green (`./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL)
- [ ] (device) verify i/s smooth show+hide + L unaffected on 31/36/37+ — user
