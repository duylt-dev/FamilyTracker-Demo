package com.example.pion.family.tracker.demo.domain.model

/**
 * A family member, real or demo-seeded — PRD §9. Carries no coordinate: "last known position"
 * is derived by [com.example.pion.family.tracker.demo.domain.repository.MemberRepository]
 * from the latest [LocationPoint] per member (phase-02 Key Insight #1).
 */
data class Member(
    val id: String,
    val name: String,
    val colorArgb: Int,
    val isSelf: Boolean,
)
