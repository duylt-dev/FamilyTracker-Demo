package com.example.pion.family.tracker.demo.domain.model

/**
 * A single computed route between two [GeoPoint]s — routing plan phase-01 Key Insight #2.
 * Named `Directions`, not `Route`: this repo already owns "Route" for a *past trip*
 * (`RouteSplitter`, `RouteStats`, `ObserveRouteForDayUseCase`, `RoutePolyline`, `Routes.kt`).
 * A third "Route" would force every reader to ask "which route" at every call site.
 *
 * [points] is `List<GeoPoint>`, never `LatLng` — see [GeoPoint].
 *
 * [attribution] is **required, non-nullable, with no default** — this is legal condition #1 of
 * `docs/routing-and-map-attribution.md` §3 given the shape of a type: it is not possible to
 * construct a [Directions] that forgot to carry credit. GraphHopper returns this string itself
 * (`info.copyrights`, e.g. `["GraphHopper", "OpenStreetMap contributors"]` — verified against a
 * real response, VERIFY-2026-08-24.md), so the phase-02 mapper copies exactly what the provider
 * demands instead of guessing on its behalf.
 *
 * [engineId] stays separate from [attribution] and exists only for logging/diagnostics — merging
 * the two would mean renaming an engine in a log line silently changes the legal text shown on
 * screen.
 */
data class Directions(
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val engineId: String,
    val attribution: List<String>,
)
