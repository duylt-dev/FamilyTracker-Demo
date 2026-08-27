package com.example.pion.family.tracker.demo.data.local.mapper

import com.example.pion.family.tracker.demo.data.local.entity.ZoneEntity
import com.example.pion.family.tracker.demo.domain.model.Zone

fun ZoneEntity.toDomain(): Zone = Zone(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    colorArgb = colorArgb,
    notifyOnEnter = notifyOnEnter,
    notifyOnExit = notifyOnExit,
    createdAt = createdAt,
)

fun Zone.toEntity(): ZoneEntity = ZoneEntity(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    colorArgb = colorArgb,
    notifyOnEnter = notifyOnEnter,
    notifyOnExit = notifyOnExit,
    createdAt = createdAt,
)
