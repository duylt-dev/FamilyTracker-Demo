package com.example.pion.family.tracker.demo.domain.model

/**
 * A [Member] paired with their most recently recorded [LocationPoint], or `null` if nobody has
 * ever recorded a point for them yet (e.g. "self" before tracking starts once — phase-05
 * Key Insight #6: "last known position" is derived from `location_points`, never a coordinate
 * column on `members`). Assembled by
 * [com.example.pion.family.tracker.demo.domain.usecase.ObserveMembersWithLastLocationUseCase]
 * for the Map screen (US-06, US-08).
 */
data class MemberLocation(
    val member: Member,
    val lastLocation: LocationPoint?,
)
