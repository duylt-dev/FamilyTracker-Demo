package com.example.pion.family.tracker.demo.domain.model

/**
 * One emission of
 * [com.example.pion.family.tracker.demo.domain.usecase.ObserveNavigationUseCase] — routing
 * plan phase-04.
 *
 * [distanceMeters] is ALWAYS populated, including when [directions] is `null`: with a route it is
 * the route's own distance; without one it is
 * [com.example.pion.family.tracker.demo.domain.tracking.GeoDistance.haversineMeters] between
 * follower and target, i.e. straight-line. [isDistanceEstimated] tells the two apart.
 *
 * This is not decoration: `GeoDistance` is `internal` to `:domain` by design (LLM.md §8.2), so
 * `:ui` cannot call it. If this use case left [distanceMeters] empty in the degraded (no-route)
 * branch, `:ui` would have no legal way to show a distance and would either invent a formula
 * inside a composable or widen `GeoDistance` to `public` — both compile, which is why nobody would
 * catch it (VERIFY-2026-08-24.md §5). Computed once, here, where it belongs.
 *
 * [lastError] rides alongside [directions] rather than replacing it: a provider failure must never
 * blank a route already on screen — losing network for three seconds must not make the drawn
 * route vanish.
 */
data class NavigationUpdate(
    val directions: Directions?,
    val distanceMeters: Double,
    val isDistanceEstimated: Boolean,
    val hasArrived: Boolean,
    val lastError: AppError?,
)
