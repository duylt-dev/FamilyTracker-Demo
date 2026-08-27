package com.example.pion.family.tracker.demo.data.local.mapper

import com.example.pion.family.tracker.demo.data.local.entity.ZoneEventEntity
import com.example.pion.family.tracker.demo.domain.model.ZoneEvent

fun ZoneEventEntity.toDomain(): ZoneEvent = ZoneEvent(
    id = id,
    zoneId = zoneId,
    zoneName = zoneName,
    memberId = memberId,
    type = type,
    occurredAt = occurredAt,
    latitude = latitude,
    longitude = longitude,
    source = source,
)

fun ZoneEvent.toEntity(): ZoneEventEntity = ZoneEventEntity(
    id = id,
    zoneId = zoneId,
    zoneName = zoneName,
    memberId = memberId,
    type = type,
    occurredAt = occurredAt,
    latitude = latitude,
    longitude = longitude,
    source = source,
)
