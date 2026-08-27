# Dev report — Phase 04: domain reroute and arrival

Date: 2026-08-24 · Plan: `plans/260824-1335-pluggable-routing-provider/` · Phase: `phase-04-domain-reroute-and-arrival.md`

## Status: completed

`:domain:test` green, 86 tests, 0.130s reported test time (well under the 5s budget). Full
`./gradlew build` also green (20s), confirming `:data`/`:ui`/`:app` still compile against the
changed `:domain` module.

## Files created

- `domain/src/main/kotlin/.../domain/tracking/RoutingGeometry.kt` (74 lines) — `internal object`,
  equirectangular point-to-segment/point-to-polyline distance.
- `domain/src/main/kotlin/.../domain/tracking/RerouteEvaluator.kt` (117 lines) — `RerouteState`,
  `RerouteReason`, `RerouteDecision` sealed interface, `RerouteEvaluator.evaluate()`.
- `domain/src/main/kotlin/.../domain/model/NavigationUpdate.kt` (26 lines).
- `domain/src/main/kotlin/.../domain/usecase/ObserveNavigationUseCase.kt` (85 lines).
- `domain/src/test/kotlin/.../domain/tracking/RoutingGeometryTest.kt` (6 tests).
- `domain/src/test/kotlin/.../domain/tracking/RerouteEvaluatorTest.kt` (8 tests).
- `domain/src/test/kotlin/.../domain/usecase/ObserveNavigationUseCaseTest.kt` (3 tests).

## Files modified

- `domain/src/main/kotlin/.../domain/tracking/TrackingConstants.kt` — appended 6 constants
  (`OFF_ROUTE_TOLERANCE_M`, `OFF_ROUTE_CONSECUTIVE_SAMPLES`, `DESTINATION_MOVED_TOLERANCE_M`,
  `REROUTE_DEBOUNCE_MS`, `ARRIVAL_M`, `ARRIVAL_EXIT_M`), each with a comment giving both the
  too-small and too-large cost, and a block comment stating the source is routing-plan phase-04
  research, not PRD §6 (matches the phase spec's explicit instruction — this file's own header
  claims everything comes from PRD §6, which is no longer true for these six).

## Tasks completed

- [x] `RoutingGeometry.kt` with empty-polyline (`Double.MAX_VALUE`) and single-point
      (point-to-point) cases handled.
- [x] 6 constants in `TrackingConstants.kt`, two-directional cost comments, source noted as
      research not PRD §6.
- [x] `RerouteEvaluator.kt` — exact six-step order from the phase file's Architecture section.
      No internal state; `nowMs` is a parameter throughout.
