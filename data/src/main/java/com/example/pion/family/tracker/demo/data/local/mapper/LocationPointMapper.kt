package com.example.pion.family.tracker.demo.data.local.mapper

import com.example.pion.family.tracker.demo.data.local.entity.LocationPointEntity
import com.example.pion.family.tracker.demo.domain.model.LocationPoint

/** `memberId` is dropped: [LocationPoint] carries no member association (LLM.md §9). */
fun LocationPointEntity.toDomain(): LocationPoint = LocationPoint(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    speedMps = speedMps,
    bearingDegrees = bearingDegrees,
    recordedAt = recordedAt,
)

/** `id`/`memberId` supplied by the caller — [LocationPoint] itself carries neither. */
fun LocationPoint.toEntity(id: String, memberId: String): LocationPointEntity = LocationPointEntity(
    id = id,
    memberId = memberId,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    speedMps = speedMps,
    bearingDegrees = bearingDegrees,
    recordedAt = recordedAt,
)
