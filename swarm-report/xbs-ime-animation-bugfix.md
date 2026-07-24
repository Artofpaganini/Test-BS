# XBS bug-fix: IME (keyboard) animation smoothness in Lift mode

Profile: bug-fix · size M · catalog base. Living state. Do NOT commit until validated on device.

## Symptoms (device-observed by user)
- FIXED: gap (navBar-sized) below sheet content above keyboard in i/s — fixed by not reserving navBarPx when keyboard visible in non-fullscreen Lift (`LiftContent`).
- FIXED: case L (StayUnderKeyboard) revert had ~1s delay on keyboard collapse — fixed by taking `isKeyboardVisible` from requested state in KeyboardLift onProgress.
- OPEN 1: API 37+ — cases i/s, keyboard SHOW looks like a DOUBLE lift (jerky). Hide is OK.
- OPEN 2: API <= 36 — cases i/s, keyboard HIDE lost smooth animation (jumps). Show OK-ish.

## Demo cases
- i = Lift, search+list, promotes to FullScreen if doesn't fit.
- l = StayUnderKeyboard (bottom under keyboard). isAlwaysFullScreenOnIme = true.
- s = Lift + bottom (bottom above keyboard).

## Mechanisms in play (all in repo)
- `KeyboardLiftState` (org.xplatform.uikit .../keyboard/lift/KeyboardLift.kt): keyboardHeight/isVisible. This session added a `WindowInsetsAnimationCompat.Callback` (per-frame keyboardHeight via onProgress) + OnGlobalLayout for settled; isKeyboardVisible now = requested visibility (getRootWindowInsets.isVisible).
- `withAdjustmentForKeyboard` (KeyboardAdjustmentNode): lifts the WHOLE sheet (translationY) when NOT fullscreen. API-split: `isLessThan30Api()` → <30 uses ADJUST_RESIZE + tween(300ms) animateTo; >=30 uses ADJUST_NOTHING + snapToTarget (relies on animated keyboardHeight for smoothness). target = keyboardHeight - bottomY.
- `withKeyboardShrink` (SOFT_INPUT_ADJUST_NOTHING): shrinks middle when fullscreen.
- `LiftContent.bottomInset`: fullscreen+kb → keyboardHeight; kb-visible (non-fullscreen) → 0; else navBarPx.
- Promote: `onImeShown` → canPromoteForIme → `animateTo(ExpandedFullScreen)` = offset.animateTo(maxHeight, NativeSheetSpring). Separate spring from the keyboard animation.
- SheetContainer: `if (!isFullScreen) withAdjustmentForKeyboard else Modifier`.

## Leading hypothesis
Two concurrent, differently-timed animations on SHOW: (a) promote-spring (offset→maxHeight, NativeSheetSpring) and (b) content-follow (bottomInset/adjustment following animated keyboardHeight / system IME curve). They desync → "double lift" (visible on 37+). And the discrete isFullScreen flip changes the LiftContent branch mid-animation. On <=36 hide, requested-visibility flips isKeyboardVisible=false at hide-start → bottomInset branch snaps → hide jumps.

## STATE: reverted to clean (commit 057d8b4)
All my session IME edits reverted. KeyboardLift.kt = OnGlobalLayout-only (no anim callback);
LiftContent.kt = original bottomInset (if fullscreen&&kbVisible → keyboardHeight else navBarPx).

