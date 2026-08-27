package com.example.pion.family.tracker.demo.domain.tracking

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Toán hình học nhỏ dùng riêng bởi [MemberRoamer] — tách ra để giữ `MemberRoamer.kt` dưới 200 dòng
// (`.claude/rules/development-rules.md`), cùng lý do `MemberRoamerModel.kt` tách khỏi cùng file.

private const val METERS_PER_DEGREE_LAT: Double = 111_320.0
private const val MIN_METERS_PER_DEGREE_LNG: Double = 1.0
internal const val FULL_CIRCLE_DEGREES: Double = 360.0

/** Đích đi loanh quanh khi chưa có zone nào để nhắm tới, hoặc khi đích quá xa sau khi đã spawn
 * ([MemberRoamer.MAX_WALK_M]). `kind = WANDER` — không có ngữ nghĩa zone nào áp dụng. */
internal fun wanderTarget(lat: Double, lng: Double, random: Random): RoamTarget {
    val (wanderLat, wanderLng) = pointAtBearing(
        lat,
        lng,
        MemberRoamer.WANDER_RADIUS_M * random.nextDouble(),
        random.nextDouble(FULL_CIRCLE_DEGREES),
    )
    return RoamTarget(wanderLat, wanderLng, zoneId = null, approachRadiusMeters = MemberRoamer.WANDER_RADIUS_M, kind = LegKind.WANDER)
}

/** Xấp xỉ phẳng (mét trên độ), cùng lựa chọn với [RouteBlueprint] — đủ chính xác ở quy mô một bán
 * kính zone cho phép (tối đa 2km). `coerceAtLeast` chặn chia cho 0 ở gần cực. */
internal fun pointAtBearing(lat: Double, lng: Double, distanceMeters: Double, bearingDegrees: Double): Pair<Double, Double> {
    val bearing = Math.toRadians(bearingDegrees)
    val metersPerDegreeLng =
        (METERS_PER_DEGREE_LAT * cos(Math.toRadians(lat))).coerceAtLeast(MIN_METERS_PER_DEGREE_LNG)
    return (lat + distanceMeters * cos(bearing) / METERS_PER_DEGREE_LAT) to
        (lng + distanceMeters * sin(bearing) / metersPerDegreeLng)
}
