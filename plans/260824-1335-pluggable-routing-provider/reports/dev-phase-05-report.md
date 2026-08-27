# Dev report — Phase 05: navigation screen and attribution

Date: 2026-08-24 · Plan: `plans/260824-1335-pluggable-routing-provider/` · Phase: `phase-05-navigation-screen-and-attribution.md`

## Status: completed (code) — screenshot verification left to device/emulator pass, per instructions

`./gradlew build` green (all modules, debug+release, lint, unit tests). `:ui:test` 79 tests / 0
failures. `:app:test` (`KoinModulesTest`) 1 test / 0 failures.

## Files created

- `ui/src/main/java/.../ui/feature/navigation/NavigationContract.kt` (72 lines)
- `ui/src/main/java/.../ui/feature/navigation/NavigationViewModel.kt` (114 lines)
- `ui/src/main/java/.../ui/feature/navigation/NavigationScreen.kt` (179 lines)
- `ui/src/main/java/.../ui/feature/navigation/component/NavigationMap.kt` (115 lines)
- `ui/src/main/java/.../ui/feature/navigation/component/NavigationPolyline.kt` (29 lines)
- `ui/src/main/java/.../ui/feature/navigation/component/NavigationSummaryCard.kt` (60 lines)
- `ui/src/main/java/.../ui/feature/navigation/component/RoutingAttribution.kt` (49 lines)
- `ui/src/test/java/.../ui/feature/navigation/NavigationViewModelTest.kt` (8 tests)

## Files modified

- `ui/src/main/java/.../ui/navigation/Routes.kt` — `NavigationRoute(memberId)` + `ARG_MEMBER_ID`.
- `ui/src/main/java/.../ui/navigation/FamilyTrackerNavHost.kt` — `composable<NavigationRoute>` +
  `onOpenNavigation` wired from `MapScreenRoute`, import alias `NavigationScreenRoute` (same
  same-name-different-package trap as `ZoneEditorRoute`/`HistoryRoute`, LLM.md §7).
- `ui/src/main/java/.../ui/feature/map/MapContract.kt` — `MapIntent.NavigateToMemberRequested`,
  `MapEffect.OpenNavigation`, computed `MapState.canNavigateToSelected`.