## Original bugs to fix (on the clean code)
- BUG-SHOW: i/s — on keyboard SHOW the list JUMPS up abruptly, no smooth follow. L is smooth.
- BUG-GAP: i/s — navBar-sized empty gap between the last content (button) and the keyboard top. All APIs (31/36).
- (L / StayUnderKeyboard is fine on clean code — the 1s revert delay was introduced by my anim-callback, so it's gone after revert.)

## Lessons from reverted experiments (DO NOT repeat blindly)
- Adding WindowInsetsAnimationCompat.Callback for per-frame keyboardHeight made SHOW smoother BUT created a
  "double lift" on API 37+ (promote-spring desynced from the keyboard-follow) — two concurrent animations.
- Taking isKeyboardVisible from requested state fixed a 1s L-revert but snapped LiftContent bottomInset on
  hide (<=36 hide jump).
- GAP root cause CONFIRMED: non-fullscreen Lift reserves navBarPx at sheet bottom, then withAdjustmentForKeyboard
  lifts the whole sheet incl. that padding → content ends navBarPx above keyboard. Fix = don't reserve navBar
  when keyboard visible. (This fix was correct; reverted only as part of the full rollback.)
- API-split in KeyboardAdjustmentNode: <30 → ADJUST_RESIZE + tween(300); >=30 → ADJUST_NOTHING + snapToTarget
  (needs an animated keyboardHeight source to be smooth).

## Diagnose consilium — 3 lenses (fill in findings)
- LENS A (insets/anim-callback/API-split): ...
- LENS B (promote vs keyboard sync / state-branch flip): ...
- LENS C (layout reserve / adjustment math / decoupling): ...

## SYNTHESIS (3 lenses agree)
- GAP = navBarPx double reserve in non-fullscreen Lift (adjustment lifts sheet by keyboardHeight + LiftContent reserves navBarPx). Fullscreen is flush.
- SHOW jump = OnGlobalLayout settled height → KeyboardAdjustmentNode.snapToTarget (>=30) = 1-frame snap.
- DOUBLE-lift (37+) = per-frame source exposes the SECOND animator: promote-spring (onImeShown → animateTo(FullScreen, NativeSheetSpring)) desynced from the keyboard follow.
- <=36 hide snap = KeyboardLift zeroes height on the requested boolean → LiftContent boolean branch snaps.
- Invariant: button-bottom(abs) = max(keyboardHeight, navBarPx).
- StayUnder (L) already correct; never gets withAdjustmentForKeyboard (only applied when !isFullScreen).

## PLAN — 2 stages
### Stage 1 — GAP (DONE, compile green, safe)
LiftContent bottomInset `when { fullscreen&&kbVisible → keyboardHeight; kbVisible → 0; else → navBarPx }`.
Endpoints correct, no animation change, no snap on clean KeyboardLift. [x]

### Stage 2 — SHOW/HIDE smoothness (BIG, needs GO + device iteration on 31/36/37+)
Disciplined hybrid:
1. Single ANIMATED keyboard source: switch `rememberKeyboardLiftState` to Compose `WindowInsets.ime` (per-frame on >=30; demo is edge-to-edge+adjustResize). Decouple keyboardHeight (animated) from isKeyboardVisible (settled/requested). Do NOT zero height on the boolean. Keep <30 tween fallback in KeyboardAdjustmentNode. Remove dead callback in KeyboardShrinkNode.
2. Promote XOR translate — decide once at show-start (canPromoteForIme). Fits → translate-only (node). Doesn't fit → promote but drive `offset` from the same animated keyboardHeight (lerp), not NativeSheetSpring. isFullScreen gates only soft-input-mode, not geometry.
3. Continuous geometry (LiftContent): fullscreen → max(keyboardHeight, navBarPx); non-fullscreen → (navBarPx - keyboardHeight).coerceAtLeast(0). No boolean branch.
4. Hide: revert at hide-START (requested-visibility false), drive offset+inset from descending keyboardHeight lockstep. Cancel manual offset-drive on drag-start; don't collide with predictive-back/additionalTop offset animators.
Files: KeyboardLift, KeyboardAdjustmentNode, LiftContent, SheetContainer, XBottomSheetState (promote/revert), ObserveSheetState.
Risk: HIGH, verify on 31/36/37+.

## Checklist
- [ ] Root-cause per symptom (37+ double, <=36 hide) agreed
- [ ] Fix designed (sync promote to keyboard OR decouple height/visibility OR revert callback)
- [ ] Implemented, compile green
- [ ] Device-verified on 31 / 36 / 37+