- [x] `RoutingGeometryTest` — 6 cases: on-route (~0m), perpendicular-to-middle, beyond-segment-start
      (clamped to endpoint), empty polyline, single-point polyline, real Hà Nội coordinate
      (a real decoded Valhalla point from `valhalla-route-hanoi.json`, cross-checked against an
      independently computed spherical cross-track-distance value, not this file's own formula).
- [x] `RerouteEvaluatorTest` — 8 tests, one per `RerouteDecision` branch (`Arrived`, `Reroute(OFF_ROUTE)`,
      `Reroute(DESTINATION_MOVED)`, `Keep`) plus the three named edge cases: 2 off-route samples then
      1 on-route resets the counter; exactly-at-debounce-boundary (`nowMs - last == 60_000`) is
      allowed; arrived → target moves to ~75m away clears the flag.
- [x] `ObserveNavigationUseCase.kt` — the only caller of `RoutingProvider`; combines
      `LocationSource.stream()` (self) with `MemberRepository.observeLatestLocations()` (target);
      `distanceMeters` always populated (route distance or `GeoDistance.haversineMeters` straight-line,
      flagged via `isDistanceEstimated`); provider failure keeps the previous `Directions`, never
      nulls it, and rides alongside via `lastError`.
- [x] `ObserveNavigationUseCaseTest` — `FakeRoutingProvider` (call-counting, queued canned
      `AppResult`s, throws if called more than queued) + Turbine: first call fires immediately;
      no second call inside the debounce window; a provider error on the second (post-debounce)
      call leaves the first route intact.
- [x] `./gradlew :domain:test` green, < 5s.

## Tests status

- `:domain:test`: **pass**, 86 tests total (new: 6 + 8 + 3 = 17; existing 69 unaffected), 0 failures,
  0 errors, 0 skipped. Reported cumulative test time across all suites: **0.130s**.
- `./gradlew build` (full project, all modules incl. `:app`/`:ui`/`:data`): **pass**, 20s.

## Verification commands run

```
./gradlew :domain:test          → BUILD SUCCESSFUL, 86 tests, 0.130s
./gradlew build                 → BUILD SUCCESSFUL
grep -rn "import android\|import com.google" domain/src   → (no output)
grep -rn "System.currentTimeMillis" domain/src/main/kotlin/.../tracking/RerouteEvaluator.kt → (no output)
```

One self-correction during this pass: my first draft of `RerouteEvaluator.kt`'s KDoc literally
quoted `System.currentTimeMillis()` while *explaining* the "never call it inside" rule, which made
the required grep match a comment, not code. Reworded the KDoc to describe the rule without the
literal string so the grep is genuinely clean.

## Design notes / deviations from a literal reading

- **Step 2 (arrival hysteresis) never returns early.** The phase file's six-step list only
  describes step 2 as a conditional mutation ("bỏ cờ, xét tiếp" — clear the flag, continue), not a
  return. Implemented literally: if `hasArrived && distance > ARRIVAL_EXIT_M`, clear the flag and
  fall through to steps 3–6 unconditionally; if the condition is false (state stays arrived because
  distance is still inside the 50–70m hysteresis band), it also falls through — the resulting
  decision may be `Keep`/`Reroute` while `state.hasArrived` stays `true`. `NavigationUpdate.hasArrived`
  is read from the resulting `RerouteState`, not from the decision's sealed type, so this is
  consistent for the caller either way.
- **`ObserveNavigationUseCase` reads self position from `LocationSource.stream()` directly**, not
  through `LocationFilter`/`TrackingRepository` — this matches the phase file's Architecture line
  literally ("combine vị trí self + vị trí target") and Related Code Files list, which names
  `LocationSource`, not the filtered/Room-backed path.
- **`nowMs` on `ObserveNavigationUseCase`** has a real-clock default (`System.currentTimeMillis()`),
  mirroring the existing `TimelineViewModel: Clock` pattern (LLM.md §3) — this is the use case
  reading the clock to pass a value *into* the pure `RerouteEvaluator`, not `RerouteEvaluator`
  reading it itself, so it does not violate the phase's non-negotiable (which is scoped to
  `RerouteEvaluator`, confirmed by the grep target being that one file). No Koin wiring for this
  default was added — out of scope for this phase (no `:data`/`:ui`/`:app` files touched).
- **`RoutingGeometry`'s equirectangular projection uses the query point itself as the local
  reference (lat0/lon0)**, not a segment endpoint — the phase file's formula names `lat0`/`lon0`
  without pinning the reference, and centering on the query point keeps the projection most
  accurate exactly where the distance is being measured.

## Test fixture provenance

All coordinate literals were computed independently in Python (Haversine + spherical
cross-track-distance formulas — a different algorithm from this file's own equirectangular one)
before being pinned as literals in the test files, following this codebase's existing convention
(`GeoDistanceTest`, `PolylineDecoderTest`). The base coordinate `(21.028833, 105.854165)` is a real
decoded point from `data/src/test/resources/valhalla-route-hanoi.json`, already used by
`PolylineDecoderTest`.

## Issues encountered

- One naming collision: my first draft of `ObserveNavigationUseCaseTest`'s local fake
  (`FakeMemberRepository`) collided with the existing top-level `private class FakeMemberRepository`
  in `ObserveMembersWithLastLocationUseCaseTest.kt` — Kotlin treats same-package test files as one
  compilation unit, so two private top-level classes with the same name in the same package is a
  redeclaration error, not just a lint concern. Renamed mine to `FakeNavigationMemberRepository`.
  No other file ownership conflicts.

## Next steps

Phase 05 draws the result on the map and attaches attribution (the legal-memo requirement) —
consumes `ObserveNavigationUseCase` and `NavigationUpdate` from `:ui`.
