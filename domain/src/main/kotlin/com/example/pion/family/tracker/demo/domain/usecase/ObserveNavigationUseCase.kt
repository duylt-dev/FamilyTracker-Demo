package com.example.pion.family.tracker.demo.domain.usecase

import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.Directions
import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.NavigationUpdate
import com.example.pion.family.tracker.demo.domain.repository.LocationSource
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import com.example.pion.family.tracker.demo.domain.repository.RoutingProvider
import com.example.pion.family.tracker.demo.domain.tracking.GeoDistance
import com.example.pion.family.tracker.demo.domain.tracking.RerouteDecision
import com.example.pion.family.tracker.demo.domain.tracking.RerouteEvaluator
import com.example.pion.family.tracker.demo.domain.tracking.RerouteState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

/**
 * The ONLY place that calls [RoutingProvider] — routing plan phase-04 non-negotiable. Combines
 * self's live position ([LocationSource]) with [targetMemberId]'s last known position
 * ([MemberRepository.observeLatestLocations]); on every combined update, asks [RerouteEvaluator]
 * whether to fetch a new route, and emits one [NavigationUpdate] per update either way.
 *
 * A provider failure keeps whatever [Directions] is already held — never nulled out, so losing
 * network for a few seconds does not erase the route already drawn.
 *
 * [nowMs] defaults to the real clock; tests inject a controllable lambda instead — same pattern
 * as `TimelineViewModel`'s `clock: Clock` default (LLM.md §3), so [RerouteEvaluator.evaluate]
 * still never reads the clock itself.
 */
class ObserveNavigationUseCase(
    private val locationSource: LocationSource,
    private val memberRepository: MemberRepository,
    private val routingProvider: RoutingProvider,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    operator fun invoke(targetMemberId: String): Flow<NavigationUpdate> = flow {
        var state = RerouteState()
        var directions: Directions? = null

        combine(
            locationSource.stream(),
            memberRepository.observeLatestLocations(),
        ) { self, locations -> self to locations[targetMemberId] }
            .collect { (self, targetPoint) ->
                // No known position for the target yet — nothing to navigate to.
                if (targetPoint == null) return@collect

                val follower = GeoPoint(self.latitude, self.longitude)
                val target = GeoPoint(targetPoint.latitude, targetPoint.longitude)

                val decision = RerouteEvaluator.evaluate(state, follower, target, directions, nowMs())
                state = when (decision) {
                    is RerouteDecision.Keep -> decision.state
                    is RerouteDecision.Arrived -> decision.state
                    is RerouteDecision.Reroute -> decision.state
                }

                var lastError: AppError? = null
                if (decision is RerouteDecision.Reroute) {
                    when (val result = routingProvider.directions(follower, target)) {
                        is AppResult.Success -> directions = result.data
                        is AppResult.Failure -> lastError = result.error
                    }
                }

                val currentDirections = directions
                val distanceMeters = currentDirections?.distanceMeters
                    ?: GeoDistance.haversineMeters(
                        follower.latitude,
                        follower.longitude,
                        target.latitude,
                        target.longitude,
                    )

                emit(
                    NavigationUpdate(
                        directions = currentDirections,
                        distanceMeters = distanceMeters,
                        isDistanceEstimated = currentDirections == null,
                        hasArrived = state.hasArrived,
                        lastError = lastError,
                    ),
                )
            }
    }
}