- `ui/src/main/java/.../ui/feature/map/MapViewModel.kt` — one new `when` branch.
- `ui/src/main/java/.../ui/feature/map/MapScreen.kt` — `onOpenNavigation` param, effect branch, a
  "Chỉ đường" `Button` at `Alignment.BottomCenter` (not `BottomStart` — that's Google's own
  logo/attribution corner, see Key Insight #2, applied generally not just to `RoutingAttribution`).
- `ui/src/main/java/.../ui/di/UiModule.kt` — `viewModelOf(::NavigationViewModel)`.
- `ui/src/main/java/.../ui/designsystem/theme/Color.kt` — `NavigationRouteColor` (`0xFFFFD600`,
  distinct from `PrimaryBlue`/zone colors/Google's default route blue).
- `ui/src/main/java/.../ui/designsystem/theme/Dimens.kt` — `NavigationPolylineWidth` (**doc drift,
  not in the phase file's own file list — added because `Color.kt` was already being touched for
  the same reason and a hardcoded width literal in a composable would violate LLM.md §12**).
- `ui/src/main/res/values/strings.xml` — 9 new strings under a `Navigation` section.
- `data/src/main/java/.../data/di/DataModule.kt` — **doc drift, not in the phase file's own file
  list.** `ObserveNavigationUseCase` binding added here, not `UiModule.kt` — see "Deviations" below.

## Tasks completed

All Todo List items except the on-device screenshot (explicitly left to the requester, per the
assignment). Concretely:

- [x] `NavigationRoute(memberId)` + NavHost registration; `NavigationViewModel` reads the arg via
      `savedStateHandle.get<String>(NavigationRoute.ARG_MEMBER_ID)`, not `toRoute<T>()`.
- [x] `NavigationContract.kt` — State/Intent/Effect only.
- [x] `NavigationViewModel.kt` — `onIntent` is the only public method; three independent
      `collectSafely` subscriptions in `init` (members+locations, navigation updates, tracking
      flag), no `combine()` — same shape as `MapViewModel.init`.
- [x] `NavigationScreen.kt` + the four `component/` files.
- [x] `RoutingAttribution.kt` — three states (route / fallback / hidden), rendered in its own strip
      **below** the map `Box`, never an overlay on top of it.
- [x] Polyline color in `Color.kt`.
- [x] Entry point from `MapScreen` — intent, effect, button, gated on `canNavigateToSelected`.
- [x] Straight-line degrade + "estimated" label + attribution swap.
- [x] Tracking-off banner, explicit opt-in button, route/polyline untouched while off.
- [x] Strings in `strings.xml`.
- [x] `viewModelOf(::NavigationViewModel)` in `UiModule`.
- [x] `NavigationViewModelTest` — 8 tests, including the two attribution-pinning tests and the two
      extra tests demanded by the task brief (sticky fallback flag, error-does-not-erase-route).

## Tests status

- Type check / compile: **pass** (`:ui:compileDebugKotlin`, `:data:compileDebugKotlin`).
- `:ui:test`: **pass**, 79 tests total across 10 suites (new: `NavigationViewModelTest` 8; existing
  71 unaffected), 0 failures, 0 errors, 0 skipped.
- `:app:test` (`KoinModulesTest.verify()`): **pass**, 1 test, 0 failures — confirms the manual
  `factory { ObserveNavigationUseCase(...) }` wiring resolves statically.
- `./gradlew build` (all modules, debug+release, lint, unit tests): **pass**. One lint error was
  found and fixed mid-pass (see Issues below); the two remaining lint warnings pre-exist this phase
  (`LocationPermissionFlow.kt` InlinedApi, `zone_editor_limit_warning` PluralsCandidate — untouched
  files).

## Verification commands run

```
./gradlew :ui:compileDebugKotlin :data:compileDebugKotlin   → BUILD SUCCESSFUL
./gradlew :ui:test :app:test                                → BUILD SUCCESSFUL, 79+1 tests, 0 failures
./gradlew build                                              → BUILD SUCCESSFUL (after lint fix)
grep -rn "import androidx.compose\|import android\." \
  ui/src/main/java/.../ui/feature/navigation/NavigationViewModel.kt   → (no output)
grep -rn "TileOverlay\|MapLibre\|maplibre" ui/src/main/java data/src/main/java app/src/main/java
  → only a KDoc comment in NavigationMap.kt stating there is NO second basemap; no actual usage
```

## MVI doc §9 checklist — self-review

Contract: Contract-only file ✓; `data class`, all `val`, defaulted ✓; `polyline`/`attributionLines`
computed, not duplicated ✓; Intent named after events ✓; every Effect one-shot ✓;
`compose-stability.conf` not applicable — project has not stood it up yet (LLM.md §13, unrelated to
this phase).

ViewModel: extends `MviViewModel<S,I,E>` ✓; `onIntent` only public method ✓; exhaustive `when`, no
`else` ✓; every coroutine through `launchSafely`/`collectSafely`, confirmed by
`CoroutineSafetyArchitectureTest` passing ✓; no flag raised-then-stranded (no busy flag used here)
✓; superseding requests cancel-and-replace (`navigationJob`, `Retry` intent) ✓; sub-job held as a
field only for cancel-and-replace, same shape as `HistoryViewModel.routeObservationJob`, not a
child-job-leak pattern ✓; no Compose/Android import, confirmed by grep ✓; registered as
`viewModelOf {}` ✓.

Screen: `XRoute`/`XScreen` split ✓; `collectAsStateWithLifecycle` ✓; effects via `CollectEffects`
(`collect`, lifecycle-aware) ✓; all three declared effects handled ✓; nothing below `NavigationRoute`
sees the ViewModel ✓; strings resolved inside composition (`stringResource`, not
`context.getString()` inside a `@Composable` — this is exactly what lint caught, see below) ✓; every
new file under 200 lines (largest: `NavigationScreen.kt`, 179) ✓.

## Deviations from the phase file — flagged, not silent

1. **`NavigationState` gained `distanceMeters: Double?` / `isDistanceEstimated: Boolean`.** The
   phase file's own `NavigationState` code snippet omits them, but the real (already-committed)
   `NavigationUpdate` always carries a computed distance — `:ui` has no legal way to compute it
   itself (`GeoDistance` is `internal` to `:domain`). This was flagged to the requester up front as
   one of "four things the phase spec doesn't know" and implemented exactly as directed.
2. **`NavigationState` gained `hasCenteredOnce: Boolean` + `NavigationIntent.CameraCentered`.**
   Implementation Step 6 asks for "the same `hasCenteredOnce` rule `MapState` uses," but the state
   snippet doesn't declare the field. Added to satisfy the step's own words.
3. **`isFallbackStraightLine` sticky formula**, exactly as directed:
   `directions == null && (lastError != null || isFallbackStraightLine)` (reads the *old* state via
   the `copy` receiver). Pinned by test `isFallbackStraightLine sticks through a later no-error
   emission while still unrouted`.

   One honest note on that test's mechanism: tracing the real, already-committed
   `RerouteEvaluator.evaluate()`, step 3 ("no route yet") returns `Reroute` **unconditionally**
   whenever `directions == null`, bypassing the 60s debounce entirely — so the specific narrative in
   the brief ("the next emit falls inside the 60s debounce window, `lastError == null` while
   `directions` is still null") cannot literally happen while unrouted; debounce only starts
   applying once a route has succeeded at least once. The sticky requirement is still fully correct
   and independently necessary — there is a real path to the same symptom via the `Arrived` branch
   (step 1), which is checked *before* step 3 and short-circuits with `lastError == null` while
   `directions` can still be `null` (arriving before ever getting a route). The test uses that real
   path (self and target coincide on the second emission) rather than a debounce timer, so it
   exercises the actual domain code, not a contrived double. Flagging this rather than silently
   asserting the brief's exact wording was correct.
4. **`data/di/DataModule.kt` change not listed in the phase file's own "Sửa" section.** Fixed in the
   phase file itself in this same change (see its updated "Related Code Files" / "Lệch khỏi snippet"
   sections) per `documentation-management.md` — doc drift is fixed in the same commit that causes
   it, not deferred. Reason: `ObserveNavigationUseCase` is a use case, and this repo's convention
   (LLM.md §6, `factoryOf(::PurgeOldHistoryUseCase)` etc.) puts use case bindings in `DataModule.kt`,
   not `UiModule.kt`. `factoryOf(::ObserveNavigationUseCase)` was tried first and does make
   `KoinModulesTest` red (constructor param 4, `nowMs: () -> Long`, has no matching binding) — fixed
   by hand-writing the `factory { }` lambda instead, which (confirmed by the now-green
   `KoinModulesTest`) does not force `verify()` to require a `Function0<Long>` binding the way
   `factoryOf`'s constructor-reference DSL does. `locationSource = get(named("fused"))` — the only
   unqualified `LocationSource` binding does not exist in `DataModule`; "fused" (the device's real
   position) is the only one that means anything for "directions from me to X".
5. **`NavigationEffect.StartTracking` is sent *alongside* `NavigationViewModel` calling
   `trackingRepository.setTracking(true)` directly**, not as the mechanism that starts tracking.
   `EnableTrackingRequested` calls the repository directly (same shape as
   `MapViewModel.onToggleTracking` — no platform boundary requires the Route to mediate this), and
   the effect is a one-shot snackbar confirmation the Route collects. This keeps the declared Effect
   meaningful (collected, not dead) rather than inventing an unclear "the Route does the real work"
   contract that has no actual platform-specific step to perform.

## Issues encountered

- `./gradlew build` failed once on `:ui:lintDebug` — `LocalContextGetResourceValueCall`: I had
  called `context.getString(...)` directly inside `NavigationRoute`'s `CollectEffects` lambda (real
  composable scope, not inside a non-`@Composable` mapper function like `MapScreen`'s
  `toDisplayMessage`, which lint does not flag). Fixed by resolving the string with
  `stringResource(...)` into a `val` before `CollectEffects`, matching MVI doc §4's explicit rule
  ("resolve strings inside the composition"). Re-ran `./gradlew build` clean afterward.
- No file ownership conflicts — this phase's file list did not overlap any other in-flight phase.

## Unresolved questions

None outstanding after the four "phase spec doesn't know this" items from the assignment were
implemented exactly as directed. Screenshot verification (Success Criteria #1–#4, all require eyes
on a device) is explicitly left to the requester per the assignment — everything checkable by build
tooling is green.

## Next steps

Phase 06 (per the phase file's own "Next Steps"): run the compliance gate, update `LLM.md` +
`docs/`, and pin the terms-of-service check date. It should also record the DataModule/Dimens doc
drift fixes above as already closed (this report + the phase file's own updated sections cover it),
and pick up the on-device screenshot task this phase deliberately left open.
