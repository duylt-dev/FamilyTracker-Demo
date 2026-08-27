package com.example.pion.family.tracker.demo.domain.repository

import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.Directions
import com.example.pion.family.tracker.demo.domain.model.GeoPoint

/**
 * The port `:ui` calls through a use case (phase-04+) without ever seeing HTTP — routing plan
 * phase-01, cổng tên `RoutingProvider`, enum tên `RoutingEngine` (Key Insight #3). Implemented by
 * `GraphHopperRoutingProvider` (phase-02) and `ValhallaRoutingProvider` (phase-03), neither of
 * which exists yet: this phase deliberately connects to no server.
 *
 * No `profile` parameter yet — added later without breaking any call site by giving it a
 * default, once a real use case exists to pass one.
 *
 * [AppResult]/[AppError] are already sufficient (VERIFY-2026-08-24.md confirms this) — no new
 * error type for routing.
 */
interface RoutingProvider {
    suspend fun directions(from: GeoPoint, to: GeoPoint): AppResult<Directions>
}
