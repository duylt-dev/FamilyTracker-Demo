package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.Directions
import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every branch of [RerouteDecision] gets at least one test (phase-04 Success Criteria #3), plus
 * the three boundary cases the phase file calls out explicitly. Fixture distances were computed
 * independently in Python (Haversine) before being pinned as literals here — see the routing plan
 * dev report for the exact values.
 */
class RerouteEvaluatorTest {

    // A ~222m route running due north from a real decoded Valhalla point near Hồ Gươm — same
    // basis coordinate as RoutingGeometryTest.
    private val routeStart = GeoPoint(21.028833, 105.854165)
    private val routeEnd = GeoPoint(21.030833, 105.854165)
    private val route = Directions(
        points = listOf(routeStart, routeEnd),
        distanceMeters = 222.39,
        durationSeconds = 60L,
        engineId = "TEST",
        attribution = listOf("Test"),
    )

    // On the route (linear interpolation at 40%, ~0m off the line).
    private val followerOnRoute = GeoPoint(21.029633, 105.854165)

    // ~62m perpendicular from the route's midpoint — past OFF_ROUTE_TOLERANCE_M (45m).
    private val followerOffRoute = GeoPoint(21.029833, 105.854765)

    // ~21m from routeEnd — within DESTINATION_MOVED_TOLERANCE_M (200m), i.e. "not moved".
    private val targetNearRouteEnd = GeoPoint(21.030833, 105.854365)

    // ~334m from routeEnd — past DESTINATION_MOVED_TOLERANCE_M.
    private val targetFarFromRouteEnd = GeoPoint(21.033833, 105.854165)

    @Test
    fun `distance under ARRIVAL_M returns Arrived`() {
        val follower = GeoPoint(21.030933, 105.854165)
        val target = GeoPoint(21.030833, 105.854165) // ~11m away

        val decision = RerouteEvaluator.evaluate(RerouteState(), follower, target, null, nowMs = 0L)

        assertTrue(decision is RerouteDecision.Arrived)
        assertTrue((decision as RerouteDecision.Arrived).state.hasArrived)
    }

    @Test
    fun `the first call of a session fires immediately, no special case needed`() {
        // A fresh RerouteState has lastRerouteAtMs = null — "no call yet" — so the debounce gate
        // cannot block, whatever the clock reads. nowMs is deliberately small here: with 0L as the
        // sentinel instead, this assertion would depend on nowMs being larger than 60_000.
        val decision = RerouteEvaluator.evaluate(
            RerouteState(),
            followerOnRoute,
            targetNearRouteEnd,
            currentDirections = null,
            nowMs = 10L,
        )

        assertTrue(decision is RerouteDecision.Reroute)
        val reroute = decision as RerouteDecision.Reroute
        assertEquals(RerouteReason.OFF_ROUTE, reroute.reason)
        assertEquals(10L, reroute.state.lastRerouteAtMs)
        assertEquals(0, reroute.state.consecutiveOffRoute)
    }

    /**
     * The quota guard. A provider that keeps failing leaves `currentDirections` null forever; if
     * "no route yet" were evaluated before the debounce gate, every combined location sample
     * (~2.5s) would fire another paid provider call, without end. Phase-04's Security
     * Considerations name the debounce as the only thing standing between an error loop and a
     * bill — so it has to hold precisely in the unrouted case, not just the routed one.
     */
    @Test
    fun `still no route 50ms after a failed attempt keeps, it does not re-call the provider`() {
        val state = RerouteState(lastRerouteAtMs = 500_000L)

        val decision = RerouteEvaluator.evaluate(
            state,
            followerOnRoute,
            targetNearRouteEnd,
            currentDirections = null,
            nowMs = 500_050L, // 50ms after the failed attempt — deep inside the 60s window
        )

        assertTrue(decision is RerouteDecision.Keep)
        assertEquals(state, (decision as RerouteDecision.Keep).state)
    }

    @Test
    fun `still no route once the debounce has elapsed reroutes again`() {
        val state = RerouteState(lastRerouteAtMs = 500_000L)

        val decision = RerouteEvaluator.evaluate(
            state,
            followerOnRoute,
            targetNearRouteEnd,
            currentDirections = null,
            nowMs = 560_000L, // exactly one debounce window later
        )

        assertTrue(decision is RerouteDecision.Reroute)
        assertEquals(RerouteReason.OFF_ROUTE, (decision as RerouteDecision.Reroute).reason)
        assertEquals(560_000L, decision.state.lastRerouteAtMs)
    }

    @Test
    fun `within the debounce window keeps even while off-route, state untouched`() {
        val state = RerouteState(lastRerouteAtMs = 500_000L)

        val decision = RerouteEvaluator.evaluate(
            state,
            followerOffRoute,
            targetNearRouteEnd,
            currentDirections = route,
            nowMs = 505_000L, // only 5s later — inside the 60s debounce window
        )

        assertTrue(decision is RerouteDecision.Keep)
        assertEquals(state, (decision as RerouteDecision.Keep).state)
    }

    @Test
    fun `destination moved beyond tolerance reroutes once debounce has elapsed`() {
        val state = RerouteState(lastRerouteAtMs = 500_000L)

        val decision = RerouteEvaluator.evaluate(
            state,
            followerOnRoute,
            targetFarFromRouteEnd,
            currentDirections = route,
            nowMs = 561_000L, // 61s later — past the 60s debounce window
        )

        assertTrue(decision is RerouteDecision.Reroute)
        val reroute = decision as RerouteDecision.Reroute
        assertEquals(RerouteReason.DESTINATION_MOVED, reroute.reason)
        assertEquals(561_000L, reroute.state.lastRerouteAtMs)
        assertEquals(0, reroute.state.consecutiveOffRoute)
    }

    @Test
    fun `off-route accumulates and reroutes on the 3rd consecutive sample`() {
        var state = RerouteState()

        val first = RerouteEvaluator.evaluate(state, followerOffRoute, targetNearRouteEnd, route, nowMs = 100_000L)
        assertTrue(first is RerouteDecision.Keep)
        state = (first as RerouteDecision.Keep).state
        assertEquals(1, state.consecutiveOffRoute)

        val second = RerouteEvaluator.evaluate(state, followerOffRoute, targetNearRouteEnd, route, nowMs = 100_000L)
        assertTrue(second is RerouteDecision.Keep)
        state = (second as RerouteDecision.Keep).state
        assertEquals(2, state.consecutiveOffRoute)

        val third = RerouteEvaluator.evaluate(state, followerOffRoute, targetNearRouteEnd, route, nowMs = 100_000L)
        assertTrue(third is RerouteDecision.Reroute)
        val reroute = third as RerouteDecision.Reroute
        assertEquals(RerouteReason.OFF_ROUTE, reroute.reason)
        assertEquals(0, reroute.state.consecutiveOffRoute)
    }

    @Test
    fun `two off-route samples then one on-route sample resets the counter, no reroute`() {
        var state = RerouteState()

        state = (RerouteEvaluator.evaluate(state, followerOffRoute, targetNearRouteEnd, route, 100_000L) as RerouteDecision.Keep).state
        assertEquals(1, state.consecutiveOffRoute)
        state = (RerouteEvaluator.evaluate(state, followerOffRoute, targetNearRouteEnd, route, 100_000L) as RerouteDecision.Keep).state
        assertEquals(2, state.consecutiveOffRoute)

        val onRouteDecision = RerouteEvaluator.evaluate(state, followerOnRoute, targetNearRouteEnd, route, 100_000L)

        assertTrue(onRouteDecision is RerouteDecision.Keep)
        assertEquals(0, (onRouteDecision as RerouteDecision.Keep).state.consecutiveOffRoute)
    }

    @Test
    fun `exactly at the debounce boundary is allowed`() {
        // nowMs - lastRerouteAtMs == REROUTE_DEBOUNCE_MS exactly (60_000) — the `<` comparison must
        // not block this one.
        val state = RerouteState(lastRerouteAtMs = 500_000L, consecutiveOffRoute = 2)

        val decision = RerouteEvaluator.evaluate(
            state,
            followerOffRoute,
            targetNearRouteEnd,
            currentDirections = route,
            nowMs = 560_000L,
        )

        assertTrue(decision is RerouteDecision.Reroute)
        assertEquals(RerouteReason.OFF_ROUTE, (decision as RerouteDecision.Reroute).reason)
    }

    @Test
    fun `arrived, then the target moves out to 75m, clears the arrived flag`() {
        val state = RerouteState(hasArrived = true, lastRerouteAtMs = null)
        val follower = GeoPoint(21.030933, 105.854165)
        val targetNowFarEnough = GeoPoint(21.0316067, 105.854165) // ~74.9m from follower

        val decision = RerouteEvaluator.evaluate(state, follower, targetNowFarEnough, currentDirections = null, nowMs = 0L)

        val resultingState = when (decision) {
            is RerouteDecision.Keep -> decision.state
            is RerouteDecision.Reroute -> decision.state
            is RerouteDecision.Arrived -> decision.state
        }
        assertTrue(!resultingState.hasArrived)
    }
}
