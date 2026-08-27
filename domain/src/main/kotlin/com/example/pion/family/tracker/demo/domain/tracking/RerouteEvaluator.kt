package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.Directions
import com.example.pion.family.tracker.demo.domain.model.GeoPoint

/**
 * Debounce + hysteresis + off-route state threaded between calls to [RerouteEvaluator.evaluate] —
 * routing plan phase-04. The caller
 * ([com.example.pion.family.tracker.demo.domain.usecase.ObserveNavigationUseCase]) owns one
 * instance per navigation session and passes the returned state back in on the next call.
 */
data class RerouteState(
    val consecutiveOffRoute: Int = 0,
    /**
     * `null` = no provider call has been made in this session yet, which is a genuinely different
     * state from "the last call happened at epoch 0". Modelling it as `0L` would make the first
     * call of a session fire only because `nowMs - 0` happens to exceed the debounce window for a
     * real wall clock — a test (or a monotonic clock) that starts near zero would silently see the
     * first call suppressed instead. The type carries the distinction so nobody has to know how
     * large `nowMs` happens to be.
     */
    val lastRerouteAtMs: Long? = null,
    val hasArrived: Boolean = false,
)

/** Distinguishes the two reroute triggers — for logging and for measurement, not decoration. */
enum class RerouteReason { OFF_ROUTE, DESTINATION_MOVED }

sealed interface RerouteDecision {
    data class Keep(val state: RerouteState) : RerouteDecision
    data class Reroute(val state: RerouteState, val reason: RerouteReason) : RerouteDecision
    data class Arrived(val state: RerouteState) : RerouteDecision
}

/**
 * Decides *when* to call [com.example.pion.family.tracker.demo.domain.repository.RoutingProvider]
 * again — routing plan phase-04, same shape as [ZoneEvaluator]: takes the previous state, returns
 * the new one, holds nothing internally (Key Insight #6). A stateful evaluator would force every
 * test to rebuild history by calling in a precise order, and one broken test would break the
 * whole file.
 *
 * `nowMs` is a **parameter**, never the platform clock read directly inside this function — a
 * function that reads the clock itself makes its own tests nondeterministic.
 *
 * The six-step order below is a contract, not a preference — see phase-04's Architecture section
 * for the reasoning behind each step and its constant table for the cost of moving a threshold
 * either direction.
 */
object RerouteEvaluator {

    fun evaluate(
        state: RerouteState,
        follower: GeoPoint,
        target: GeoPoint,
        currentDirections: Directions?,
        nowMs: Long,
    ): RerouteDecision {
        val distanceToTarget = GeoDistance.haversineMeters(
            follower.latitude,
            follower.longitude,
            target.latitude,
            target.longitude,
        )

        // 1 — close enough to the target: arrived, stop rerouting.
        if (distanceToTarget < TrackingConstants.ARRIVAL_M) {
            return RerouteDecision.Arrived(state.copy(hasArrived = true))
        }

        // 2 — hysteresis exit: only clears the flag once past ARRIVAL_EXIT_M. Either way the
        // evaluation continues below with whichever flag value applies now.
        val workingState = if (state.hasArrived && distanceToTarget > TrackingConstants.ARRIVAL_EXIT_M) {
            state.copy(hasArrived = false)
        } else {
            state
        }

        // 3 — debounce gates before every other reason below, INCLUDING "no route yet". The first
        // call of a session passes because `lastRerouteAtMs` is still null, not because of any
        // arithmetic on the clock.
        //
        // This sits before the "no route" branch, where a literal reading of phase-04's step order
        // ("bất kể debounce") would put it after. The literal order opens a real hole: a provider
        // that keeps failing leaves `currentDirections` null forever, so an unconditional "no route
        // -> reroute" fires on EVERY combined location sample — roughly every 2.5s, without end.
        // Phase-04's own Security Considerations name the debounce as "thứ duy nhất đứng giữa một
        // vòng lặp lỗi và một hoá đơn", and a failure loop is exactly when it has to hold. The
        // stated reason for the original order ("lần đầu phải gọi ngay") is preserved by the null
        // sentinel above; the hole is not.
        val lastReroute = workingState.lastRerouteAtMs
        if (lastReroute != null && nowMs - lastReroute < TrackingConstants.REROUTE_DEBOUNCE_MS) {
            return RerouteDecision.Keep(workingState)
        }

        // 4 — no route: either the first call of the session, or a retry after the debounce has
        // elapsed. Steps 5 and 6 both need an existing route to measure against, so this must
        // resolve before them.
        if (currentDirections == null) {
            return RerouteDecision.Reroute(
                workingState.copy(lastRerouteAtMs = nowMs, consecutiveOffRoute = 0),
                RerouteReason.OFF_ROUTE,
            )
        }

        // 5 — the target itself moved away from where the current route ends.
        val routeEnd = currentDirections.points.lastOrNull()
        if (routeEnd != null) {
            val destinationMovedMeters = GeoDistance.haversineMeters(
                target.latitude,
                target.longitude,
                routeEnd.latitude,
                routeEnd.longitude,
            )
            if (destinationMovedMeters > TrackingConstants.DESTINATION_MOVED_TOLERANCE_M) {
                return RerouteDecision.Reroute(
                    workingState.copy(lastRerouteAtMs = nowMs, consecutiveOffRoute = 0),
                    RerouteReason.DESTINATION_MOVED,
                )
            }
        }

        // 6 — off-route accumulation. Any on-route sample resets the counter to 0 — a counter
        // that only accumulates turns a few scattered GPS wobbles over ten minutes into a
        // reroute, and that is not off-route, that is GPS.
        val offRouteDistance = RoutingGeometry.distanceToPolylineMeters(follower, currentDirections.points)
        return if (offRouteDistance > TrackingConstants.OFF_ROUTE_TOLERANCE_M) {
            val nextCount = workingState.consecutiveOffRoute + 1
            if (nextCount >= TrackingConstants.OFF_ROUTE_CONSECUTIVE_SAMPLES) {
                RerouteDecision.Reroute(
                    workingState.copy(consecutiveOffRoute = 0, lastRerouteAtMs = nowMs),
                    RerouteReason.OFF_ROUTE,
                )
            } else {
                RerouteDecision.Keep(workingState.copy(consecutiveOffRoute = nextCount))
            }
        } else {
            RerouteDecision.Keep(workingState.copy(consecutiveOffRoute = 0))
        }
    }
}
